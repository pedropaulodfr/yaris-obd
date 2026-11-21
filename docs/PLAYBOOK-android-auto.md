# Playbook: colocar um app próprio no Android Auto de uma central original

> **O que é isto:** o registro do que funcionou de verdade num Toyota Yaris 2021 com central original, em novembro de 2026 — incluindo tudo o que **não** funcionou e por quê. Serve para qualquer app Android que precise aparecer na tela de uma central por Android Auto, sem publicar na Play Store.
>
> **Regra do documento:** só entra aqui o que foi medido no aparelho/carro. O que é inferência está marcado como tal.

---

## TL;DR — as cinco coisas que decidem o projeto

1. **Não existe "painel livre" em template.** Para desenhar o que quiser (gauges, gráficos) só há um caminho: `NavigationTemplate`/`MapWithContentTemplate` + `ACCESS_SURFACE`, desenhando num `Canvas` ou via `VirtualDisplay` + `Presentation`.
2. **Sideload não aparece na central.** A opção "Fontes desconhecidas" **não** vale para apps da Car App Library (vale para mídia, notificações e parked).
3. **A porteira é o registro de instalação.** O Android Auto exige `initiatingPackageName = com.google.android.packageinstaller` e `installerPackageName = com.android.vending`.
4. **`adb install` nunca passa.** Nem `pm install -i com.android.vending`. É preciso um app que conduza o instalador do sistema (UnknownAuto/AAEnabler via Shizuku) ou a rota oficial (Internal App Sharing).
5. **O DHU não é teste de distribuição.** Ele é "developer head unit" e pula justamente a checagem que a central faz. Ele valida desenho e manifesto, só isso.

---

## 1. Como o Android Auto funciona (o mínimo que importa)

- É **projeção**: o app roda no celular; a central é tela e entrada. Nada é instalado na central.
- O app declara suporte no manifesto:
  ```xml
  <meta-data android:name="com.google.android.gms.car.application"
             android:resource="@xml/automotive_app_desc"/>
  ```
  e em `automotive_app_desc.xml`, para apps de template:
  ```xml
  <automotiveApp><uses name="template" /></automotiveApp>
  ```
- O serviço descoberto pelo host:
  ```xml
  <service android:name=".car.MyCarAppService" android:exported="true">
      <intent-filter>
          <action android:name="androidx.car.app.CarAppService" />
          <category android:name="androidx.car.app.category.IOT" />
      </intent-filter>
  </service>
  ```
- **Categorias existentes** (constantes da biblioteca): `NAVIGATION`, `POI`, `IOT`, `WEATHER`, `PARKING`, `CHARGING`, `SETTINGS`, `MESSAGING`, `CALLING`. **Não existe** categoria "dashboard"/"telemetria"/"OBD".
- **Templates**: só texto, ícone, lista, grade, pane, mensagem. Não há `View` nem `Canvas` dentro de template. Quota de **no máximo 5 templates por tarefa**.
- **Desenho livre**: `NavigationTemplate` (permissão `androidx.car.app.NAVIGATION_TEMPLATES`) ou `MapWithContentTemplate` (`NAVIGATION_TEMPLATES` ou `MAP_TEMPLATES`), mais `androidx.car.app.ACCESS_SURFACE`. Documentação permite `Canvas` **e** `VirtualDisplay` + `Presentation` (inclusive `ComposeView`).

## 2. A porteira da instalação

### O que foi medido no aparelho

| Tentativa | `installerPackageName` | `initiatingPackageName` | Apareceu na central? |
|---|---|---|---|
| `adb install app.apk` | `null` | `com.android.shell` | **Não** |
| `pm install -i com.android.vending` | `com.android.vending` | `com.android.shell` | **Não** |
| UnknownAuto + Shizuku | `com.android.vending` | `com.google.android.packageinstaller` | **Sim** |

Verificação (é o primeiro comando a rodar antes de ir ao carro):

```powershell
adb shell dumpsys package <seu.pacote> | Select-String "installerPackageName|initiatingPackageName"
```

### Por que "Fontes desconhecidas" não resolve

Texto oficial (`developer.android.com/training/cars/testing`):

> "This setting applies to media, messaging notifications, and parked apps but **doesn't apply to apps built using the Android for Cars App Library**."

O validador do Android Auto (engenharia reversa publicada, AA 17.7) lê `getInstallSourceInfo()`: usa `getInitiatingPackageName()` e, **só se** o iniciador for o instalador de pacotes (`com.google.android.packageinstaller`), consulta `getInstallingPackageName()`. Instalação por shell tem iniciador `com.android.shell` → negada silenciosamente, sem mensagem na central.

## 3. Receita de instalação que funciona

### 3.1 Preparar o celular

1. **Android Auto instalado e funcionando** na central (teste com Google Maps antes de qualquer coisa — sem isso, o resto não importa).
2. **Opções do desenvolvedor → Instalar via USB** ligado (obrigatório em Xiaomi/HyperOS; sem isso o `adb install` falha com `INSTALL_FAILED_USER_RESTRICTED`).
3. Depuração USB (ou por Wi-Fi, ver §6.1).

### 3.2 Ferramentas (open source)

| Ferramenta | Pacote | Papel |
|---|---|---|
| Shizuku | `moe.shizuku.privileged.api` | Dá privilégios de shell a um app comum |
| UnknownAuto | `com.mag.unknownauto` | Conduz o instalador do sistema marcando "vindo da Play" |
| (alternativa) AAEnabler | — | Mesma função, outra implementação |

### 3.3 Iniciar o Shizuku

O Shizuku 13.6 **não tem** inicialização só por ADB (o antigo `start.sh` não existe mais). Caminho suportado no Android 16:

1. Abrir o Shizuku → **Pareamento** (permitir notificação).
2. **Opções do desenvolvedor → Depuração por Wi-Fi → Parear dispositivo com um código** (deixar a tela aberta).
3. Digitar o código na notificação do Shizuku.
4. Voltar ao Shizuku → **Iniciar** (uma tela preta abre e fecha).

Conferir: `adb shell ps -A | Select-String shizuku_server`.

### 3.4 Instalar o app pelo UnknownAuto

O UnknownAuto **não lê `file://` de `/sdcard/Download`** (armazenamento restrito do Android 16). Copie o APK para a pasta que ele pode ler:

```powershell
adb push app-debug.apk /sdcard/Android/data/com.mag.unknownauto/files/app.apk
adb shell am force-stop com.mag.unknownauto
adb shell am start -a android.intent.action.VIEW `
  -d file:///sdcard/Android/data/com.mag.unknownauto/files/app.apk `
  -t application/vnd.android.package-archive -n com.mag.unknownauto/.InstallActivity
```

Na tela: **INSTALL …** → depois, no **Play Protect**, escolher **"Verificar app"** (não "Não instalar app").

O `tools/install-aa-apk.ps1` deste repositório automatiza esses passos e valida o resultado.

### 3.5 Confirmar antes de ir ao carro

1. Registro de instalação com os dois campos corretos (§2).
2. Hash do APK instalado igual ao local:
   ```powershell
   $p = (adb shell pm path <pacote>) -replace 'package:',''
   adb shell sha256sum $p
   ```
3. No app, uma tela de autodiagnóstico que mostre o próprio registro de instalação (foi o que salvou tempo aqui).

## 4. Atualizações

**Todo update precisa passar pelo UnknownAuto.** Instalar por ADB derruba o registro e o app **some** da central — sem aviso.

Antes de atualizar, garanta: celular desbloqueado, Shizuku em execução (ele para a cada reinício) e o Play Protect aceito.

## 5. Armadilhas conhecidas (sintoma → causa → solução)

| Sintoma | Causa | Solução |
|---|---|---|
| App não aparece na grade da central | Registro de instalação "do shell" | Reinstalar via UnknownAuto/AAEnabler/Internal App Sharing |
| App aparece e fecha / tela preta | Template recusado pelo host | Ter fallback de template (`PaneTemplate`/`MessageTemplate`) e registrar o erro numa tela de diagnóstico |
| DHU mostra tudo, carro não mostra nada | DHU é developer head unit e pula a checagem de instalador | Nunca usar DHU como prova de distribuição |
| `adb install` falha: `INSTALL_FAILED_USER_RESTRICTED` | Xiaomi/HyperOS exige "Instalar via USB" | Ligar em Opções do desenvolvedor |
| UnknownAuto não carrega o APK | `file://` bloqueado em `/sdcard/Download` | Copiar para `Android/data/com.mag.unknownauto/files/` |
| Play Protect bloqueia a instalação | APK não analisado | Tocar em "Verificar app" (não desativar a proteção) |
| Botão "abrir Android Auto" diz "não encontrado" | (a) Android 11+ esconde pacotes; (b) AA 18 não tem ícone de launcher | Declarar `<queries><package android:name="com.google.android.projection.gearhead"/></queries>` e cair para `ACTION_APPLICATION_DETAILS_SETTINGS` |
| Shizuku não inicia por adb | Versão 13+ removeu o `start.sh` | Pareamento por depuração sem fio pelo app |
| `adb` por Wi-Fi cai sozinho | Porta muda ao religar a depuração sem fio / troca de rede | Reconectar com a porta atual |
| Números "pulam" no painel | Dígitos proporcionais | `Paint.fontFeatureSettings = "tnum"` |

## 6. Truques de diagnóstico

### 6.1 ADB por Wi-Fi (sem cabo no PC)

```powershell
adb pair <ip>:<porta-de-pareamento> <codigo-de-6-digitos>   # a tela de pareamento mostra os dois
adb connect <ip>:<porta-de-conexao>                          # porta diferente da de pareamento
```

Útil para acompanhar o carro: `adb logcat` durante a conexão. O validador do AA aparece em linhas `CAR.VALIDATOR` / `Package DENIED`.

### 6.2 Ver o que a central entregou

Ter, dentro do app, uma tela de diagnóstico mostrando: superfície disponível, resolução e dpi, quadros desenhados, fps, fonte de dados e último erro. Isso responde no carro o que o logcat responderia no PC.

### 6.3 Não usar o celular como cobaia de automação

`input tap` às cegas no aparelho do usuário navega por apps pessoais. Se for automatizar toques, confira cada passo lendo a hierarquia de UI (`uiautomator dump`) **antes** de tocar.

## 7. Testar sem o hardware

- **Sem o carro**: o DHU valida desenho, manifesto e quota de templates — **não** valida distribuição.
- **Sem o hardware alvo** (sensores, adaptadores): emular o **dispositivo**, não os valores na UI. Um emulador do protocolo (ELM327, por exemplo) exercita o caminho de produção inteiro: comando → transporte → resposta crua → decodificação. É a diferença entre testar a tela e testar o produto.
- **Sem a central**: gerar PNGs pelo próprio renderizador para revisar layout; nunca usar mockup como prova.

## 8. Design para tela automotiva (o que se provou necessário)

1. **Leitura em menos de 1 segundo.** Uma informação domina; o resto apoia.
2. **Tema escuro e contraste alto** (uso noturno; e o AA exige suporte a dark).
3. **Dígitos tabulares** para não haver "pulos" de largura.
4. **Cor nunca é o único indicador** — sempre acompanhada de rótulo e número.
5. **Indisponível ≠ zero.** Sem leitura, mostrar `—`.
6. **Avisos de dado simulado/velho em faixa de texto**, não em ícone discreto.
7. **Sem animação decorativa**; só o valor muda.
8. **Layout adaptável**: validar pelo menos 1920x720, 1280x720 e **800x480** (central de 7" é comum nessa resolução).

## 9. Checklist antes de ir ao carro

- [ ] `initiatingPackageName=com.google.android.packageinstaller`
- [ ] `installerPackageName=com.android.vending`
- [ ] Hash do APK instalado confere com o build
- [ ] App tem tela de autodiagnóstico com o registro de instalação
- [ ] Painel validado em 1920x720, 1280x720 e 800x480
- [ ] Existe fallback de template caso o host recuse o principal
- [ ] Dados simulados são sinalizados de forma impossível de ignorar
- [ ] Atualizações só pelo caminho que preserva o registro

## 10. O que continua não confirmado

- Se o host aceita `NavigationTemplate` de um app de categoria `IOT` em **todas** as versões do AA. Aqui funcionou (IOT + `NAVIGATION_TEMPLATES` + `ACCESS_SURFACE`); a checagem documentada é de permissão de manifesto, não de categoria, mas o comportamento pode variar por fabricante/versão.
- Limite numérico de refresh de template (a documentação só diz que pode ser "throttled").
- Se a checagem do validador muda entre versões do Android Auto — ela não é API pública e pode quebrar sem aviso.

---

## Referências locais

- Receita detalhada e medições: [`docs/instalacao-android-auto.md`](instalacao-android-auto.md)
- Automação: [`tools/install-aa-apk.ps1`](../tools/install-aa-apk.ps1)
- Pesquisa de Android Auto (com fontes e datas): [`docs/pesquisa/android-auto.md`](pesquisa/android-auto.md)
- Relatório de viabilidade do projeto: [`docs/ETAPA-ZERO.md`](ETAPA-ZERO.md)
