# 🏎️ Drag Race Telemetry (DRT) - Architecture & Refactoring Guide

Este documento consolida as decisões arquiteturais, de performance e os padrões de projeto (Design Patterns) definidos
para o módulo de telemetria e corrida (Race Feature). O foco absoluto deste módulo é **Alta Performance (Zero GC
Thrashing)**, **Concorrência Estruturada** e **Clean Code**.

## 1. Visão Geral da Arquitetura (MVI)

A feature utiliza o padrão **Model-View-Intent (MVI)** com foco em *Unidirectional Data Flow (UDF)* e uma **Dumb UI (
Interface Burra)**.

* **State (Model):** A única fonte da verdade. É imutável e gerado exclusivamente pela ViewModel.
* **Intent:** Ações disparadas pela UI (ex: `StartRace`, `ConnectToDevice`). A UI nunca toma decisões lógicas.
* **Effect:** Eventos *One-off* (únicos) como Toasts, navegação ou sons de vitória, disparados via `SharedFlow` (
  Channel).

## 2. Pilares de Performance Implementados

### A. Zero-Allocation (GC-Friendly) no Loop de Telemetria

* **Problema:** Criar objetos complexos (ex: `Strings` formatadas, instâncias de `Flow`, `Filters` com listas
  instanciadas on-the-fly) dentro do loop de 120ms (BLE) ou 16ms (Física) causa o acionamento do *Garbage Collector*,
  gerando *jank* (perda de quadros) na tela.
* **Solução:** O `ObdParser` utiliza **SAM Interfaces** (`ObdDataListener`) e **Enums** (`ObdPid`). Trafegamos apenas
  tipos primitivos (`Int`) e referências de memória estáticas. As `Strings` de tempo do cronômetro NUNCA usam
  `String.format` (que faz boxing/Regex), usamos `padStart(3, '0')` e *String Templates* sob demanda.

### B. Event Stream Unification

* **Padrão:** O `RaceTelemetryViewModel` usa um *wrapper* `sealed interface HardwareEvent` aliado ao operador `merge()`
  das Coroutines.
* **Motivo:** Em vez de dezenas de `launch { collect {} }` alterando o estado simultaneamente (arriscando *race
  conditions*), todos os fluxos de hardware (GPS, BLE, Velocidade Fundida, Logs) caem em um funil síncrono único (
  `processHardwareEvent`), garantindo estabilidade e baixo consumo de CPU.

### C. Física e Precisão de Cronômetro

* **Padrão:** Uso do `TimeSource.Monotonic` nativo do Kotlin em vez de `System.currentTimeMillis()` ou `epochSeconds`.
* **Motivo:** O relógio monotônico é imune a sincronizações de rede NTP (Wall-Clock Jumps) ou mudanças de fuso horário,
  garantindo que o cálculo de integração ($\Delta d = v \cdot \Delta t$) seja cirurgicamente preciso para arrancadas.

---

## 3. O Contrato Oficial (Contract)

Este é o contrato atualizado e otimizado que deve ser consumido pela UI.

```kotlin
interface RaceTelemetryContract {

    data class Checkpoint(val timeMs: Long? = null) {
        val isCompleted: Boolean get() = timeMs != null

        // Uso EXCLUSIVO da UI para exibio
        val formattedTime: String
            get() {
                if (timeMs == null) return "--.---"
                val seconds = timeMs / 1000
                val fractions = (timeMs % 1000).toString().padStart(3, '0')
                return "$seconds.$fractions"
            }
    }

    data class TelemetryState(
        val speed: Int = 0,
        val rpm: Int = 0,
        val maxSpeed: Int = 0,
        val maxRpm: Int = 0
    )

    data class RaceSession(
        val isRecording: Boolean = false,
        val currentDistance: Double = 0.0,
        val currentTimerMs: Long = 0L,
        val run0to100: Checkpoint = Checkpoint(),
        val run60ft: Checkpoint = Checkpoint(),
        val run100m: Checkpoint = Checkpoint(),
        val run201m: Checkpoint = Checkpoint()
    ) {
        // Uso EXCLUSIVO da UI para o cronmetro central animado
        val formattedCurrentTimer: String
            get() {
                val seconds = currentTimerMs / 1000
                val fractions = (currentTimerMs % 1000).toString().padStart(3, '0')
                return "$seconds.$fractions"
            }
    }

    data class TerminalState(
        val isVisible: Boolean = false,
        val logs: List<String> = emptyList()
    )

    data class State(
        val bluetoothStatus: BluetoothStatus = BluetoothStatus.Disconnected,
        val telemetry: TelemetryState = TelemetryState(),
        val race: RaceSession = RaceSession(),
        val terminal: TerminalState = TerminalState()
    )

    sealed class Intent {
        object StartScanning : Intent()
        object ConnectToDevice : Intent() // A UI dispara cega. A VM busca o device no State.
        object DisconnectDevice : Intent()
        object StartRace : Intent()
        object StopRace : Intent()
    }

    sealed class Effect {
        data class ShowError(val message: String) : Effect()
        data class Race0to100Completed(val time: Long, val formattedTime: String) : Effect()
        data class Race201mCompleted(val time: Long, val formattedTime: String) : Effect()
    }
}
```

## 4. Diretrizes para a Refatoração da UI (Jetpack Compose)

Para que a performance da arquitetura brilhe, o Jetpack Compose precisa ser implementado seguindo estas regras de ouro:

### 1. Coleta Segura de Estado (Lifecycle Awareness)

NUNCA use collectAsState() simples. Como a telemetria roda em alta frequência, se o app for para segundo plano (
Background), precisamos parar a renderização.

Kotlin
// CORRETO
val state by viewModel.state.collectAsStateWithLifecycle()

### 2. A UI Deve Ser "Burra" (Passive View)

A UI não formata strings, não faz if/else complexos e não guarda o Advertisement do Bluetooth. Ela apenas lê os getters
do State e dispara as funções do Intent.

Kotlin
// CORRETO
Text(text = state.race.formattedCurrentTimer)

Button(onClick = { viewModel.handleIntent(RaceTelemetryContract.Intent.ConnectToDevice) })

### 3. Tratamento Correto de Effects (One-Off Events)

Os efeitos (Snackbars, sons, navegação) não fazem parte do estado persistente. Eles devem ser coletados em um bloco
LaunchedEffect.

```Kotlin
val snackbarHostState = remember { SnackbarHostState() }

LaunchedEffect(Unit) {
    viewModel.effect.collect { effect ->
        when (effect) {
            is RaceTelemetryContract.Effect.ShowError -> {
                snackbarHostState.showSnackbar(effect.message)
            }
            is RaceTelemetryContract.Effect.Race0to100Completed -> {
// Tocar som de vitria, iniciar animao de confete, etc.
            }
// ...
        }
    }
}
``` 

### 4. Estabilidade de Recomposição (Recomposition)

Como o State principal muda 60 vezes por segundo (devido ao currentTimerMs), extraia componentes que não mudam
frequentemente para composables isolados (@Composable fun TerminalView()) para que eles sofram Skip (pulem a
recomposição), garantindo que apenas o Cronômetro e o Velocímetro sejam redesenhados.