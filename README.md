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

- **Adaptadores:** Recomendado **[uScan OBD2](https://meli.la/1pv3UKW)** (Ultra-rápido), ou similares baseados em ELM327.
- **Protocolo:** ELM327 sobre BLE (Bluetooth Low Energy).
- **Veículos:** Compatível com veículos que seguem o padrão OBD2 (PIDs de velocidade e RPM padrão SAE J1979).

## 🏗️ Project Structure

```text
├── composeApp
│   ├── src
│   │   ├── commonMain (Lógica Compartilhada & UI)
│   │   │   ├── app (Navegação global e Entry Point)
│   │   │   ├── core (Bluetooth Manager, OBD2 Parsers, State Global)
│   │   │   ├── features (Race Dashboard, Settings, History, Splash)
│   │   │   └── plataform (Expect declarations para hardware)
│   │   ├── androidMain (Implementação de permissões e Bluetooth Android)
│   │   └── iosMain (Configurações específicas CoreBluetooth)
├── iosApp (Entry point nativo para iOS)
└── gradle/libs.versions.toml (Gerenciamento centralizado de dependências)
```

## ✨ Últimas Atualizações e Funcionalidades

- **Histórico de Puxadas:** Agora as medições (tempo 0-100, 201m, etc.) e estatísticas de RPM e Velocidade são armazenadas em um histórico persistente dentro do app e exibidas de maneira visual otimizada para o usuário.
- **Navegação Global e Animações (App Navigation Rail):** Implementação de menu lateral (`NavigationRail`) nativo, fluido e responsivo para transição entre telas (Dashboard, Settings, Histórico), usando controle de estado unificado em um `AppMainViewModel`.
- **UI Refinada (Material 3):** Animações suaves de transição (Crossfade de botões, Toggle de Menu Hambúrguer vs NavRail e Expansão/Recolhimento do Terminal OBD2), melhorando substancialmente a experiência visual de pista.
- **Otimização de Lógica:** Separação rígida de ViewModels por tela para garantir que o _Single Source of Truth_ de navegação fique blindado contra lógica de hardware Bluetooth.

## 🏁 Roadmap

- [x] **MVP:** Conexão estável com uScan OBD2 e leitura de RPM/Velocidade.
- [x] **UI:** Dashboard automotivo com modo Dark forçado, terminal de logs animado interativo e NavigationRail.
- [x] **Armazenamento:** Histórico detalhado de pulls salvos localmente.
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
