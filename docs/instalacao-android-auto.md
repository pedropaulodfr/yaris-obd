# Instalação no Android Auto — receita comprovada no Yaris

> **Estado:** comprovado no veículo em **13/11/2026**. O app apareceu na grade de apps da central original do Toyota Yaris 2021, abriu e desenhou o painel.

Este documento registra o **único caminho que funcionou**, com as medições feitas no aparelho. Se algum dia o app deixar de aparecer depois de um update, é aqui que se recomeça.

---

## O problema, em uma frase

O Android Auto **não pergunta se o app está instalado: pergunta quem o instalou.** Se o registro de instalação não apontar para o instalador de pacotes do sistema agindo em nome da Play Store, o app é silenciosamente omitido da grade da central — sem erro visível para o usuário.

## O que NÃO funciona (medido)

| Tentativa | Registro resultante | Resultado na central |
|---|---|---|
| `adb install app.apk` | `installer=null`, `initiating=com.android.shell` | **Não aparece** |
| `pm install -i com.android.vending` | `installer=com.android.vending`, `initiating=com.android.shell` | **Não aparece** |
| Ativar "Fontes desconhecidas" no modo desenvolvedor | — | **Não se aplica** a apps da Car App Library (documentação oficial) |

## O que funciona (medido)

Registro final, lido com `dumpsys package com.pedro.yarisobd`:

```
versionName=0.1.0-poc-debug
installerPackageName=com.android.vending
initiatingPackageName=com.google.android.packageinstaller
originatingPackageName=com.android.shell
```

Com `initiatingPackageName=com.google.android.packageinstaller`, o app apareceu na central.

---

## Passo a passo

> Atalho: `tools/install-aa-apk.ps1` faz os passos 3 a 5 abaixo (copia, abre o UnknownAuto, toca em INSTALL, resolve o Play Protect e valida o registro). Os passos manuais ficam aqui como referência do que o script faz.

### 1. Pré-requisitos

- Celular com Android Auto funcionando (Android 9+ na documentação; na prática 11+).
- `app-debug.apk` do projeto.
- As duas ferramentas open source (baixadas dos releases oficiais):
  - **Shizuku** — `moe.shizuku.privileged.api` — <https://github.com/RikkaApps/Shizuku/releases>
  - **UnknownAuto** — `com.mag.unknownauto` — <https://github.com/GitMag/UnknownAuto/releases>

### 2. Instalar as duas ferramentas

Por ADB (não importa a origem, essas duas não vão no Android Auto):

```powershell
adb install -r shizuku-13.6.0.apk
adb install -r unknownauto-1.6.apk
```

### 3. Iniciar o Shizuku

O Shizuku 13.6 **não tem mais** inicialização só por ADB (o antigo `start.sh` não existe nesta versão). No Android 16 ele inicia por pareamento de depuração sem fio:

1. Abra o **Shizuku**.
2. Em *Iniciar via depuração sem fio*, toque em **Pareamento** e permita a notificação (fica em "Procurando serviço de pareamento").
3. **Opções do desenvolvedor → Depuração por Wi-Fi → Parear dispositivo com um código**. Anote o código e **deixe a tela aberta**.
4. Puxe a notificação do Shizuku → **Inserir código de pareamento** → digite os 6 dígitos.
5. Volte ao Shizuku e toque em **Iniciar**. Uma tela preta abre e fecha — é normal.

Verificação: `adb shell ps -A | grep shizuku` deve mostrar `shizuku_server`.

### 4. Instalar o nosso app pelo UnknownAuto

1. Copie o APK para o celular (ex.: `adb push app-debug.apk /sdcard/Download/yaris-obd.apk`).
2. Abra o **UnknownAuto** → **Select local APK** → escolha o APK → **Install**.
3. Autorize o Shizuku quando ele pedir ("Permitir o tempo todo").

### 5. Confirmar o registro

```powershell
adb shell dumpsys package com.pedro.yarisobd | Select-String "installerPackageName|initiatingPackageName"
```

Precisa sair `initiatingPackageName=com.google.android.packageinstaller`. O próprio app também mostra isso na tela inicial, em "Instalado por".

### 6. Testar no carro

Cabo USB na central → grade de apps → **Yaris OBD**.

---

## Regra de manutenção

**Todo update do app precisa passar pelo UnknownAuto.** Se você instalar uma versão nova por `adb install`, o registro volta para `com.android.shell` e o app **some da central** — sem aviso. O autodiagnóstico na tela inicial existe justamente para pegar isso antes de você ir ao carro.

### Atualizando uma versão (procedimento usado e comprovado)

```powershell
# 1. colocar o APK onde o UnknownAuto consegue ler (o file:// de /sdcard/Download
#    é bloqueado pelo armazenamento restrito do Android 16)
adb push app-debug.apk /sdcard/Android/data/com.mag.unknownauto/files/yaris-obd.apk

# 2. abrir o UnknownAuto já com o APK carregado
adb shell am force-stop com.mag.unknownauto
adb shell am start -a android.intent.action.VIEW `
  -d file:///sdcard/Android/data/com.mag.unknownauto/files/yaris-obd.apk `
  -t application/vnd.android.package-archive -n com.mag.unknownauto/.InstallActivity
```

3. Na tela do UnknownAuto, tocar em **INSTALL YARIS OBD FOR ANDROID AUTO**.
4. **Play Protect**: na primeira instalação de cada versão aparece "Este app ainda não foi analisado pelo Play Protect". Tocar em **Verificar app** (não em "Não instalar app") — depois da verificação a instalação conclui. O caminho seguro é verificar, não desativar a proteção.
5. Conferir que a versão instalada é a nova:

```powershell
$p = (adb shell pm path com.pedro.yarisobd) -replace 'package:',''
adb shell sha256sum $p     # precisa bater com o hash do APK local
```

O registro (`initiatingPackageName=com.google.android.packageinstaller`) se mantém entre atualizações feitas por esse caminho.

### Observações de campo

- O celular **precisa estar desbloqueado** para o UnknownAuto concluir; com a tela bloqueada o toque não chega ao app.
- O `adb` por Wi-Fi cai quando o aparelho troca de rede ou reinicia; refaça `adb connect <ip>:<porta>` (a porta muda a cada vez que a depuração sem fio é religada).
- O Shizuku precisa estar em execução **antes** da atualização; depois de reiniciar o celular ele para e precisa ser iniciado de novo.

## Limitações conhecidas

- Depende de ferramenta não oficial: um update do Android Auto pode quebrar o caminho e exigir reinstalação.
- Se o Shizuku for encerrado (ex.: depois de reiniciar o celular), ele precisa ser iniciado de novo **antes** de instalar atualizações.
- Não há garantia de funcionamento permanente: a rota oficial equivalente é *Internal App Sharing* do Play Console, que dispensa esse contorno.

## Evidência

- Autodiagnóstico do app após a instalação correta: [`docs/img/redmi-instalacao-ok.png`](../img/redmi-instalacao-ok.png)
- Relatório de viabilidade que previu esse gate: [`docs/ETAPA-ZERO.md`](../ETAPA-ZERO.md)
