<#
  getdoc.ps1 - baixa uma pagina HTML e extrai o texto do conteudo principal.

  Uso:
    pwsh -NoProfile -File tools/getdoc.ps1 -Url 'https://...' [-MaxLines 400] [-Raw]

  Objetivo: permitir leitura de documentacao oficial (developer.android.com,
  github, foruns) sem o ruido de navegacao, e salvar o texto bruto em
  docs/research/raw/ para rastreabilidade das fontes.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Url,

    [int]$MaxLines = 600,

    [string]$Out,

    # Quando presente, imprime o HTML/JSON cru em vez do texto extraido.
    [switch]$Raw
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$headers = @{
    'User-Agent'      = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'
    'Accept-Language' = 'en-US,en;q=0.9'
}

$resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -Headers $headers -TimeoutSec 90
$html = $resp.Content
Write-Output "STATUS=$($resp.StatusCode) BYTES=$($html.Length) URL=$Url"

if ($Raw) {
    if ($Out) { $html | Set-Content -LiteralPath $Out -Encoding utf8 }
    else { Write-Output $html }
    return
}

# Recorta o miolo do documento: sem <script>, <style>, comentarios e sem a
# navegacao global do devsite (que ocupa ~150 linhas em todas as paginas).
$t = $html
$t = [regex]::Replace($t, '(?is)<script.*?</script>', ' ')
$t = [regex]::Replace($t, '(?is)<style.*?</style>', ' ')
$t = [regex]::Replace($t, '(?is)<noscript.*?</noscript>', ' ')
$t = [regex]::Replace($t, '(?is)<!--.*?-->', ' ')

# Preferencia: <article>; fallback: do marcador main-content ate o rodape.
if ($t -match '(?is)<article[^>]*>(.*?)</article>') {
    $t = $Matches[1]
} elseif ($t -match '(?is)id="main-content"(.*?)(<footer|</body>)') {
    $t = $Matches[1]
}

# Preserva alguma estrutura util antes de tirar as tags.
$t = [regex]::Replace($t, '(?i)</(p|div|li|tr|h1|h2|h3|h4|h5|pre|table|section)>', "`n")
$t = [regex]::Replace($t, '(?i)<br\s*/?>', "`n")
$t = [regex]::Replace($t, '(?i)</t[dh]>', ' | ')
$t = [regex]::Replace($t, '(?is)<[^>]+>', ' ')

$t = [Net.WebUtility]::HtmlDecode($t)
$t = [regex]::Replace($t, '[ \t\u00a0]+', ' ')

$lines = $t -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 }

$text = ($lines | Select-Object -First $MaxLines) -join "`n"

if ($Out) {
    $dir = Split-Path -Parent $Out
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $text | Set-Content -LiteralPath $Out -Encoding utf8
    Write-Output "SALVO=$Out LINHAS=$($lines.Count)"
}

Write-Output '--- INICIO ---'
Write-Output $text
Write-Output '--- FIM ---'
