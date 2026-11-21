# Yaris OBD — painel OBD2 no Android Auto

App Android pessoal que lê a ECU de um **Toyota Yaris 2021 (BR)** por scanner OBD2 e desenha um painel automotivo na tela da **central multimídia original, via Android Auto**. Uso privado, instalação por APK, sem publicação na Play Store.

> **Estado atual: funcionando em campo.** Em 18/11/2026 o app rodou na central original do Yaris 2021 e **conversou com o scanner OBD2 real**, exibindo dados do veículo. Tem 13 layouts selecionáveis, tela de diagnóstico na central e serviço em primeiro plano (a leitura continua com outro app aberto no celular). Detalhes da instalação em [`docs/instalacao-android-auto.md`](docs/instalacao-android-auto.md).

## Comece por aqui

| Documento | Para quê |
|---|---|
| [`docs/PLAYBOOK-android-auto.md`](docs/PLAYBOOK-android-auto.md) | **Comece por aqui em qualquer projeto novo de Android Auto**: regras, receita de instalação, armadilhas e checklist |
| [`docs/instalacao-android-auto.md`](docs/instalacao-android-auto.md) | **Receita de instalação comprovada no carro** (Shizuku + UnknownAuto) e o registro que o Android Auto exige |
| [`tools/install-aa-apk.ps1`](tools/install-aa-apk.ps1) | **Automação** da instalação/atualização pelo caminho que o Android Auto aceita, com validação |
| [`docs/ETAPA-ZERO.md`](docs/ETAPA-ZERO.md) | **Relatório consolidado de viabilidade**: o que dá, o que não dá, alternativas, riscos, plano e o teste de conceito |
| [`docs/simulador-obd2.md`](docs/simulador-obd2.md) | **Adaptador ELM327 virtual**: testar o app com o protocolo real, sem o scanner físico |
| [`docs/estilos-painel.md`](docs/estilos-painel.md) | **Os cinco estilos visuais** do painel, quando usar cada um e as decisões de design |
| [`docs/mockups/index.html`](docs/mockups/index.html) | **12 protótipos de layouts novos** em HTML/JS, animados — só para aprovação, ainda não implementados |
| [`docs/pesquisa/android-auto.md`](docs/pesquisa/android-auto.md) | Pesquisa completa sobre Android Auto (com correção registrada) |
| [`docs/pesquisa/yaris-2021.md`](docs/pesquisa/yaris-2021.md) | O que se sabe de fato sobre o Yaris 2021 brasileiro, confirmado vs. hipótese |
| [`docs/especificacao-obd2-elm327.md`](docs/especificacao-obd2-elm327.md) | Especificação da camada OBD2: protocolo, comandos AT, PIDs com fórmulas, DTC, arquitetura |
| [`docs/img/`](docs/img) | Renderizações reais do painel (1920x720, 1280x720, 800x480) |

## Requisitos

- Celular com **Android Auto** funcionando (o Android Auto atual exige Android 9+ na documentação, e na prática Android 11+).
- Central compatível com Android Auto por **cabo USB**.
- Scanner **ELM327 Bluetooth** (Classic ou BLE). Wi-Fi conflita com Android Auto sem fone; USB conflita com a projeção.

## Compilar

```powershell
.\gradlew.bat :app:assembleDebug
```

APK em `app/build/outputs/apk/debug/app-debug.apk`.

Categoria do app no Android Auto (default `IOT`):

```powershell
.\gradlew.bat :app:assembleDebug -PcarCategory=androidx.car.app.category.NAVIGATION
```

Testes unitários (JVM, sem aparelho):

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Requisitos de build: JDK 17, Android SDK com plataforma 36. Versões fixadas em Gradle 8.13 + AGP 8.13.2 + Kotlin 2.2.20.

## Instalar e testar

1. **Habilitar "Instalar via USB"** nas opções do desenvolvedor (obrigatório no HyperOS/MIUI; sem isso o `adb install` falha com `INSTALL_FAILED_USER_RESTRICTED`).
2. Instalar o APK. **Sideload comum não faz o app aparecer no Android Auto** — a plataforma exige que o app pareça instalado por uma loja. Use `UnknownAuto` ou `AAEnabler` (via Shizuku) ou, na rota oficial, *Internal App Sharing* do Play Console.
3. Conferir o registro de instalação:
   ```powershell
   adb shell dumpsys package com.pedro.yarisobd | Select-String "installerPackageName|initiatingPackageName"
   ```
   Esperado: `installerPackageName=com.android.vending` e `initiatingPackageName=com.google.android.packageinstaller`.
4. Android Auto → **Personalizar launcher** → habilitar "Yaris OBD".
5. Plugar na central → grade de apps → **Yaris OBD**.
6. Na tela do carro, tocar em **Detalhes** para ver resolução da superfície, quadros desenhados, fps e último erro.

Se nada aparecer no passo 4, não adianta plugar no carro: o problema é distribuição, não código.

## Estrutura

```
app/src/main/java/com/pedro/yarisobd/
  MainActivity.kt              painel de controle do carro virtual (celular)
  AppPrefs.kt                  preferências (subir simulador automaticamente)
  car/                         integração com Android Auto
    ObdCarAppService.kt        serviço descoberto pelo host (Car App Library)
    ObdSession.kt              sessão por conexão
    DashboardScreen.kt         NavigationTemplate + SurfaceCallback + laço de desenho
    DiagnosticsScreen.kt       tela de diagnóstico no carro
    ScreenStatus.kt            estado exposto ao diagnóstico
  obd/                         camada OBD2 (a mesma que vai falar com o scanner real)
    ObdTransport.kt            interface de transporte + adaptador virtual
    ElmSession.kt              conversa com o ELM327 (mutex, timeout, prompt)
    Pids.kt                    fórmulas de decodificação (SAE J1979)
    ObdConnection.kt           inicialização, PIDs suportados, laço de leitura, DTCs
    ConsoleLog.kt              console de comandos e respostas cruas
  sim/                         simulador
    VehicleSimulator.kt        modelo do carro em tempo real
    Elm327Emulator.kt          protocolo ELM327 emulado
    SimulatorController.kt     liga/desliga e controles
  render/
    DashboardRenderer.kt       desenho do painel em Canvas
    GaugeMath.kt               escalas, ângulos, formatação (testável em JVM)
    DashboardPreview.kt        exporta PNG com o mesmo renderizador (ferramenta)
  telemetry/
    Telemetry.kt               modelo; null = indisponível (nunca zero)
    TelemetryRepository.kt     fonte única de verdade para carro e celular
    DtcCodes.kt                codificação/decodificação de códigos de falha
    DemoTelemetrySource.kt     simulação interna sem protocolo (legado)
tools/getdoc.ps1               baixa e limpa HTML das fontes de pesquisa
```

## Simulador

Antes do scanner chegar, o app já conversa com um **ELM327 virtual** que fala o protocolo de verdade, alimentado por um modelo de veículo em tempo real. Dá para acelerar, frear, ver o motor aquecer e injetar falhas — tudo passando pelo mesmo caminho de decodificação que será usado no carro.

Passo a passo em [`docs/simulador-obd2.md`](docs/simulador-obd2.md).

## Regras de projeto

- **Somente leitura** da ECU no uso normal. Nenhuma escrita sem ação explícita do usuário.
- **Indisponível ≠ zero.** PID não suportado aparece como travessão, nunca como 0.
- **Simulação é rotulada** de forma impossível de ignorar; nunca apresentada como dado do carro.
- **Dado velho é sinalizado** (3 s sem atualização), em vez de repetir o último número como se fosse atual.
- Sem internet, sem telemetria para servidor, sem analytics, sem serviço em background desnecessário.
- Sem PIDs proprietários inventados: o que não está documentado não entra no código.

