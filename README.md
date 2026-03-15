# DragRaceTelemetry 🏎️💨

DragRaceTelemetry é uma plataforma de telemetria de alta performance desenvolvida para entusiastas automotivos e preparadores. O projeto resolve o problema de acesso a métricas precisas de arrancada (0-100km/h, 201m, 400m) sem a necessidade de hardware proprietário caro, utilizando apenas o smartphone do usuário e um adaptador OBD2 padrão.

Este é um projeto Micro-SaaS focado em baixa latência, portabilidade e arquitetura reativa.

## 🚀 Tech Stack (Modern Engineering 2026)

O projeto foi construído utilizando o estado da arte do ecossistema Kotlin:

- **Kotlin Multiplatform (KMP):** 100% da lógica de negócio e parsers de protocolo compartilhados entre Android e iOS.
- **Compose Multiplatform (CMP):** UI declarativa unificada, garantindo paridade visual e funcional em ambas as plataformas.
- **Strict MVI Architecture:** Fluxo de dados unidirecional (Intent -> ViewModel -> State -> UI) para gerenciamento de estado previsível.
- **Kable (BLE):** Comunicação de baixa latência via Bluetooth Low Energy (BLE) configurada integralmente no `commonMain`.
- **Kotlin Coroutines & Flow:** Processamento assíncrono para garantir fluidez da UI, mesmo com alta taxa de atualização do barramento CAN.
- **Performance First:** Parser OBD2 otimizado com operações **Bitwise** para garantir o menor overhead possível durante a telemetria em tempo real.

## 🛠️ Hardware Support

Focado em precisão e compatibilidade:

- **Adaptadores:** Recomendado **[uScan OBD2](https://meli.la/1pv3UKW)** (Ultra-rápido), Konnwei KW903 ou similares baseados em ELM327.
- **Protocolo:** ELM327 sobre BLE (Bluetooth Low Energy).
- **Veículos:** Compatível com veículos que seguem o padrão OBD2 (PIDs de velocidade e RPM padrão SAE J1979).

## 🏗️ Project Structure

```text
├── composeApp
│   ├── src
│   │   ├── commonMain (Lógica Compartilhada & UI)
│   │   │   ├── app (Navegação e Entry Point)
│   │   │   ├── core (Bluetooth Manager, OBD2 Parsers, State Global)
│   │   │   ├── features (Race Dashboard, Settings)
│   │   │   └── plataform (Expect declarations para hardware)
│   │   ├── androidMain (Implementação de permissões e Bluetooth Android)
│   │   └── iosMain (Configurações específicas CoreBluetooth)
├── iosApp (Entry point nativo para iOS)
└── gradle/libs.versions.toml (Gerenciamento centralizado de dependências)
```

## 🏁 Roadmap

- [x] **MVP:** Conexão estável com uScan OBD2 e leitura de RPM/Velocidade.
- [x] **UI:** Dashboard automotivo com modo Dark forçado e terminal de logs interativo.
- [ ] **v1.1:** Gráficos de telemetria em tempo real (Boost vs Speed).
- [ ] **v1.2:** Exportação de logs em CSV/JSON para análise em desktop.
- [ ] **v2.0:** Leaderboard global e validação de tempo via GPS + OBD2.

## 📄 License

Este projeto é desenvolvido sob a licença MIT.

---

### Build and Run

#### Android
- **Windows:** `.\gradlew.bat :composeApp:assembleDebug`
- **macOS/Linux:** `./gradlew :composeApp:assembleDebug`

#### iOS
Abra o diretório `/iosApp` no Xcode ou use a configuração de execução do Android Studio.
