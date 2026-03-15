1. Stack Tecnológica Exigida:

Core: Kotlin Multiplatform (Targets: Android e iOS).

UI: Compose Multiplatform (CMP) compartilhando 100% da UI no commonMain.

Arquitetura: Strict MVI (Model-View-Intent) usando StateFlow e Coroutines.

Conectividade BLE: Biblioteca Kable (JuulLabs) configurada no commonMain.

Injeção de Dependência: Koin KMP. 

2. Arquitetura e Estrutura (KMP/MVI)
   Pattern: Strict MVI (Model-View-Intent).

Unidirecionalidade: O fluxo deve ser obrigatoriamente: Intent -> ViewModel -> State -> UI.

Proporção ViewModel/Tela (1:1): Mantenha a responsabilidade separada. Cada tela deve ter seu próprio ViewModel e Contrato exclusivos para maior legibilidade e manutenabilidade.

Navegação Centralizada: Use um `AppMainViewModel` exclusivo para gerenciar o roteamento global (Compose Navigation). ViewModels de features (ex: `RaceTelemetryViewModel`) não devem conhecer as rotas, devem apenas emitir efeitos/callbacks para o ViewModel pai.

Compartilhamento de Estado (Shared State): Se múltiplos ViewModels precisarem compartilhar dados, estados ou variáveis, crie um `Object` dedicado (ex: `AppSharedState` com `MutableStateFlow`) para servir como Single Source of Truth comum entre eles, em vez de passar dados como parâmetros acoplados.

Shared Module: Toda a lógica de negócio, parsers de OBD2 e persistência devem residir no commonMain.

Expect/Actual: Use apenas para APIs de hardware que o KMP ainda não cobre (ex: Bluetooth nativo se o Kable não for suficiente).

Modularização: Organize por feature, não por tipo de arquivo (ex: features/race, features/history, core/network).

3. Padrões de Código (Kotlin/CMP)
   Concorrência: Use Coroutines e Flow exclusivamente. Evite Callbacks.

UI Declarativa: Use Compose Multiplatform. Os componentes devem ser Stateless. Todo estado deve ser elevado (State Hoisting) para o ViewModel.

Imutabilidade: Todos os estados da UI (State) devem ser classes data class imutáveis usando val.

Dependency Injection: Use Koin (KMP version). Evite Singletons globais; prefira injeção via construtor (exceto para os Shared States globais definidos via `Object`).

4. Regras para o Agente de IA (Execution Protocol)
   Zero Hallucination: Se não houver uma biblioteca KMP para uma função específica, o agente deve implementar via expect/actual e não inventar dependências.

Performance First: No parser OBD2, evite alocações excessivas de memória dentro de loops de alta frequência (telemetria). Use Bitwise operations para conversão de Hexadecimal.

Error Handling: Todo fluxo de hardware (Bluetooth) deve ser encapsulado em blocos runCatching ou try-catch específicos, mapeando erros para estados da UI (BluetoothError, ConnectionLost).

DRY vs AHA: Prefira o princípio AHA (Avoid Hasty Abstractions). Não abstraia o código antes que ele se repita pelo menos 3 vezes. Mantenha o código de telemetria legível.

5. Stack de Referência 2026
   Language: Kotlin 2.1+ (K2 Compiler).

Async: Kotlinx.Coroutines 1.9+.

Connectivity: JuulLabs Kable (BLE).

Serialization: Kotlinx.Serialization (para JSON de logs).

Design System: Material 3 Adaptive (focado em legibilidade para alta luminosidade em carros).

Mantenha a Single Source of Truth (SSOT).

A UI deve ser puramente reativa e burra, apenas observando o StateFlow do ViewModel.

Trate cenários de erro do Kable (falha na conexão, perda de sinal durante a puxada) retornando ao estado Disconnected.

Sempre prefira o uso de APIs disponíveis em commonMain. Se precisar de algo específico de plataforma, sugira a estrutura expect/actual

Não escreva código nativo (Swift/Kotlin Android) para a lógica de Bluetooth, use 100% Kable no commonMain.
