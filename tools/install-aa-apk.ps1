<#
.SYNOPSIS
    Instala/atualiza um APK no Android Auto pelo UnknownAuto + Shizuku, que é o
    único caminho descoberto que faz o app aparecer numa central original.

.DESCRIPTION
    Por que este script existe: uma instalação por `adb install` (ou mesmo
    `pm install -i com.android.vending`) deixa o registro de instalação com
    `initiatingPackageName=com.android.shell`, e o Android Auto simplesmente
    ignora o app — sem erro visível na tela do carro.

    O que ele faz:
      1. escolhe o aparelho (USB ou Wi-Fi);
      2. valida o APK e descobre pacote/versão;
      3. confere se o Shizuku está rodando (sem ele o UnknownAuto não instala);
      4. copia o APK para a pasta que o UnknownAuto consegue ler
         (file:// de /sdcard/Download é bloqueado no Android 16);
      5. abre o UnknownAuto já com o APK carregado;
      6. com -AutoTap, toca em INSTALL e, se o Play Protect aparecer, em
         "Verificar app";
      7. valida o resultado: hash do APK instalado e registro de instalação.

.PARAMETER Apk
    Caminho do APK a instalar. Obrigatório.

.PARAMETER Device
    Serial do aparelho (ex.: 192.168.0.4:39289). Opcional se só houver um.

.PARAMETER AutoTap
    Liga a automação de toques. Sem esta opção, o script para depois de abrir o
    UnknownAuto e imprime o que você deve tocar.

.PARAMETER TimeoutSeconds
    Tempo máximo de espera pela conclusão da instalação (padrão 90).

.EXAMPLE
    .\install-aa-apk.ps1 -Apk .\app\build\outputs\apk\debug\app-debug.apk

.EXAMPLE
    .\install-aa-apk.ps1 -Apk app.apk -Device 192.168.0.4:39289 -AutoTap
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Apk,

    [string]$Device,

    [switch]$AutoTap,

    [int]$TimeoutSeconds = 90
)

# 'Continue' de propósito: o adb escreve progresso em stderr (ex.: "1 file pushed"),
# e com 'Stop' o PowerShell trataria isso como erro fatal. Os erros reais são
# detectados pelo código de saída em Invoke-Adb.
$ErrorActionPreference = 'Continue'

# ---------- utilidades ----------

function Write-Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "  OK  $text" -ForegroundColor Green }
function Write-Warn2($text) { Write-Host "  !   $text" -ForegroundColor Yellow }
function Fail($text) { Write-Host "  ERRO $text" -ForegroundColor Red; exit 1 }

function Resolve-Adb {
    if ($env:ANDROID_HOME -and (Test-Path (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'))) {
        return (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe')
    }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    Fail 'adb não encontrado. Defina ANDROID_HOME ou coloque o adb no PATH.'
}

$Adb = Resolve-Adb

function Invoke-Adb {
    param([string[]]$Arguments, [switch]$AllowFail)
    $output = & $Adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0 -and -not $AllowFail) {
        Fail "adb $($Arguments -join ' ') falhou:`n$output"
    }
    return ($output | Out-String).Trim()
}

function Get-UiXml {
    $remote = '/data/local/tmp/aa_install_ui.xml'
    Invoke-Adb -Arguments @('-s', $device, 'shell', 'uiautomator', 'dump', $remote) -AllowFail | Out-Null
    $local = Join-Path $env:TEMP 'aa_install_ui.xml'
    Invoke-Adb -Arguments @('-s', $device, 'pull', $remote, $local) -AllowFail | Out-Null
    if (-not (Test-Path $local)) { return '' }
    return (Get-Content $local -Raw)
}

# Toca no nó cujo texto casa com o regex. Retorna $true se tocou.
function Tap-ByText {
    param([string]$Pattern)
    $xml = Get-UiXml
    if (-not $xml) { return $false }

    foreach ($node in [regex]::Matches($xml, '<node[^>]*>')) {
        $text = [regex]::Match($node.Value, 'text="([^"]*)"').Groups[1].Value
        if (-not $text) { continue }
        if ($text -notmatch $Pattern) { continue }

        $bounds = [regex]::Match($node.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $bounds.Success) { continue }

        $cx = ([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2
        $cy = ([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2
        Invoke-Adb -Arguments @('-s', $device, 'shell', 'input', 'tap', "$cx", "$cy") -AllowFail | Out-Null
        Write-Ok "toque em '$text' ($cx,$cy)"
        return $true
    }
    return $false
}

# ---------- preparação ----------

if (-not (Test-Path -LiteralPath $Apk)) { Fail "APK não encontrado: $Apk" }
$Apk = (Resolve-Path -LiteralPath $Apk).Path
$localHash = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash

Write-Step 'Aparelhos conectados'
$devices = @((& $Adb devices) | Select-String '\s+device$' | ForEach-Object { ($_ -split '\s+')[0] })
if ($devices.Count -eq 0) { Fail 'Nenhum aparelho conectado. Ligue o cabo (ou `adb connect <ip>:<porta>`).' }
if ($Device) {
    if ($devices -notcontains $Device) { Fail "Aparelho '$Device' não está na lista: $($devices -join ', ')" }
    $device = $Device
} elseif ($devices.Count -eq 1) {
    $device = $devices[0]
} else {
    Fail "Mais de um aparelho conectado. Use -Device. Opções: $($devices -join ', ')"
}
Write-Ok "aparelho: $device"

Write-Step 'Validando o APK'
$aapt = $null
if ($env:ANDROID_HOME) {
    $candidate = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($candidate) {
        $p = Join-Path $candidate.FullName 'aapt2.exe'
        if (Test-Path $p) { $aapt = $p }
    }
}
$package = $null
if ($aapt) {
    $badging = & $aapt dump badging $Apk 2>$null
    $package = [regex]::Match(($badging | Out-String), "name='([^']+)'").Groups[1].Value
    $versionCode = [regex]::Match(($badging | Out-String), "versionCode='([^']+)'").Groups[1].Value
    Write-Ok "pacote: $package (versionCode $versionCode)"
} else {
    Write-Warn2 'aapt2 não encontrado; não dá para validar o APK antes de instalar.'
}

Write-Step 'Shizuku'
$ps = Invoke-Adb -Arguments @('-s', $device, 'shell', 'ps', '-A') -AllowFail
if ($ps -match 'shizuku_server') {
    Write-Ok 'shizuku_server em execução'
} else {
    Write-Warn2 'Shizuku NÃO está rodando — o UnknownAuto vai falhar. Abra o Shizuku e inicie (pareamento por depuração sem Wi-Fi).'
    Write-Warn2 'Lembre-se: ele para a cada reinício do celular.'
}

Write-Step 'Copiando o APK para a pasta do UnknownAuto'
$remoteDir = '/sdcard/Android/data/com.mag.unknownauto/files'
Invoke-Adb -Arguments @('-s', $device, 'shell', 'mkdir', '-p', $remoteDir) -AllowFail | Out-Null
$remoteApk = "$remoteDir/aa-install.apk"
Invoke-Adb -Arguments @('-s', $device, 'push', $Apk, $remoteApk) | Out-Null
Write-Ok "enviado para $remoteApk"
Write-Warn2 'Se o UnknownAuto não carregar o APK, é porque o file:// de /sdcard/Download está bloqueado — este caminho resolve.'

Write-Step 'Abrindo o UnknownAuto com o APK'
Invoke-Adb -Arguments @('-s', $device, 'shell', 'am', 'force-stop', 'com.mag.unknownauto') -AllowFail | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb -Arguments @(
    '-s', $device, 'shell', 'am', 'start',
    '-a', 'android.intent.action.VIEW',
    '-d', "file://$remoteApk",
    '-t', 'application/vnd.android.package-archive',
    '-n', 'com.mag.unknownauto/.InstallActivity'
) -AllowFail | Out-Null
Start-Sleep -Seconds 3

if (-not $AutoTap) {
    Write-Host ''
    Write-Host 'Ação manual necessária:' -ForegroundColor Yellow
    Write-Host '  1. Na tela do UnknownAuto, toque em "INSTALL ...".'
    Write-Host '  2. Se o Play Protect aparecer, toque em "Verificar app" (NÃO em "Não instalar app").'
    Write-Host '  3. Rode o script de novo com -AutoTap para automatizar, ou confira o resultado abaixo.'
} else {
    Write-Step 'Tocando em INSTALL'
    if (-not (Tap-ByText '^INSTALL.*FOR ANDROID AUTO')) {
        Write-Warn2 'Botão INSTALL não encontrado. O celular pode estar bloqueado — desbloqueie e tente de novo.'
    }

    # Play Protect costuma aparecer alguns segundos depois.
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $verified = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (-not $verified -and (Tap-ByText '^Verificar app$')) {
            $verified = $true
            continue
        }
        $p = Invoke-Adb -Arguments @('-s', $device, 'shell', 'pm', 'path', $package) -AllowFail
        if ($p -match 'package:') {
            $path = ($p -replace 'package:', '').Trim()
            $hash = ((Invoke-Adb -Arguments @('-s', $device, 'shell', 'sha256sum', $path) -AllowFail) -split '\s+')[0]
            if ($hash -eq $localHash.ToLower()) { break }
        }
    }
}

# ---------- validação ----------

Write-Step 'Validação'

if ($package) {
    $path = ((Invoke-Adb -Arguments @('-s', $device, 'shell', 'pm', 'path', $package) -AllowFail) -replace 'package:', '').Trim()
    # Quando o aparelho desconecta no meio, o adb devolve mensagem de erro em vez
    # de caminho. Validar o formato evita relatar "hash diferente" sem sentido.
    if (-not $path -or -not $path.StartsWith('/')) {
        Fail "O pacote $package não está instalado (a instalação não concluiu). Confira se o celular está desbloqueado e se o Shizuku segue em execução."
    }
    $installedHash = (((Invoke-Adb -Arguments @('-s', $device, 'shell', 'sha256sum', $path) -AllowFail) -split '\s+')[0]).ToUpper()
    if ($installedHash -eq $localHash) {
        Write-Ok 'hash do APK instalado confere com o build local'
    } else {
        Write-Warn2 "hash diferente — instalado $installedHash, local $localHash (a instalação não concluiu?)"
    }

    $info = Invoke-Adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'package', $package) -AllowFail
    $installing = [regex]::Match($info, 'installerPackageName=(\S+)').Groups[1].Value
    $initiating = [regex]::Match($info, 'initiatingPackageName=(\S+)').Groups[1].Value

    Write-Host "  installerPackageName  = $installing"
    Write-Host "  initiatingPackageName = $initiating"

    if ($initiating -eq 'com.google.android.packageinstaller' -and $installing -eq 'com.android.vending') {
        Write-Host ''
        Write-Host '  RESULTADO: Android Auto deve aceitar este app.' -ForegroundColor Green
        exit 0
    }

    Write-Host ''
    Write-Host '  RESULTADO: o registro NÃO está correto — o app não vai aparecer na central.' -ForegroundColor Red
    Write-Host '  Instale pelo UnknownAuto (com Shizuku ativo) ou por Internal App Sharing.' -ForegroundColor Red
    exit 1
}

Write-Warn2 'Sem o nome do pacote não foi possível validar o registro. Confira manualmente com dumpsys.'
exit 0
