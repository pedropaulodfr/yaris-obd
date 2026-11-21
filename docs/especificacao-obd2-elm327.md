# ESPECIFICAÇÃO TÉCNICA — CAMADA OBD-II / ELM327

**Projeto:** app Android (Kotlin, uso pessoal, distribuição por APK)
**Veículo alvo:** Toyota Yaris 2021 — Brasil (flex)
**Documento:** AGENTE 2 — especialista em OBD2/ELM327
**Versão:** 1.0.0
**Escopo:** camada de transporte/protocolo. Não define UI, banco de dados, nem o app inteiro — define **contratos, comandos, fórmulas e critérios de aceite** para que a camada seja implementável e testável isoladamente.
**Data:** 2026-11-12

---

## Legenda de confiança das afirmações

| Marca | Significado |
|---|---|
| `[CONFIRMADO: URL]` | Fato lido diretamente na fonte citada nesta sessão (spec, código-fonte, datasheet). |
| `[HIPÓTESE]` | Dedução técnica plausível, coerente com as fontes, **não** verificada explicitamente. Deve virar teste antes de virar requisito duro. |
| `[NÃO CONFIRMADO]` | Não achei fonte primária; não use como base de implementação sem medir/validar. |

Regra do projeto: **nenhum PID, fórmula ou comando entra no código sem estar marcado `[CONFIRMADO]` neste documento**. O que estiver `[HIPÓTESE]`/`[NÃO CONFIRMADO]` deve ser tratado como funcionalidade opcional e degradar de forma segura.

---

## Sumário

0. [Decisões de arquitetura (TL;DR)](#0-decisões-de-arquitetura-tldr)
1. [Protocolo OBD-II](#1-protocolo-obd-ii)
2. [ELM327 — comandos AT](#2-elm327--comandos-at)
3. [Conectividade](#3-conectividade)
4. [PIDs — fórmulas exatas (Modo 01)](#4-pids--fórmulas-exatas-modo-01)
5. [Diagnóstico (modos 02/03/04/07/09/0A e PID 01)](#5-diagnóstico)
6. [Arquitetura modular (Kotlin)](#6-arquitetura-modular-kotlin)
7. [Testabilidade](#7-testabilidade)
8. [URLs consultadas](#8-urls-consultadas)
9. [Lacunas e o que falta validar no carro](#9-lacunas-e-o-que-falta-validar-no-carro)

---

## 0. Decisões de arquitetura (TL;DR)

Estas são as decisões que o restante do documento justifica. Se outra pessoa (ou outro agente) for implementar, comece por aqui.

| # | Decisão | Justificativa curta |
|---|---|---|
| D1 | Transporte primário: **Bluetooth Classic SPP** (RFCOMM). | Menor latência estável, menor consumo, sem perder internet do celular, API Android estável. Ver §3.5. |
| D2 | Transportes suportados na v1: **BT SPP** e **TCP Wi-Fi**. **BLE** e **USB serial** entram como adaptadores opcionais da mesma interface `Transport`. | Custo/benefício; mantém a camada de protocolo agnóstica. |
| D3 | O `ElmSession` é **serializado por mutex**: nunca dois comandos ao mesmo tempo no ELM. | Qualquer byte novo no RS232 aborta o comando em curso (gera `STOPPED`). Ver §2.4. |
| D4 | Protocolo: **descoberta automática uma vez** (`ATSP0` + `0100` + `ATDPN`), depois **fixar** (`ATSP6` para CAN 11/500). | Auto-detecção é lenta e instável em clones; fixar remove segundos de cada conexão. Ver §2.4. |
| D5 | **Headers OFF** (`ATH0`) no ciclo normal; `ATH1` só em diagnóstico/descoberta. | `ATH0` encurta a resposta e simplifica o parser; `ATH1` identifica o ECU (7E8 etc.). |
| D6 | **`ATCAF1`** (auto-formatting CAN) e **`ATCFC1`** (flow control automático) ligados. | O ELM resolve ISO-TP sozinho; sem isso o app teria de enviar Flow Control. Ver §1.4.4. |
| D7 | Cada PID tem estado explícito: `Disponível/Fresco`, `Stale`, `SemDado`, `NãoSuportado`, `Erro`. UI nunca mostra valor velho como se fosse atual. | Requisito de segurança de leitura. Ver §6.9. |
| D8 | Meta de ciclo: **3–4 PIDs de painel a 4–6 Hz**; demais em round-robin de 0,5–1 Hz. | Orçamento real: 12–20 comandos/s num clone e 25–35 num dongle bom. Ver §6.10. |
| D9 | `VehicleProfile` é **cacheado por VIN** e inclui o bitmask de PIDs suportados. | Evita re-testar PIDs a cada conexão. |
| D10 | Testes sem carro: **emulador TCP + transcripts gravados**. | Permite CI e desenvolvimento sem veículo. Ver §7. |

---

## 1. Protocolo OBD-II

### 1.1 Camada física — conector SAE J1962

O conector é o DLC (Data Link Connector) de 16 pinos, fêmea, em forma de D, sob o painel do motorista (dentro de ~60 cm do volante). Existem dois tipos: **Type A** (12 V, veículos de passeio) e **Type B** (24 V, com sulco central interrompido e marcado em azul) `[CONFIRMADO: https://en.wikipedia.org/wiki/Data_link_connector]`.

Pinagem (o que importa para o app):

| Pino | Função | Protocolo |
|---|---|---|
| 2 | J1850 Bus+ | SAE J1850 PWM / VPW |
| 4 | Terra de chassi | — |
| 5 | Terra de sinal | — |
| 6 | CAN High | ISO 15765-4 |
| 7 | K-Line | ISO 9141-2 / ISO 14230-4 (KWP) |
| 10 | J1850 Bus− | SAE J1850 PWM |
| 14 | CAN Low | ISO 15765-4 |
| 15 | L-Line | ISO 9141-2 (opcional, muitos carros não usam) |
| 16 | +12 V bateria (normalmente não chaveado) | alimenta o scanner |
| 1, 3, 8, 9, 11, 12, 13 | Fabricante (Ford MS-CAN em 3/11, GM SW-CAN em 1) | — |

`[CONFIRMADO: https://elm327obd2.com/elm327-commands/]` — mesma pinagem, com a observação de que o pino 16 permanece energizado com a chave desligada (por isso o adaptador fica consumindo bateria e existe o sleep mode).

Consequências práticas para o app:
- O dongle liga ao plugar; **não** há sinal de "conectado ao carro" além da resposta do protocolo.
- Se `AT RV` (tensão) cair abaixo de ~11,5 V com o carro desligado, o adaptador pode resetar durante o crank. O app deve **tolerar reconexão** (§6.3).
- Carro 2021 no Brasil: pino 6/14 (CAN) presentes; pinos 2/10/7 normalmente ausentes.

### 1.2 Camadas de protocolo e o que o ELM327 fala

O ELM327 implementa 9 protocolos OBD + J1939 `[CONFIRMADO: https://en.wikipedia.org/wiki/ELM327]`:

| Código `ATSP` | Protocolo | Velocidade | Pinos | Carros típicos |
|---|---|---|---|---|
| 1 | SAE J1850 **PWM** | 41,6 kbit/s | 2, 10 | Ford/GM antigos (EUA) |
| 2 | SAE J1850 **VPW** | 10,4 kbit/s | 2 | GM antigos (EUA) |
| 3 | ISO 9141-2 | 10,4 kbit/s, init 5 baud | 7 (15 opcional) | Asiáticos/europeus ~1996–2003 |
| 4 | ISO 14230-4 KWP2000 (5 baud) | 10,4 kbit/s | 7 | Europeus/asiáticos ~2000–2007 |
| 5 | ISO 14230-4 KWP2000 (fast init) | 10,4 kbit/s | 7 | idem |
| **6** | **ISO 15765-4 CAN, 11-bit, 500 kbit/s** | **500 kbit/s** | **6, 14** | **padrão de fato desde ~2008** |
| 7 | ISO 15765-4 CAN, 29-bit, 500 kbit/s | 500 kbit/s | 6, 14 | heavy-duty, alguns EUA |
| 8 | ISO 15765-4 CAN, 11-bit, 250 kbit/s | 250 kbit/s | 6, 14 | alguns asiáticos |
| 9 | ISO 15765-4 CAN, 29-bit, 250 kbit/s | 250 kbit/s | 6, 14 | heavy-duty |
| A | SAE J1939 (29-bit, 250 kbit/s) | 250 kbit/s | 6, 14 | caminhões/ônibus |

`[CONFIRMADO: https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf — seção "SP h"]` e `[CONFIRMADO: https://en.wikipedia.org/wiki/ELM327]`.

O padrão de emissões que rege tudo isso é o **SAE J1979** (serviços/PIDs) sobre **ISO 15765-4** (quando CAN) `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs]`. O ISO 15765-4:2021 é a parte 4 do DoCAN (requisitos para sistemas de emissões) `[CONFIRMADO: https://www.iso.org/standard/78384.html]`.

### 1.3 O que um Toyota Yaris 2021 (Brasil) usa

**Afirmação:** o Yaris 2021 brasileiro usa **ISO 15765-4 CAN, 11-bit, 500 kbit/s (protocolo 6)** `[HIPÓTESE]`.

Base da hipótese:
- O Yaris XP150 vendido no Brasil é produzido em Sorocaba/SP e é um projeto global Toyota da era pós-2008; CAN 11/500 é o padrão Toyota para OBD-II nesse período `[HIPÓTESE]`.
- Todo veículo OBD-II moderno migrou para CAN; o ELM327 só fala CAN em 6/7/8/9.
- Confirmado indiretamente: "a maioria dos carros americanos de 2008 em diante responde em 6" `[CONFIRMADO: https://elm327obd2.com/elm327-commands/]`.

**Como confirmar em 30 segundos no carro (obrigatório antes de fixar o protocolo):**
```
ATZ
ATE0
ATSP0
0100          → dispara a busca; pode responder "SEARCHING..."
ATDPN         → ex.: "A6"  (A = encontrado automaticamente, 6 = CAN 11/500)
ATDP          → ex.: "AUTO, ISO 15765-4 (CAN 11/500)"
```
Se `ATDPN` devolver `A6`, fixar `ATSP6` na conexão seguinte. Se devolver `A8`, fixar `ATSP8`. Qualquer outro valor: manter auto e registrar em log `[CONFIRMADO: https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf — seção "DP/DPN"]`.

> **Atenção:** fixar protocolo errado faz o ELM responder `UNABLE TO CONNECT` e **não** tentar os outros (o `ATSP h` com h ≠ 0 desabilita a busca). Isso está explícito no datasheet: "If another protocol (other than 0) is selected … that protocol will become the default, and will be the only protocol used" `[CONFIRMADO: ELM327DS.pdf — SP h]`.

### 1.4 Estrutura da mensagem de diagnóstico

#### 1.4.1 Requisição e resposta no nível J1979

Requisição = **2 bytes**: `modo` + `PID` (quando o serviço tem PID). Ex.: `01 0C` = "Modo 01, PID 0C (RPM)".
Resposta = `0x40 + modo` + `PID` + dados. Ex.: `41 0C 1A F8` `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs]` (tabela Service 01 e exemplo `41 0C 1A F8` em `https://elm327obd2.com/elm327-commands/`).

- `41` = confirmação do modo 01.
- `0C` = PID ecoado.
- `1A F8` = dados (A=0x1A, B=0xF8) → RPM = (256·26 + 248)/4 = **1726 rpm** `[CONFIRMADO: https://elm327obd2.com/elm327-commands/]`.

#### 1.4.2 O byte NOD (Number Of Data bytes)

Em CAN, cada resposta chega em um **Single Frame** cujo primeiro byte é o PCI: nibble alto `0` (tipo SF) e nibble baixo = **NOD** (quantidade de bytes de dados que seguem). Exemplo de `0100` com `ATH0` e `ATCAF1` (o ELM remove o PCI e mostra só o payload):

```
06 41 00 BE 1F A8 13
```
Aqui `06` diz que há 6 bytes de dados (`41 00` + 4 bytes de bitmask). Um PID de 1 byte retorna `03 41 0D 32` (3 bytes: `41 0D 32`). O parser **não deve assumir tamanho fixo**: deve usar o NOD quando disponível e, no modo formatado (CAF1), o tamanho da linha.

> Com `ATCAF1` o ELM já remove o PCI e as linhas do modo texto começam em `41`/`49`/etc. O `06` acima só aparece se você estiver lendo frames crus (`ATCAF0`) ou monitorando (`ATMA`).

`[CONFIRMADO: https://en.wikipedia.org/wiki/ISO_15765-2]` (PCI/NOD) e `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/protocols/protocol_can.py]` (comentário explícito `06 41 00 BE 7F B8 13` e extração de `data_len`).

#### 1.4.3 Cabeçalho de 3 bytes e o `7E8`

Em protocolos legados (J1850/ISO9141/KWP), a mensagem tem **3 bytes de cabeçalho**:

| Byte | Nome | Significado |
|---|---|---|
| 1 | priority/type | prioridade e tipo de mensagem |
| 2 | receiver/target | endereço de destino |
| 3 | transmitter/source | endereço de quem responde |

`[CONFIRMADO: ELM327DS.pdf — AT SH: "xx will be used for the first or priority/type byte, yy … receiver/target byte, zz … transmitter/source byte"]`.

Em CAN 11-bit, o "cabeçalho" é o **CAN ID** de 11 bits, impresso pelo ELM com `ATH1` como 3 dígitos hex:

| ID | Quem é | Uso |
|---|---|---|
| `7DF` | requisição **funcional** (broadcast) | todos os ECUs ouvem (é o que o ELM envia por padrão) |
| `7E0`–`7E7` | requisição **física** a um ECU | diagnóstico dirigido |
| `7E8`–`7EF` | resposta dos ECUs (7E8 = motor, tipicamente) | `7E8` é o que interessa no painel |
| `7E9` | transmissão / segundo ECU | respostas duplicadas — cuidado |

`[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/protocols/protocol_can.py]` — o parser do python-OBD documenta exatamente `00 00 07 E8 06 41 00 BE 7F B8 13`, com `addr_mode 0xD0 = functional` e `0xE0 = physical`, e o `elm327obd2` confirma "on CAN cars, 7E8 is usually the engine computer" `[CONFIRMADO: https://elm327obd2.com/elm327-commands/]`.

Formato cru (ATH1, CAF1) de uma resposta:
```
7E8 06 41 00 BE 1F A8 13
```
Sem `ATH1` (ATH0):
```
41 00 BE 1F A8 13
```

**Regra para o app:** com `ATH0` não é possível distinguir `7E8` de `7E9`. Para o painel isso é aceitável (o ELM imprime as respostas de todos os ECUs, uma por linha); para diagnóstico (DTC/VIN) usar `ATH1` e filtrar `7E8`, ou usar `ATCRA 7E8` durante essa fase. `[CONFIRMADO: ELM327DS.pdf — "CRA hhh … if you only want to see information from 7E8, simply send AT CRA 7E8"]`.

#### 1.4.4 Multi-frame (ISO-TP) — First Frame, Consecutive Frame, Flow Control

Mensagens com mais de 7 bytes (VIN, Calibration ID, lista de DTCs) são segmentadas pelo ISO 15765-2 `[CONFIRMADO: https://en.wikipedia.org/wiki/ISO_15765-2]`:

| Tipo | PCI (nibble alto) | Conteúdo |
|---|---|---|
| Single Frame (SF) | `0` | nibble baixo = NOD (1–7), depois dados |
| First Frame (FF) | `1` | 12 bits de comprimento total + 6 primeiros bytes |
| Consecutive Frame (CF) | `2` | nibble baixo = índice sequencial (1..F, 0, 1..) + 7 bytes |
| Flow Control (FC) | `3` | status (0=CTS, 1=WAIT, 2=OVERFLOW) + Block Size + STmin |

Exemplo de VIN (`09 02`, 20 bytes de payload: `49 02 01` + 17 ASCII), com `ATH1`/`ATCAF0`:

```
7E8 10 14 49 02 01 39 42 52      ← FF: len=0x014 (20), 6 bytes de dados
7E8 30 00 00 00 00 00 00 00      ← FC enviado pelo tester/ELM (CTS, BS=0, STmin=0)
7E8 21 42 41 33 48 45 30 4D      ← CF #1
7E8 22 30 31 32 33 34 35 36      ← CF #2
```
Payload reconstruído: `49 02 01` + `39 42 52 42 41 33 48 45 30 4D 30 31 32 33 34 35 36` → VIN `9BRBA3HE0M0123456`. (Exemplo **ilustrativo** de bytes; o formato dos frames é o confirmado.)

`[CONFIRMADO: https://en.wikipedia.org/wiki/ISO_15765-2]` + `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/protocols/protocol_can.py]` (o código extrai `data_len` do FF, ordena os CF por `seq_index`, valida contiguidade e corta no tamanho).

**Quem manda o Flow Control?** Com `ATCFC1` (default), **o próprio ELM327** responde ao FF com `30 00 00` e entrega a mensagem já montada ao app. O datasheet diz: "Flow Control messages are automatically generated by the ELM327 in response to a First Frame reply, as long as the CFC setting is on" `[CONFIRMADO: ELM327DS.pdf — CAF0/CAF1]`. Portanto:
- App normal: **nunca** envie FC; mantenha `ATCFC1`.
- Se algum dia usar `ATCFC0` (ex.: sniffing), o app vira responsável pelo FC e pelo timing — **fora do escopo da v1**.

Com `ATCAF1`, o ELM imprime multi-frame no formato "linha de tamanho + linhas numeradas", algo como:

```
014
0: 49 02 01 39 42 52 42 41 33
1: 48 45 30 4D 30 31 32 33 34
2: 35 36
```
`[HIPÓTESE]` — o formato exato (`014` e `N:`) é o comportamento documentado do auto-formatting, mas **deve ser validado no primeiro teste com o carro** (ver §9). O parser deve aceitar tanto esse formato quanto o cru, e nunca depender da quebra de linha.

### 1.5 O que fazer com "NO DATA", "SEARCHING...", "?", "STOPPED", "BUS INIT: ..."

Essas são respostas **do ELM327**, não do carro. O app precisa classificá-las antes de tentar decodificar `[CONFIRMADO: ELM327DS.pdf — "Error Messages and Alerts"; strings também confirmadas em https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/command/RegexPatterns.kt]`.

| Resposta | Significado | Ação do app |
|---|---|---|
| `SEARCHING...` | ELM está varrendo protocolos (só em `ATSP0`, na primeira requisição) | **Não é erro.** Aumentar timeout para 10–15 s nessa fase; nunca enviar outro comando durante a busca. |
| `NO DATA` | O carro não respondeu àquele PID/serviço no tempo do `AT ST` | Marcar PID como `SemDado` (não como "não suportado"). Repetir 1× com backoff; após 3 falhas seguidas do **mesmo** PID, marcar `IndisponívelTemporário` por 30 s. |
| `UNABLE TO CONNECT` | Nenhum protocolo respondeu (ignição off, dongle solto, fusível) | Reconectar; checar `AT RV`; orientar usuário "ligue a ignição". |
| `BUS INIT: ...` / `BUS INIT: ERROR` | Inicialização ISO 9141/KWP em andamento ou falhou | Só ocorre em protocolos 3/4/5. Timeout maior; se `...ERROR`, tentar `ATSP0` e reportar. |
| `?` | Comando AT desconhecido ou sintaxe inválida (inclusive comando OBD com caractere não-hex) | Erro de **software** (ou clone sem o comando). Logar com o comando enviado; não repetir. |
| `STOPPED` | O comando em curso foi interrompido por um byte recebido no RS232 (ou RTS) | Sintoma de **concorrência**: dois comandos enviados juntos. Corrigir o mutex. Descartar a resposta. |
| `CAN ERROR` | Erro de barramento CAN | Verificar fiação/adaptação; em geral indica dongle ruim. Reconectar. |
| `BUFFER FULL` | Buffer interno do ELM encheu (respostas muito longas/rápidas) | Reduzir ritmo (`AT ST`, pausa entre comandos). |
| `7F 01 12` | **Resposta do carro** (negativa): serviço 01, NRC 0x12 = subfunção/PID não suportado | Marcar PID como `NãoSuportado`. Ver §4.4. |

Referências: `[CONFIRMADO: https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/command/RegexPatterns.kt]` (padrões `BUS INIT`, `SEARCHING`, `NO DATA`, `STOPPED`, `?`, `UNABLE TO CONNECT`, `ERROR`, `7F 0[0-A] 1[1-2]`) e `[CONFIRMADO: ELM327DS.pdf — seção "Error Messages and Alerts"/"Communicating with the ELM327"]`.

**Regra de ouro do parser:** a resposta termina no caractere `>` (prompt). Todo o resto (`\r`, `\n`, eco, `SEARCHING...`) é ruído a ser limpo. O python-OBD faz exatamente isso: lê até `>` e descarta `\x00`, `\r`, `\n` e linhas vazias `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/elm327.py]`.

---

## 2. ELM327 — comandos AT

### 2.1 Sequência de inicialização robusta

O ELM327 é um interpretador "OBD → RS232". Todo comando é texto ASCII terminado em **CR (`\r`, 0x0D)**; a resposta termina com o prompt `>` (0x3E) `[CONFIRMADO: ELM327DS.pdf — "Communicating with the ELM327"]`. Comandos AT começam com `AT`; comandos OBD são hex puro. Espaços são ignorados e não há diferença entre maiúsculas/minúsculas.

#### Sequência canônica (a que deve ser implementada)

| # | Comando | Efeito | Obrigatório? | Observações |
|---|---|---|---|---|
| 0 | `" "` (espaço) | acorda o ELM do low power | condicional | só se o link foi aberto e o ELM estava dormindo; resposta é lixo, ignore |
| 1 | `ATZ` | reset completo (~800 ms–1 s) | **sim** | descarta qualquer estado herdado; ler até `>`; pode retornar lixo/eco |
| 2 | `ATE0` | echo off | **sim** | sem isso o comando enviado volta como primeira linha e quebra parsers ingênuos |
| 3 | `ATL0` | linefeed off | **sim** | remove `\n`; reduz bytes e simplifica split por `\r` |
| 4 | `ATS0` | spaces off | **sim** | resposta mais curta e transferência mais rápida (datasheet) |
| 5 | `ATH0` | headers off | **sim** (ciclo normal) | ver §1.4.3; usar `ATH1` na fase de descoberta/diagnóstico |
| 6 | `ATAT1` | adaptive timing auto1 | **sim** | default e recomendado pelo datasheet |
| 7 | `ATST 32` | timeout = 0x32 × 4 ms ≈ 200 ms | **sim** | valor default; não abaixar antes de conhecer o carro |
| 8 | `ATSP0` | protocolo automático | **sim** (1ª vez) | não faz busca sozinho: a busca começa no primeiro comando OBD |
| 9 | `0100` | dispara a busca e lê PIDs suportados | **sim** | pode responder `SEARCHING...` por até ~15 s |
| 10 | `ATDPN` | número do protocolo detectado (`A6`, `A8`, …) | **sim** | persistir no `VehicleProfile` |
| 11 | `ATSP6` | fixa ISO 15765-4 CAN 11/500 | recomendado | usar o valor descoberto, não hardcode cego |
| 12 | `0100` | valida protocolo fixado | **sim** | se `UNABLE TO CONNECT`, voltar para `ATSP0` e registrar |
| 13 | `ATCAF1` | CAN auto-formatting on | **sim** | remove PCI e monta multi-frame |
| 14 | `ATCFC1` | CAN flow control on | **sim** | ELM responde ao First Frame sozinho |
| 15 | `AT RV` | tensão no pino 16 | **sim** | sanidade: < 11 V ⇒ ignição fraca/desligada |
| 16 | `0902`, `0904`, `0906`, `090A`, `0101`, `03`, `07`, `0A` | diagnóstico | 1× por sessão | com `ATH1` para identificar ECU; cachear VIN/CALID |

Fundamento de cada item:

- `ATZ` reseta tudo (inclusive protocolo salvo, headers, echo); tempo de reset é da ordem de centenas de ms e o python-OBD espera **1 s** após enviar `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/elm327.py]`.
- `ATE0`, `ATL0`, `ATH1`, `ATSP0`/`ATTP`, `ATDPN`, `AT RV` são exatamente a sequência usada pelo python-OBD `[CONFIRMADO: mesmo arquivo]`.
- `ATS0`: o datasheet explica que o ELM insere espaços a cada byte por legibilidade e que removê-los "can provide significant improvements for computer processing" `[CONFIRMADO: ELM327DS.pdf — "S0 and S1"]`.
- `ATAT1` é o default; `AT0` desliga e `AT2` é mais agressivo `[CONFIRMADO: ELM327DS.pdf — "AT0, AT1 and AT2"]`.
- `ATST hh` é em incrementos de **4 ms**; `32` ⇒ ~200 ms; `00` restaura o default `[CONFIRMADO: ELM327DS.pdf — "ST hh"; tabela oficial: "Set Timeout to hh x 4 msec" em https://cdn.sparkfun.com/assets/4/e/5/0/2/ELM327_AT_Commands.pdf]`.
- `ATSP h` salva o protocolo na EEPROM; `ATSP 0` **não** grava (evita 30 ms de escrita); `ATSP Ah` mantém fallback automático `[CONFIRMADO: ELM327DS.pdf — "SP h"]`.
- `ATCAF0/CAF1` e `ATCFC0/CFC1` `[CONFIRMADO: ELM327DS.pdf — "CAF0 and CAF1"]`.

#### Sequência "mínima de clone" (fallback quando o ATZ não coopera)

Alguns clones não respondem ao primeiro `ATZ` após o pareamento. O README do obd-java-api documenta a receita de recuperação: `ATD`, `ATZ`, `AT E0`, `AT L0`, `AT S0`, `AT H0`, `AT SP 0` `[CONFIRMADO: https://raw.githubusercontent.com/pires/obd-java-api/master/README.md]`. Implementar como: tentar sequência canônica → se falhar 2×, enviar `ATD`/`ATWS` e repetir.

#### Esqueleto em Kotlin (o `ElmSession` completo está no §6)

```kotlin
/** Primeira linha de uma resposta bem-sucedida (ou "" se falhou). */
fun ElmResult.singleLine(): String =
    (this as? ElmResult.Lines)?.lines?.firstOrNull()?.trim().orEmpty()

class ElmBootstrapper(private val session: ElmSession) {

    suspend fun boot(profile: VehicleProfile?): BootResult {
        session.execute("ATZ", timeout = 1_500.ms)            // reset
        session.requireOk("ATE0")                              // echo off
        session.requireOk("ATL0")                              // linefeeds off
        session.requireOk("ATS0")                              // spaces off
        session.requireOk("ATH0")                              // headers off
        session.requireOk("ATAT1")                             // adaptive timing
        session.requireOk("ATST 32")                           // 200 ms

        var protocol: String? = profile?.protocolNumber

        if (protocol == null) {
            // Descoberta: só na primeira conexão com este carro.
            session.requireOk("ATSP0")
            val r0100 = session.execute("0100", timeout = 15_000.ms)   // SEARCHING...
            if (r0100 is ElmResult.Failure && r0100.error == ElmError.UNABLE_TO_CONNECT) {
                return BootResult.Failure(BootFailure.NoVehicle)
            }
            val dpn = session.execute("ATDPN").singleLine()
            protocol = dpn.trimStart('A')                       // "A6" -> "6"
            session.requireOk("ATSP$protocol")
            session.execute("0100", timeout = 3_000.ms)         // valida
        } else {
            session.requireOk("ATSP$protocol")
        }

        session.requireOk("ATCAF1")
        session.requireOk("ATCFC1")
        val volts = session.execute("AT RV").singleLine()
            .removeSuffix("V").toDoubleOrNull()

        val detected = protocol ?: return BootResult.Failure(BootFailure.UnknownProtocol)
        return BootResult.Success(detected, volts)
    }
}
```

### 2.2 Comandos de descoberta

| Comando | Resposta típica | Para que serve | Notas |
|---|---|---|---|
| `ATI` | `ELM327 v1.5` / `v2.1` | versão do firmware | **primeiro teste de clone** (§2.4.1) |
| `AT@1` | `OBDII to RS232 Interpreter` | descrição do dispositivo | clones costumam devolver string vazia/genérica |
| `AT@2` | identificador gravado | serial do módulo | `@3` grava (12 chars) `[CONFIRMADO: AT Command Table, sparkfun]` |
| `ATDP` | `AUTO, ISO 15765-4 (CAN 11/500)` | protocolo por extenso | prefixo `AUTO` quando busca automática está ligada |
| `ATDPN` | `A6` ou `6` | protocolo por número | `A` = auto; `6` = CAN 11/500 `[CONFIRMADO: ELM327DS.pdf — "DPN"]` |
| `AT RV` | `12.6V` | tensão no pino 16 | ~2 % de erro não calibrado |
| `ATIGN` | `ON` / `OFF` | nível do pino 15 (IgnMon) | só útil se o dongle liga esse pino; muitos não ligam |
| `ATCS` | status CAN | erros de barramento | v2.2+ mostra frequência medida |
| `ATPPS` | sumário de PP | debug de configuração | raramente necessário |
| `ATBD` | dump do OBD buffer (12 bytes) | ver o último frame cru | ótimo para diagnosticar clone que "come" bytes |

`[CONFIRMADO: ELM327DS.pdf — "DP", "DPN", "RV", "IGN", "BD", "CS"]` e `[CONFIRMADO: https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/command/at/Info.kt]` (classes `DescribeProtocolCommand`, `DescribeProtocolNumberCommand`, `IgnitionMonitorCommand`, `AdapterVoltageCommand`).

### 2.3 Timeouts e ajustes para leitura em tempo real

Há **três timeouts distintos** e o app precisa dos três:

| Timeout | Onde vive | Valor sugerido | Quando muda |
|---|---|---|---|
| Timeout do ELM (`AT ST`) | firmware do dongle | `0x32` (200 ms) na descoberta; `0x19`–`0x1E` (100–120 ms) em regime | após 1º ciclo bem-sucedido; voltar a 200 ms se aparecer `NO DATA` |
| Timeout de busca de protocolo | app | 10–15 s | só na primeira requisição com `ATSP0` |
| Timeout de comando no app | `ElmSession` | `ST_protocolo + 150 ms` (ex.: 350 ms) e, para multi-frame, +300 ms | nunca menor que 2× o RTT medido |

Regras práticas:

1. **Adaptive timing ligado (`ATAT1`)** — o ELM aprende o tempo de resposta do carro e usa o `AT ST` como teto `[CONFIRMADO: ELM327DS.pdf — "AT0/AT1/AT2"]`. Só desligue (`ATAT0`) quando estiver **medindo latência** de forma científica.
2. `AT ST` no CAN é multiplicado por 1; `ATCTM5` multiplica por 5 (útil em CAN lento/ruidoso, mas o datasheet recomenda desligar adaptive timing junto) `[CONFIRMADO: ELM327DS.pdf — "CTM5"]`.
3. **Nunca** reduzir `AT ST` abaixo de ~80 ms em CAN de carro frio: ECUs podem levar dezenas de ms para responder ao primeiro pedido após o boot.
4. `SEARCHING...` pode aparecer **por vários segundos** e não é erro; o timeout do app nessa fase tem de ser bem maior que o `AT ST`.
5. O ELM só processa um comando por vez; o timeout do app deve cobrir **envio + processamento + resposta**, não só o tempo de barramento.
6. Em Bluetooth, some ao orçamento o RTT do RFCOMM (tipicamente 10–30 ms); em Wi-Fi, 5–15 ms `[HIPÓTESE — medir com o adaptador real]`.

### 2.4 Armadilhas conhecidas

#### 2.4.1 Clones "ELM327 v1.5" — o firmware que nunca existiu

As versões realmente lançadas pela Elm Electronics são **1.0, 1.0a, 1.1, 1.2, 1.2a, 1.3, 1.3a, 1.4, 1.4b, 2.0, 2.1, 2.2 e 2.3**. **Não existem v1.4a e v1.5** `[CONFIRMADO: https://en.wikipedia.org/wiki/ELM327]`. Portanto:

- `ATI` respondendo `ELM327 v1.5` ⇒ **clone** (código v1.4 com string falsificada). O datasheet da Elm nem lista 1.5 `[CONFIRMADO: https://en.wikipedia.org/wiki/ELM327]`.
- Clones herdam **bugs da v1.4**: tratamento de multi-frame, resposta a `ATSP0`, headers CAN e timing. Alguns reportam versões futuras inexistentes (v2.3 falsa).
- Evolução real que importa para o app `[CONFIRMADO: https://en.wikipedia.org/wiki/ELM327]`:
  - **1.2**: adaptive timing, baud 500k, J1939, protocolos B/C.
  - **1.3**: mensagem `STOPPED`, LV RESET.
  - **1.4**: low power (`ATLP`), extended addressing, 4800 baud ISO.
  - **2.0**: **buffer Tx RS232 para 512 bytes**, wake retém configurações.
  - **2.1**: processa **Response Pending `7F xx 78`**, busca CAN mede frequência.
  - **2.2**: `AT CS` mostra frequência CAN.
  - **2.3**: `AT FT`, novos modos de flow control.
- **Consequência de projeto:** não confie em `ATI`. Faça *probe* de capacidades no boot e guarde um **`AdapterCapabilities`** (bitmask): suporta `AT@1`? `ATCAF0`? `ATCFC0`? `ATCTM5`? `ATAL`? `ATSH`? `ATCS`? `7F xx 78`? O probe custa ~10 comandos e roda 1× por adaptador.

#### 2.4.2 CH340/CP2102 (USB) vs Bluetooth

| Chip | Tipo | Android | Observação |
|---|---|---|---|
| CH340/CH341 | USB-serial vendor-specific | precisa de driver na lib (`usb-serial-for-android`) | barato, muito comum em cabos "ELM327 USB v1.5" |
| CP2102 | USB-serial vendor-specific | idem | comum em cabos de marca |
| FTDI FT232 | USB-serial vendor | idem | mais caro/estável |
| PL2303 | USB-serial vendor | idem | clones problemáticos no Android |
| HC-05/BC417 | Bluetooth Classic SPP | nativo (`BluetoothSocket`) | pareamento com PIN 1234/0000 |
| HM-10/CC2541 | BLE GATT (FFE0/FFE1) | nativo (GATT) | throughput menor |

O ELM327 em si é o mesmo; o que muda é o **transport**. Detalhes em §3.

#### 2.4.3 Resposta truncada em 32 bytes

**Status: `[NÃO CONFIRMADO]`.** Não encontrei fonte primária para "firmwares antigos truncam em 32 bytes". O que **está** confirmado:
- O buffer OBD interno do ELM327 guarda **12 bytes** por mensagem (usado por `AT BD`) `[CONFIRMADO: ELM327DS.pdf — "BD [perform an OBD Buffer Dump]"]`.
- O buffer de transmissão RS232 foi **aumentado para 512 bytes na v2.0** `[CONFIRMADO: https://en.wikipedia.org/wiki/ELM327]` — logo, versões anteriores tinham buffer menor (valor exato não publicado nesta fonte).
- `ATAL` permite receber mensagens longas "unlimited in number" e enviar 8 bytes `[CONFIRMADO: ELM327DS.pdf — "AL [Allow Long messages]"]`.

**Decisão:** não projetar para "32 bytes". Projetar para: (a) resposta pode chegar **fragmentada em N leituras**; (b) resposta pode vir **truncada** em clones — detectar comparando com o tamanho esperado (NOD/comprimento do FF) e, se faltar byte, marcar `TRUNCATED` e repetir 1×; (c) nunca enviar/receber payload gigante no ciclo de tempo real (só VIN/CALID, 1×).

#### 2.4.4 Necessidade de `ATAL`

- Com **`ATCAF1`** (default e recomendado), o ELM monta multi-frame e você **não precisa** de `ATAL` para VIN/CALID.
- `ATAL` passa a ser necessário quando se usa **`ATCAF0`** (frames crus) ou envio/recebimento de mensagens > 7 bytes fora do formato OBD. O datasheet: "The standard OBDII protocols restrict the number of data bytes in a message to seven, which the ELM327 normally does as well (for both send and receive). If AL is selected, the ELM327 will allow long sends (eight data bytes) and long receives (unlimited in number). The default is AL off (and NL selected)." `[CONFIRMADO: ELM327DS.pdf — "AL"]`.
- Em KWP (protocolos 4/5), desde a v1.2 o ELM **não** exige `ATAL` para receber mensagens longas `[CONFIRMADO: ELM327DS.pdf — "NL"]`.

#### 2.4.5 Problemas de eco

- Sintoma: a primeira linha da resposta é o próprio comando (`010C`), e o parser tenta decodificar `01 0C` como se fosse `41 0C`.
- Causa: `ATE0` não aplicado, ou clone que reativa echo após `ATZ`/`ATWS`.
- Defesa em camadas: (1) enviar `ATE0` e exigir `OK`; (2) no parser, **descartar a primeira linha se ela for igual ao comando enviado** (normalizando espaços/caixa); (3) teste de fumaça: enviar `ATI` e verificar que a resposta tem 1 linha só.

#### 2.4.6 `ATSP0` (auto-detect) e por que fixar o protocolo

Comportamento confirmado:
- `ATSP0` **não busca nada sozinho**: a busca inicia no primeiro comando OBD e o ELM imprime `SEARCHING...` `[CONFIRMADO: ELM327DS.pdf — "SP h"]`.
- Quando acha, **salva na EEPROM** (se `M1`) e passa a usar como default; se falhar depois, volta a buscar `[CONFIRMADO: idem]`.
- "Note that some vehicles respond to more than one protocol" — ou seja, a busca pode encontrar respostas em mais de um barramento e o `ATDPN` pode reportar um protocolo que não é o "principal" `[CONFIRMADO: ELM327DS.pdf — "SP h"]`.
- O python-OBD só confia no `ATSP0` até certo ponto: se `ATDPN` vier `0`/desconhecido, ele **itera manualmente** pelos protocolos em ordem de probabilidade (`6, 8, 1, 7, 9, 2, 3, 4, 5, A`) `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/elm327.py]`.

**Regra do app:** auto na primeira conexão; ao obter `ATDPN = A6`, gravar `protocolNumber = "6"` no `VehicleProfile` e usar `ATSP6` nas próximas. Se, com protocolo fixo, vier `UNABLE TO CONNECT` ou 3× `NO DATA`, cair para `ATSP0` uma vez e re-descobrir.

#### 2.4.7 Concorrência: qualquer byte aborta o comando

"While processing OBD commands, the ELM327 will continually monitor for either an active RTS input, or an RS232 character received. Either one will interrupt the IC … possibly aborting any initiation" `[CONFIRMADO: ELM327DS.pdf — "Communicating with the ELM327"]`. E ao interromper, o ELM imprime `STOPPED` `[CONFIRMADO: ELM327DS.pdf — "MA"]`.

Consequências:
- **Mutex obrigatório** por sessão (§6.2).
- Nunca enviar "heartbeat" enquanto um PID está em voo.
- Ao cancelar uma coroutine, **não** confie que o ELM parou: envie `\r` (comando vazio repete o último!) com cuidado — o datasheet diz que um CR isolado **repete o último comando**; para limpar, o correto é ler até o prompt e só então enviar o próximo. Em caso de dúvida, `ATZ`/`ATWS` como recuperação.

#### 2.4.8 Outras armadilhas que custam horas

| Armadilha | Sintoma | Defesa |
|---|---|---|
| `ATLP` (low power) | primeiro comando após o carro desligar volta lixo | enviar `" "` e aguardar ~1 s antes de qualquer comando |
| Queda de tensão no crank | `LV RESET` / reconexão | detectar queda de `AT RV`, aguardar 2 s, reconectar |
| `ATMA` usado para "ficar ouvindo" | comando nunca termina; app trava | nunca usar monitoração no ciclo; se usar, enviar 1 char para parar |
| `M1` (memory) salvando protocolo | carro A "contamina" carro B | usar `ATM0`? Não: `ATSP` grava independentemente de M; para não persistir, aceitar o custo e reconfigurar no boot |
| Clone que responde `AUTO` no `ATDP` mas `0` no `ATDPN` | parser acha que detectou | tratar `0` como "não detectado" e cair no fallback manual |
| `ATSH` errado | `NO DATA` em tudo | resetar com `ATD`/`ATZ`; nunca mexer em `ATSH` no fluxo normal |
| Resposta multi-ECU (7E8 + 7E9) | duas linhas para o mesmo PID; parser pega a errada | com `ATH0`, escolher a **primeira** linha válida; em diagnóstico, filtrar por `7E8` |
| `7F xx 78` (response pending) | clone v1.4 não entende e devolve lixo | tratar `7F .. 78` como "aguarde" e reler; marcar capability se o clone falhar |
| Baud do USB | `ATZ` devolve caracteres estranhos | 38400 (padrão) ou 9600; em BT SPP o baud é virtual e irrelevante |

---

## 3. Conectividade

A camada de protocolo (§1, §2) é **idêntica** nos quatro meios; o que muda é a implementação de `Transport`. Requisito: o `ElmSession` só conhece `InputStream`/`OutputStream` + um `close()`.

### 3.1 Bluetooth Classic (SPP / RFCOMM)

**É o transporte nativo do ELM327 "de painel".** O módulo Bluetooth do dongle (HC-05/BC417 e similares) expõe o perfil **Serial Port Profile**, cujo UUID padrão é:

```
00001101-0000-1000-8000-00805F9B34FB
```

`[CONFIRMADO: https://en.wikipedia.org/wiki/List_of_Bluetooth_profiles — SPP; UUID padrão do perfil]`.

#### Pareamento

1. O usuário pareia em **Configurações → Bluetooth** (PIN típico `1234` ou `0000`). Pareamento por app (`createBond()`) funciona, mas exige o MAC e UX de PIN; para uso pessoal, parear no sistema é mais simples e estável.
2. O app lista `adapter.bondedDevices` e filtra por nome (`OBDII`, `V-LINK`, `ELM327`, `OBD2`) ou MAC salvo.
3. **Nunca** chamar `startDiscovery()` com o socket aberto: descoberta derruba a conexão. Se precisar descobrir, `cancelDiscovery()` antes de conectar.
4. Permissões: `BLUETOOTH_CONNECT` (Android 12+/API 31) além de `BLUETOOTH`/`BLUETOOTH_ADMIN` (legado). Para *scan*, `BLUETOOTH_SCAN` (e localização em versões antigas); para *bonded SPP*, `BLUETOOTH_CONNECT` basta.

#### Conexão RFCOMM

```kotlin
class SppTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
) : Transport {

    private var socket: BluetoothSocket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        adapter.cancelDiscovery()
        val s = try {
            device.createRfcommSocketToServiceRecord(sppUuid)
        } catch (e: IOException) {
            // Fallback clássico para clones com SDP quebrado: canal RFCOMM 1
            device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
        }
        s.connect()                    // bloqueante: 2–10 s
        socket = s
    }

    override fun input(): InputStream = socket!!.inputStream
    override fun output(): OutputStream = socket!!.outputStream

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }
}
```

Notas de implementação:

- `BluetoothSocket` **não** suporta `setSoTimeout`. O timeout tem de ser feito no leitor: ler com `available()` em laço + deadline (é o que o kotlin-obd-api faz, com polling de 10 ms e `interByteTimeout`) `[CONFIRMADO: https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/connection/ObdDeviceConnection.kt]`.
- O fallback por reflexão (`createRfcommSocket(1)`) é prática consolidada para clones cujo SDP não anuncia o serviço; **não é documentado pelo Android** `[HIPÓTESE]`, mas está em praticamente todo app OBD de código aberto.
- Com o socket aberto, o dongle mantém o link; se o carro desligar, o ELM pode dormir — o primeiro comando volta lixo/`NO DATA`.

### 3.2 BLE (GATT)

Módulos BLE (HM-10/CC2541, Vgate iCar Pro BLE, LELink) expõem o ELM327 como um "UART transparente" sobre GATT. O perfil mais comum:

| Item | UUID | Uso |
|---|---|---|
| Serviço | `0000FFE0-0000-1000-8000-00805F9B34FB` | serviço UART do módulo |
| Característica | `0000FFE1-0000-1000-8000-00805F9B34FB` | escrita (comandos) + notificação (respostas) |
| CCCD | `00002902-0000-1000-8000-00805F9B34FB` | habilita notify/indicate |

`[CONFIRMADO: https://www.tinkered.ai/components/hm-10]` (HM-10 = serviço FFE0 / característica FFE1) e `[CONFIRMADO: https://electronics.stackexchange.com/questions/323071/send-custom-gatt-characteristics-using-hm-10-actually-a-cc2541]` (UUIDs longos `0000ffe0-…` / `0000ffe1-…`). O manual do HM-10 também cita "UUID FFE0, FFE1" `[CONFIRMADO: https://manuals.plus/m/20769fb4e4aa5f2aeb37ccb86a04a4207e6de47e12bdb3239a614015b9bd010c]`.

Pontos críticos:

1. **Descoberta obrigatória.** Nem todo dongle BLE usa FFE0/FFE1 (alguns usam FFF0/FFF1, outros serviços próprios). O app deve: escanear filtrando por `FFE0` **e** por nome; ao conectar, `discoverServices()` e escolher a primeira característica com `PROPERTY_WRITE` ou `PROPERTY_WRITE_NO_RESPONSE` **e** `PROPERTY_NOTIFY`. Cachear o UUID por MAC no `VehicleProfile`.
2. **MTU.** O ATT default é **23 bytes** → **20 bytes de payload** por escrita/notificação. Peça `requestMtu(517)` (máximo aceito pelo Android) e use `mtu - 3` após `onMtuChanged`; se o módulo recusar, ficar em 20 `[CONFIRMADO: https://developer.android.com/reference/android/bluetooth/BluetoothGatt — requestMtu/getMtu]` `[HIPÓTESE: máximo 517 e payload mtu-3]`.
3. **Escrita.** Prefira `WRITE_TYPE_DEFAULT` (com resposta) para comandos; `NO_RESPONSE` só para streams. Fragmentar o comando em pedaços ≤ `mtu-3`.
4. **Reassemblagem.** A resposta do ELM chega em **várias notificações** (o `>` pode cair no meio de um pacote). O leitor acumula bytes até encontrar `>` — nunca assume "1 notificação = 1 resposta".
5. **Throughput.** BLE tem connection interval (7,5–50 ms) e cada operação write/notify custa um ou mais eventos de conexão. Resultado prático: **5–12 comandos/s** `[HIPÓTESE]` — menos que SPP. Para painel a 4 Hz com 4 PIDs (16 cmd/s), BLE costuma ficar no limite.
6. **iOS vs Android.** No Android, BLE é totalmente utilizável; no iOS, SPP não existe (por isso LELink é BLE). Como o alvo é Android, SPP é preferível.

### 3.3 Wi-Fi (TCP)

Dongles Wi-Fi criam um **AP próprio** (SSID `WiFi_OBDII`, `V-LINK`, `OBDII`, `WIFI_OBDII`) e expõem o ELM327 em um socket TCP.

| Parâmetro | Valor dominante | Observações |
|---|---|---|
| IP do dongle | `192.168.0.10` | alguns usam `192.168.0.11`, `192.168.1.5`, `192.168.4.1` |
| Porta | **35000** | documentada por vários fabricantes/manuais |
| Portas alternativas | 2000, 23, 8080 | aparecem em clones; **testar em sequência** `[HIPÓTESE]` |
| Protocolo | TCP puro, mesmo texto AT/OBD do RS232 | sem framing extra; terminador `\r` e prompt `>` |

`[CONFIRMADO: https://www.carscanner.info/wifi/]` ("na maioria dos casos 192.168.0.10 e porta 35000") e `[CONFIRMADO: https://interfusellc.com/elm327/manual/wifi-windows/]` ("IP 192.168.0.10, Port 35000"). As portas 2000/23/8080 são relatos de comunidade, não de fonte primária — implementar como **lista de tentativa**, não como fato.

Implementação:

```kotlin
class TcpTransport(
    private val host: String = "192.168.0.10",
    private val ports: List<Int> = listOf(35_000, 2_000, 23, 8_080),
    private val connectTimeoutMs: Int = 4_000,
) : Transport {
    private var socket: Socket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        var last: IOException? = null
        for (p in ports) {
            try {
                val s = Socket()
                s.tcpNoDelay = true          // obrigatório: sem Nagle para RTT baixo
                s.soTimeout = 500            // leitura com timeout
                s.connect(InetSocketAddress(host, p), connectTimeoutMs)
                s.keepAlive = true
                socket = s
                return@withContext
            } catch (e: IOException) { last = e }
        }
        throw last ?: IOException("nenhuma porta respondeu")
    }
    override fun input() = socket!!.getInputStream()
    override fun output() = socket!!.getOutputStream()
    override fun close() { runCatching { socket?.close() }; socket = null }
}
```

Cuidados:

- **Perda de internet**: ao entrar no AP do dongle, o Android passa a rotear pelo Wi-Fi sem internet. Para manter o app funcionando (mapas, telemetria remota), ou se usa `ConnectivityManager.requestNetwork()`/`bindProcessToNetwork` com `TRANSPORT_CELLULAR`, ou se aceita a perda. O dongle não faz NAT.
- **Troca de rede a cada uso**: conectar/desconectar do AP manualmente é UX ruim; `WifiNetworkSpecifier` automatiza, mas em versões recentes do Android pode derrubar a rede ao fechar o app.
- **Bateria**: rádio Wi-Fi em modo AP + tela ligada consome bem mais que BT `[HIPÓTESE]`.
- **Latência**: TCP em si adiciona pouco (5–15 ms), mas o gargalo continua sendo o ELM (10–20 cmd/s).

### 3.4 USB serial (CDC-ACM / FTDI / CH340)

Viabilidade **técnica**: alta. Viabilidade **prática** no seu caso: **baixa**, por causa do Android Auto.

Como funciona:

- O chip USB-serial (CH340, CP2102, FT232, PL2303) aparece como `UsbDevice`; o app abre com `UsbManager.openDevice()` e faz `bulkTransfer()` nas endpoints IN/OUT.
- O Android **não** tem driver de serial embutido; usa-se a biblioteca `usb-serial-for-android` (implementa CDC-ACM, FTDI, CP210x, CH340, PL2303).
- CDC-ACM: interface class `0x02`, subclass `0x02`, protocol `0x01` (modem/ACM) `[CONFIRMADO: USB CDC 1.1 spec / classe CDC]`.

Conflito com Android Auto:

- O Android Auto **com fio** usa a mesma porta USB-C do celular como link com a central (o celular atua como *device* USB/AOA). Um adaptador serial OBD precisa que o celular atue como **host** USB. A mesma porta não pode ser host e device ao mesmo tempo; hubs USB-C que fazem "power + data" não resolvem o papel duplo de forma confiável `[HIPÓTESE]`.
- Na prática: **AA com fio ativo ⇒ USB serial indisponível**. Com **AA sem fio**, a porta fica livre e o USB serial funciona.
- Além disso, o cabo do OBD (embaixo do painel) até o celular é inconveniente e ocupa a única porta (sem carregamento simultâneo, salvo dock).

Conclusão: implementar USB como transport **opcional** (mesma interface), mas **não** como caminho principal.

### 3.5 Comparativo e escolha

Números realistas para **ELM327 típico** (o gargalo é o firmware do dongle, não o rádio). Todos marcados `[HIPÓTESE]` até medição com o adaptador real; a ordem de grandeza é consistente com o limite de 10–20 comandos/s citado na literatura de OBD e com o comportamento dos transports.

| Meio | Throughput útil | Comandos/s (01xx) | RTT típico | Estabilidade | Consumo de bateria | Complexidade | Internet do celular | Android Auto |
|---|---|---|---|---|---|---|---|---|
| **BT Classic SPP** | 1–3 kB/s | **10–20** | 15–40 ms | **Alta** | **Baixa** | **Baixa** | preservada | convive (BT) |
| BLE (GATT) | 0,5–2 kB/s | 5–12 | 30–80 ms | Média | Muito baixa | Média | preservada | convive (BT) |
| Wi-Fi (TCP) | 5–50 kB/s (rádio) | 10–20 (limitado pelo ELM) | 10–40 ms | Média-alta | Alta | Média | **perdida** | convive |
| USB serial | 5–20 kB/s | 15–25 | 10–25 ms | **Muito alta** | nula (alimenta do carro) | Média | preservada | **conflita (com fio)** |

**Escolha: Bluetooth Classic SPP.** Justificativa:

1. **Latência e estabilidade** melhores que BLE, equivalentes a Wi-Fi, com muito menos variabilidade.
2. **Não derruba a internet** do celular (Wi-Fi derruba) e não conflita com Android Auto (USB conflita).
3. **Consumo** menor que Wi-Fi; o dongle BT é alimentado pelo OBD, o celular só mantém o link.
4. **Custo e oferta**: praticamente todo dongle ELM327 barato é SPP ou Wi-Fi; BLE de qualidade custa mais.
5. **API Android estável** (`BluetoothSocket`), sem depender de scanner GATT/MTU/negociação.

Ressalvas obrigatórias:
- Evitar clones "v1.5" (§2.4.1); preferir dongles com firmware real 1.4b/2.1+ (ex.: família OBDLink STN, Vgate vLinker) `[HIPÓTESE: modelos recomendados por comunidade; validar com `ATI` e probe de capacidades]`.
- O Wi-Fi continua suportado como fallback (ex.: dongle que já se tem) — o custo é a perda de internet e a UX de trocar de rede.
- USB só para bancada/testes com AA desligado.

---

## 4. PIDs — fórmulas exatas (Modo 01)

### 4.1 Convenções

- Na resposta `41 <PID> A B C D…`, **A** é o primeiro byte de **dados** (não o `41`), **B** o segundo, etc. `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — nota da coluna Formula]`.
- Todas as fórmulas abaixo foram conferidas contra duas fontes independentes: a tabela J1979 da Wikipedia e os decoders do python-OBD `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/decoders.py]`.
- "Faixa válida" = limites matemáticos da fórmula (o que o protocolo permite). "Faixa plausível" = o que se espera num Yaris 2021 1.5 flex **saudável**, em Fortaleza (nível do mar) — tudo nessa coluna é `[HIPÓTESE]` de engenharia e serve para *sanity check*/alarme, não para validação de emissões.
- O app deve exibir o valor **bruto** e o valor decodificado em modo debug; nunca "corrigir" valor fora da faixa plausível — apenas sinalizar `SUSPEITO`.

### 4.2 Tabela mestra (Modo 01)

| PID | Nome | Bytes de dados | Fórmula (A, B = bytes de dados) | Unidade | Faixa válida | Faixa plausível Yaris 1.5 flex |
|---|---|---|---|---|---|---|
| `0x04` | Carga calculada do motor | 1 | `A × 100 / 255` | % | 0 … 100 | 15–35 marcha lenta; 20–45 cruzeiro; 80–100 WOT |
| `0x05` | Temperatura do líquido de arrefecimento | 1 | `A − 40` | °C | −40 … 215 | 80–105 (ventoinha ~95–100); > 110 alarme |
| `0x0B` | Pressão absoluta do coletor (MAP) | 1 | `A` | kPa | 0 … 255 | 25–45 marcha lenta; 90–100 WOT (aspirado) |
| `0x0C` | Rotação do motor (RPM) | 2 | `(256·A + B) / 4` | rpm | 0 … 16 383,75 | 600–750 lenta; corte ~6 400 |
| `0x0D` | Velocidade do veículo | 1 | `A` | km/h | 0 … 255 | 0–180 |
| `0x0E` | Avanço de ignição | 1 | `A / 2 − 64` | ° antes do TDC | −64 … 63,5 | 5–20 lenta; 25–45 cruzeiro |
| `0x0F` | Temperatura do ar de admissão (IAT) | 1 | `A − 40` | °C | −40 … 215 | ambiente … 65 (após parado no sol) |
| `0x10` | Vazão de ar (MAF) | 2 | `(256·A + B) / 100` | g/s | 0 … 655,35 | 2–5 lenta; 60–90 WOT |
| `0x11` | Posição do acelerador (TPS) | 1 | `A × 100 / 255` | % | 0 … 100 | 10–20 lenta; 90–100 WOT |
| `0x14`–`0x1B` | Sensores de O₂ (8 sensores: B1S1…B2S4) | 2 | tensão: `A / 200`; trim curto: `B × 100 / 128 − 100` | V; % | 0 … 1,275 V; −100 … 99,2 % | 0,1–0,9 V alternando (sensor estreito); trim ±10 % |
| `0x1F` | Tempo desde a partida do motor | 2 | `256·A + B` | s | 0 … 65 535 | 0 … 65 535 |
| `0x21` | Distância percorrida com MIL acesa | 2 | `256·A + B` | km | 0 … 65 535 | 0 se nunca acendeu |
| `0x22` | Pressão do rail (relativa ao vácuo) | 2 | `0,079 × (256·A + B)` | kPa | 0 … 5 177,265 | não aplicável (aspirado, sem rail) |
| `0x23` | Pressão do rail (manômetro; diesel/GDI) | 2 | `10 × (256·A + B)` | kPa | 0 … 655 350 | não aplicável (aspirado) |
| `0x2F` | Nível de combustível | 1 | `A × 100 / 255` | % | 0 … 100 | 0–100 (resolução 0,392 %) |
| `0x31` | Distância desde limpeza de DTCs | 2 | `256·A + B` | km | 0 … 65 535 | 0 … 65 535 |
| `0x33` | Pressão barométrica | 1 | `A` | kPa | 0 … 255 | 99–102 (Fortaleza, nível do mar) |
| `0x42` | Tensão do módulo de controle | 2 | `(256·A + B) / 1000` | V | 0 … 65,535 | 13,5–14,4 motor ligado; 12,2–12,8 desligado |
| `0x59` | Pressão absoluta do rail | 2 | `10 × (256·A + B)` | kPa | 0 … 655 350 | não aplicável (aspirado) |
| `0x5B` | Vida restante da bateria híbrida | 1 | `A × 100 / 255` | % | 0 … 100 | **não é pressão de rail** (ver nota) |
| `0x5C` | Temperatura do óleo do motor | 1 | `A − 40` | °C | −40 … 210 | 80–110 em regime |
| `0x5E` | Taxa de consumo de combustível | 2 | `(256·A + B) / 20` | L/h | 0 … 3 212,75 | 0,6–1,2 lenta; 25–45 WOT |

Fontes linha a linha: `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — tabela Service 01]` e `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/decoders.py]` (funções `percent`, `temp`, `pressure`, `sensor_voltage`, `timing_advance`, `fuel_rate`, `uas(0x0B)`, `uas(0x25)`, `uas(0x1B)`).

**Nota sobre `0x5B`:** você citou `0x23/0x59/0x5B` como "pressão rail (diesel)". Os dois primeiros estão corretos (`0x22` é a relativa, `0x23` a manométrica, `0x59` a absoluta). **`0x5B` é "Hybrid battery pack remaining life"**, não pressão — a pressão absoluta do rail é `0x59` `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs]`. O Yaris 2021 brasileiro é flex (não híbrido) e não tem rail de alta pressão, então `0x22/0x23/0x59/0x5B` devem aparecer como **não suportados** — mas o decoder deve existir (custo zero) para outros carros.

**Nota sobre `0x5E`:** é um PID "novo" (J1979-2/SAE 2010+) e não é obrigatório; muitos carros não respondem. Se responder, vale ouro para consumo instantâneo — mas **não** derive consumo dele sem verificar (a taxa pode ser calculada ou medida). `[CONFIRMADO: fórmula; suporte veicular é HIPÓTESE]`.

**Nota sobre sensores de O₂ (`0x14`–`0x1B`):** `B == 0xFF` significa "sensor não usado no cálculo de trim" — nesse caso reportar apenas a tensão e omitir o trim `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs]`. No Yaris, espera-se `0x14` (B1S1) e talvez `0x15` (B1S2); `0x16`–`0x1B` normalmente não suportados.

> **Premissa de motorização:** assume-se o Yaris 2021 brasileiro com motor **1.5 flex (família 2NR-FBE)** `[HIPÓTESE]`; as faixas plausíveis e a existência de `0x52` (etanol) dependem disso. Se o carro for 1.3, as faixas de MAF/carga mudam um pouco, mas as fórmulas não.

#### 4.2.1 PIDs extras úteis (mesmas fontes confirmadas)

| PID | Nome | Bytes | Fórmula | Unidade | Faixa válida |
|---|---|---|---|---|---|
| `0x30` | Warm-ups desde limpeza de DTCs | 1 | `A` | contagem | 0 … 255 |
| `0x3C`–`0x3F` | Temperatura do catalisador (B1S1, B2S1, B1S2, B2S2) | 2 | `(256·A + B)/10 − 40` | °C | −40 … 6 513,5 |
| `0x43` | Carga absoluta | 2 | `(256·A + B) × 100 / 255` | % | 0 … 25 700 |
| `0x46` | Temperatura ambiente | 1 | `A − 40` | °C | −40 … 215 |
| `0x49` / `0x4A` | Posição do pedal D / E | 1 | `A × 100 / 255` | % | 0 … 100 |
| `0x4C` | Atuador de borboleta comandado | 1 | `A × 100 / 255` | % | 0 … 100 |
| `0x4E` | Tempo desde limpeza de DTCs | 2 | `256·A + B` | min | 0 … 65 535 |
| `0x51` | Tipo de combustível | 1 | enumerado | código | 1 … 23 |
| `0x52` | Percentual de etanol | 1 | `A × 100 / 255` | % | 0 … 100 |
| `0x5A` | Posição relativa do pedal | 1 | `A × 100 / 255` | % | 0 … 100 |

`[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — tabela Service 01]`. `0x51` é enumerado (a tabela de códigos está na mesma fonte, seção "Fuel Type Coding"); não invente a lista — leia o código e mostre "código N" quando desconhecido.

### 4.3 PIDs de suporte (`0x00`, `0x20`, `0x40`, `0x60`, `0x80`, `0xA0`, `0xC0`)

Cada um devolve **4 bytes** (A, B, C, D) que formam um bitmask de 32 bits, big-endian. O bit mais significativo de A corresponde ao **primeiro PID da faixa seguinte**:

| Requisição | Byte A bit 7 → PID | … | Byte D bit 0 → PID |
|---|---|---|---|
| `0100` | `0x01` | … | `0x20` |
| `0120` | `0x21` | … | `0x40` |
| `0140` | `0x41` | … | `0x60` |
| `0160` | `0x61` | … | `0x80` |
| `0180` | `0x81` | … | `0xA0` |
| `01A0` | `0xA1` | … | `0xC0` |
| `01C0` | `0xC1` | … | `0xE0` |

`[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — "Service 01 PID 00" e entradas de 0x20/0x40/0x60]`.

Exemplo canônico: resposta `41 00 BE 1F A8 13` ⇒

```
A=BE = 1011 1110 → 01 03 04 05 06 07          (02 e 08 não)
B=1F = 0001 1111 → 0C 0D 0E 0F 10
C=A8 = 1010 1000 → 11 13 15
D=13 = 0001 0011 → 1C 1F 20
Suportados: 01,03,04,05,06,07,0C,0D,0E,0F,10,11,13,15,1C,1F,20
```

`[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — exemplo BE1FA813]`.

Decoder genérico:

```kotlin
/** base = PID do pedido de suporte (0x00, 0x20, 0x40, ...) */
fun parseSupported(base: Int, data: ByteArray): Set<Int> {
    require(data.size >= 4)
    val mask = ((data[0].toLong() and 0xFF) shl 24) or
               ((data[1].toLong() and 0xFF) shl 16) or
               ((data[2].toLong() and 0xFF) shl 8)  or
                (data[3].toLong() and 0xFF)
    return buildSet {
        for (i in 0..31) {
            val bit = 31 - i                       // MSB primeiro
            if ((mask shr bit) and 1L == 1L) add(base + 1 + i)
        }
    }
}
```

Regras:

1. Só pedir `0120` se o bit de `0x20` estiver setado em `0100`; e assim por diante. Não pedir `01C0` "no escuro" — economiza comandos.
2. O bit diz "o PID é **declarado** suportado", não que ele responderá sempre (pode retornar `NO DATA` sob certas condições).
3. `0100` pode vir com 6 bytes de dados (`41 00` + 4) ou com espaços (`41 00 BE 1F A8 13`); o parser limpa espaços e usa só os 4 bytes após o PID.
4. Bitmask é **por ECU**. Com múltiplas respostas (7E8/7E9), o `VehicleProfile` deve guardar o bitmask do motor (7E8).

### 4.4 PID não suportado (`7F 01 12`) e estado "indisponível"

Duas formas de "não suportado":

| Resposta | Significado | Ação |
|---|---|---|
| `7F 01 12` | resposta negativa do ECU: serviço `01`, NRC `0x12` (subfunção/PID inválido ou não suportado) | marcar `Unsupported` **definitivo** para o PID naquele ECU |
| `NO DATA` (3×) | ECU ignorou o PID | marcar `Unsupported` **provisório** (re-testar em nova sessão/7 dias) |

`[CONFIRMADO: https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/command/RegexPatterns.kt]` — o padrão `7F 0[0-A] 1[1-2]` cobre NRC `0x11` (serviço não suportado) e `0x12` (subfunção/PID não suportado). O python-OBD segue a mesma lógica: `NO DATA`/`7F …` ⇒ comando não suportado, e ele **remove** o comando da lista ativa `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/elm327.py]`.

Modelo de estado (usar no `VehicleProfile` e na UI):

```kotlin
enum class PidAvailability { UNKNOWN, SUPPORTED, UNSUPPORTED, ERROR }

data class PidSupport(
    val pid: Int,
    val availability: PidAvailability,
    val confirmedAtMs: Long?,
    val lastError: String? = null,
)
```

Comportamento:
- `UNSUPPORTED` ⇒ PID **não aparece** na UI (ou aparece em "diagnóstico → PIDs suportados" com badge cinza).
- `UNKNOWN` ⇒ aparece como "—" e é testado no próximo ciclo de descoberta.
- **Nunca** transformar `UNSUPPORTED` em zero: zero é um valor válido para RPM/velocidade e mentiria para o usuário.

---

## 5. Diagnóstico

### 5.1 Visão geral dos serviços

| Serviço | Requisição | Resposta positiva | Conteúdo |
|---|---|---|---|
| 01 | `01 <PID>` | `41 …` | dados atuais |
| 02 | `02 <PID> <frame>` | `42 …` | freeze frame |
| 03 | `03` | `43 …` | DTCs confirmados |
| 04 | `04` | `44` | limpa DTCs/freeze frame/readiness |
| 06 | `06 <MID>` | `46 …` | resultados de testes (CAN) |
| 07 | `07` | `47 …` | DTCs pendentes |
| 09 | `09 <PID>` | `49 …` | informações do veículo (VIN etc.) |
| 0A | `0A` | `4A …` | DTCs permanentes |

`[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — tabela de Services/Modes]`.

### 5.2 DTCs — Modo 03 (confirmados), 07 (pendentes), 0A (permanentes)

#### Formato no CAN (com `ATCAF1`)

- **Modo 03:** `43 <N> <DTC1 hi> <DTC1 lo> <DTC2 hi> …`, onde `N` = número de DTCs. Cada DTC = 2 bytes. O ELM pode preencher o resto com `00 00`. `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — Service 03: "Each trouble code requires 2 bytes…"]`.
- **Modo 07:** `47` seguido dos pares de DTCs. Alguns ECUs incluem um byte de contagem após o `47`; **o parser deve ser tolerante** (ver algoritmo abaixo) `[HIPÓTESE: presença de contador no 07 varia por ECU]`.
- **Modo 0A:** `4A` seguido dos pares. Esses DTCs **não** são apagados pelo Modo 04 — só desaparecem quando o ECU confirma o reparo após ciclos de condução `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — Service 0A]`.

Exemplo (Modo 03): `43 02 01 33 04 20` ⇒ 2 DTCs: **P0133** e **P0420**.

#### Decodificação de 2 bytes → letra + 4 dígitos

| Bits | Significado |
|---|---|
| `A7 A6` | categoria: `00`=P (powertrain), `01`=C (chassis), `10`=B (body), `11`=U (network) |
| `A5 … B0` | número (14 bits) |

Algoritmo: `letra = "PCBU"[(A >> 6) & 3]`; `d2 = (A >> 4) & 3` (o segundo dígito, sempre 0–3); `num = ((A and 0x0F) shl 8) or B` (os 12 bits restantes, exibidos em 3 dígitos hex). Resultado = 5 caracteres: `letra + d2 + "%03X".format(num)` `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — Service 03 e exemplo U0158]`.

```kotlin
fun decodeDtc(a: Int, b: Int): String? {
    if (a == 0 && b == 0) return null                  // padding
    val letter = "PCBU"[(a shr 6) and 0x03]
    val digit2 = (a shr 4) and 0x03
    val num = ((a and 0x0F) shl 8) or (b and 0xFF)   // 12 bits restantes
    return "$letter$digit2" + "%03X".format(num)     // ex.: P0133, U0158
}
```

Exemplos verificados: `01 33` → **P0133**; `04 20` → **P0420**; `C1 58` → **U0158** `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs]`.

Algoritmo tolerante para 03/07/0A (usar sempre):

```kotlin
fun parseDtcPayload(mode: Int, payload: ByteArray): List<String> {
    var data = payload
    // Modo 03 sempre traz contador; 07/0A às vezes.
    if (mode == 0x03 && data.isNotEmpty()) data = data.copyOfRange(1, data.size)
    else if (mode != 0x03 && data.size >= 3) {
        val asCount = data[0].toInt() and 0xFF
        if (asCount * 2 + 1 == data.size) data = data.copyOfRange(1, data.size)
    }
    return data.toList().chunked(2)
        .filter { it.size == 2 }                       // ignora byte solto no fim
        .mapNotNull { (h, l) -> decodeDtc(h.toInt() and 0xFF, l.toInt() and 0xFF) }
}
```

### 5.3 Modo 04 — limpar DTCs e freeze frame

**O que faz:** apaga DTCs confirmados, freeze frame, resultados de testes e **zera os monitores de readiness** ("readiness monitors") `[CONFIRMADO: https://elm327obd2.com/elm327-commands/ — "04 clears codes and freeze frame, and resets readiness monitors"]` e `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — Service 04]`.

**O que NÃO faz:**
- Não apaga DTCs **permanentes** (0A) — esses só saem após o ECU validar o reparo `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — Service 0A]`.
- Não conserta nada; o DTC volta no próximo ciclo se a falha persistir.

**Riscos e regras de UX (obrigatórias):**
1. **Nunca** executar automaticamente, no boot, por reconexão ou por "botão de pânico". Só por ação explícita do usuário.
2. Tela de confirmação com: (a) lista dos DTCs que serão apagados; (b) aviso de que o veículo **não passará** em inspeção de emissões até completar os monitores; (c) exigência de digitar/segurar "CONFIRMAR" por 2 s; (d) veículo **parado, motor ligado, câmbio em P/N**.
3. Registrar em log local (data, VIN, DTCs apagados) — evidência para mecânico.
4. Bloquear se `0x0D` (velocidade) > 0 ou se não houver leitura recente de RPM/velocidade.
5. Após o 04, invalidar o cache de readiness e forçar releitura de `0101`.

`[HIPÓTESE: boas práticas de segurança; não são exigidas pelo protocolo]`.

### 5.4 Modo 02 — freeze frame

Formato da requisição: `02 <PID> <frame number>` — **o número do frame vai no terceiro byte**, ao contrário do Modo 01 `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — Service 02: "A person has to send the frame number in the data section of the message"]`.

- `frame 0x00` = o snapshot armazenado quando o DTC que disparou o freeze foi setado (o caso normal).
- `frame 0xFF` / outros valores = frames adicionais, se o ECU armazenar mais de um; a maioria dos carros de passeio só tem o frame 0 `[HIPÓTESE]`.
- **PID `0x02` dentro do Modo 02** devolve o **DTC que causou o freeze frame** (2 bytes), decodificado como em §5.2. Se retornar `00 00`, não há freeze frame e os demais PIDs do Modo 02 não têm significado `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs]`.

Exemplo:
```
>020200        → 42 02 00 01 33     (DTC do freeze = P0133)
>020C00        → 42 0C 00 1A F8     (RPM no momento do freeze = 1726)
>020500        → 42 05 00 7B        (coolant = 83 °C)
```

Implementação: `freezeFrame(frame: Int = 0, pids: List<Int>)` reusa os decoders do Modo 01 (mesmas fórmulas), mudando só o modo e inserindo o frame. O python-OBD inclusive **clona** os comandos do Modo 01 para o Modo 02 `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/commands.py — __mode2__]`.

### 5.5 Modo 09 — informações do veículo

| PID | Nome | Resposta | Bytes | Observações |
|---|---|---|---|---|
| `09 00` | PIDs 09 suportados | `49 00` + bitmask 4 bytes | 4 | mesma lógica de bitmask (§4.3), faixa `01`–`20` |
| `09 01` | Contagem de mensagens do VIN | `49 01 <n>` | 1 | "usualmente 5" em ISO 9141/KWP/J1850; **não usado em CAN** `[CONFIRMADO: Wikipedia]` |
| `09 02` | **VIN** | `49 02 01` + 17 ASCII | 17 | multi-frame; ASCII, padding `0x00` |
| `09 03` | Contagem de mensagens do CALID | `49 03 <n>` | 1 | múltiplo de 4 (legado) |
| `09 04` | **Calibration ID** | `49 04 <idx>` + 16 ASCII | 16/cada | pode haver vários CALIDs |
| `09 05` | Contagem de mensagens do CVN | `49 05 <n>` | 1 | legado |
| `09 06` | **CVN** | `49 06 <idx>` + 4 bytes | 4/cada | número de CVNs = número de CALIDs |
| `09 09` | Contagem de mensagens do ECU name | `49 09 <n>` | 1 | legado |
| `09 0A` | **ECU name** | `49 0A <idx>` + 20 ASCII | 20 | ex.: "ECM-EngineControl" |

`[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — Service 09]` e `[CONFIRMADO: https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/commands.py — __mode9__, com tamanhos 22/18/10 bytes de mensagem]`.

Recomendações:
- Ler `0902` **uma vez por sessão** e cachear por VIN. É multi-frame: exigir `ATCAF1`, timeout maior e `ATH1` (para confirmar que veio de `7E8`).
- Decodificar ASCII **descartando bytes de controle** (`< 0x20`) no início e `0x00` no fim; não confiar no byte de índice.
- Validar VIN com regex `^[A-HJ-NPR-Z0-9]{17}$` (sem I, O, Q) `[HIPÓTESE: convenção VIN ISO 3779]`.
- `0904`/`0906` são úteis para detectar ECU reprogramada (comparar CALID/CVN entre sessões).

### 5.6 Modo 01 PID 01 — status dos monitores e MIL

Resposta: 4 bytes A, B, C, D `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — "Service 01 PID 01"]`.

**Byte A:**

| Bit | Significado |
|---|---|
| `A7` | MIL/check-engine aceso (1 = aceso) |
| `A6`–`A0` | número de DTCs confirmados (0–127) |

**Byte B (testes comuns):**

| Bit | Disponibilidade (1 = disponível) | Bit | Completude (0 = completo) |
|---|---|---|---|
| `B0` | Misfire | `B4` | Misfire |
| `B1` | Fuel System | `B5` | Fuel System |
| `B2` | Components | `B6` | Components |
| `B3` | Tipo de ignição: `0` = faísca (Otto), `1` = compressão (diesel) | `B7` | reservado (0) |

**Bytes C e D — ignição por faísca (o caso do Yaris):**

| Sistema | Disponibilidade | Completude |
|---|---|---|
| EGR e/ou VVT | `C7` | `D7` |
| O₂ heater | `C6` | `D6` |
| O₂ sensor | `C5` | `D5` |
| GPF (filtro de particulados) | `C4` | `D4` |
| Secondary Air | `C3` | `D3` |
| EVAP | `C2` | `D2` |
| Heated Catalyst | `C1` | `D1` |
| Catalyst | `C0` | `D0` |

**Bytes C e D — ignição por compressão (diesel):** `C7/D7` EGR/VVT, `C6/D6` PM filter, `C5/D5` exhaust gas sensor, `C4/D4` reservado, `C3/D3` boost pressure, `C2/D2` reservado, `C1/D1` NOx/SCR, `C0/D0` NMHC catalyst `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs]`.

Regra de leitura: **disponibilidade = 1 significa "existe"**; **completude = 0 significa "teste concluído"** (a lógica é invertida — é a maior fonte de bug nessa tela).

Exemplo didático `82 07 65 04`:

```
A=82  → MIL aceso, 2 DTCs
B=07  → 0000 0111: comuns completos (B6..B4=000), ignição faísca (B3=0),
         misfire/fuel/components disponíveis (B2..B0=111)
C=65  → 0110 0101: O2 heater, O2 sensor, EVAP e catalyst disponíveis
D=04  → 0000 0100: D2=1 → EVAP NÃO completou; os demais completaram
```
`[HIPÓTESE: valores apenas para teste de parser; não são leitura real do Yaris]`.

**Modo 01 PID 41** tem a mesma estrutura, com A sempre `00`, e descreve os monitores **deste ciclo de condução** `[CONFIRMADO: https://en.wikipedia.org/wiki/OBD-II_PIDs — "Service 01 PID 41"]`.

---

## 6. Arquitetura modular (Kotlin)

### 6.1 Camadas e dependências

```
┌────────────────────────────────────────────────────────────┐
│ UI (Compose/Views)                                         │
│   TelemetryState (StateFlow<Map<Int, Sample>>)             │
└───────────────▲────────────────────────────────────────────┘
                │ Samples
┌───────────────┴────────────────────────────────────────────┐
│ PidScheduler (round-robin, prioridade, orçamento)          │
│   ├─ PidDecoder.Registry (PID → fórmula)                   │
│   └─ VehicleProfile (suporta? protocolo? VIN?)             │
└───────────────▲────────────────────────────────────────────┘
                │ ElmResult
┌───────────────┴────────────────────────────────────────────┐
│ ElmSession (fila serializada, mutex, timeout, latência)    │
│   ├─ ElmBootstrapper / AdapterProbe                        │
│   └─ ResponseParser (limpa eco, SEARCHING, prompt '>')     │
└───────────────▲────────────────────────────────────────────┘
                │ Byte streams
┌───────────────┴────────────────────────────────────────────┐
│ Transport: SppTransport | BleTransport | TcpTransport | Usb│
└────────────────────────────────────────────────────────────┘
                ▲
        ConnectionManager (backoff + link-dead + estado)
```

Princípio: **a camada de cima nunca vê bytes**; a de baixo nunca vê PID. Isso permite trocar transporte e testar com transcript (§7).

### 6.2 `Transport`

```kotlin
package br.com.obd.transport

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

enum class TransportKind { BLUETOOTH_SPP, BLE, TCP, USB_SERIAL }

interface Transport : Closeable {
    val id: String                  // MAC, "192.168.0.10:35000", VID:PID
    val kind: TransportKind
    val isOpen: Boolean

    /** Abre a conexão. Deve lançar IOException em falha. */
    suspend fun open()

    /** Streams válidos somente após open(). */
    fun input(): InputStream
    fun output(): OutputStream

    override fun close()
}
```

Regras:
- `open()` é bloqueante por natureza (RFCOMM/TCP/GATT) → sempre em `Dispatchers.IO`.
- `close()` deve ser idempotente e **interromper** leituras em andamento (é o mecanismo de cancelamento).
- `input()`/`output()` não fazem buffer próprio; o `ElmSession` é o único que lê/escreve.

### 6.3 `ElmSession`

```kotlin
package br.com.obd.elm

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface ElmResult {
    val command: String
    val latency: Duration

    data class Lines(
        override val command: String,
        val lines: List<String>,       // já sem eco, prompt, vazias
        val raw: String,
        override val latency: Duration,
    ) : ElmResult

    data class Failure(
        override val command: String,
        val error: ElmError,
        val raw: String = "",
        override val latency: Duration = Duration.ZERO,
    ) : ElmResult
}

enum class ElmError {
    NO_DATA, SEARCHING_TIMEOUT, UNKNOWN_COMMAND, STOPPED, BUS_INIT_ERROR,
    UNABLE_TO_CONNECT, CAN_ERROR, BUFFER_FULL, NEGATIVE_RESPONSE,
    TIMEOUT, LINK_DEAD, TRUNCATED,
}

interface ElmSession {
    /** Executa um comando (AT ou OBD) e devolve linhas limpas. Serializado. */
    suspend fun execute(command: String, timeout: Duration = 400.milliseconds): ElmResult

    /** Exige "OK" (comandos AT de configuração). */
    suspend fun requireOk(command: String, timeout: Duration = 400.milliseconds)

    val stats: SessionStats
    val isAlive: Boolean
}
```

Implementação (resumo do algoritmo — o código completo deve seguir exatamente esta ordem):

```kotlin
class SerialElmSession(
    private val transport: Transport,
    private val scope: CoroutineScope,
) : ElmSession {

    private val mutex = Mutex()
    private val inbox = Channel<String>(Channel.UNLIMITED)
    private var reader: Job? = null
    private var alive = true

    private val _stats = MutableStateFlow(SessionStats())
    override val stats: SessionStats get() = _stats.value
    override val isAlive: Boolean get() = alive

    fun start() {
        reader = scope.launch(Dispatchers.IO) {
            val line = StringBuilder()
            val inBuf = ByteArray(1)
            while (isActive) {
                val n = transport.input().read(inBuf)
                if (n < 0) { alive = false; break }
                val c = inBuf[0].toInt().toChar()
                line.append(c)
                if (c == '>') { inbox.send(line.toString()); line.clear() }
            }
        }
    }

    override suspend fun execute(command: String, timeout: Duration): ElmResult =
        mutex.withLock {
            val t0 = TimeSource.Monotonic.markNow()
            try {
                transport.output().apply {
                    write("$command\r".toByteArray(Charsets.US_ASCII))
                    flush()
                }
                val raw = withTimeout(timeout) { inbox.receive() }
                val lines = ResponseParser.clean(raw, command)
                val error = ResponseParser.classify(lines)
                val latency = t0.elapsedNow()
                _stats.update { it.copy(lastLatency = latency, commands = it.commands + 1) }
                if (error != null) ElmResult.Failure(command, error, raw, latency)
                else ElmResult.Lines(command, lines, raw, latency)
            } catch (e: TimeoutCancellationException) {
                // ELM pode responder atrasado: sessão fica suja -> descartar e reconectar
                alive = false
                ElmResult.Failure(command, ElmError.TIMEOUT)
            } catch (e: IOException) {
                alive = false
                ElmResult.Failure(command, ElmError.LINK_DEAD)
            }
        }

    override suspend fun requireOk(command: String, timeout: Duration) {
        val r = execute(command, timeout)
        check(r is ElmResult.Lines && r.lines.any { it.trim() == "OK" }) {
            "comando AT não retornou OK: $command -> $r"
        }
    }
}
```

`ResponseParser` (contrato):

```kotlin
object ResponseParser {
    /** Remove prompt, CR/LF, NUL, eco do comando e linhas "SEARCHING...". */
    fun clean(raw: String, command: String): List<String> = raw
        .replace("\u0000", "")
        .replace(">", "")
        .split('\r', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.equals(command.trim(), ignoreCase = true) }   // eco
        .filterNot { it.contains("SEARCHING", ignoreCase = true) }

    fun classify(lines: List<String>): ElmError? {
        val all = lines.joinToString(" ").uppercase()
        return when {
            all.isBlank() -> ElmError.NO_DATA
            "UNABLE TO CONNECT" in all -> ElmError.UNABLE_TO_CONNECT
            "STOPPED" in all -> ElmError.STOPPED
            "BUS INIT" in all && "ERROR" in all -> ElmError.BUS_INIT_ERROR
            "CAN ERROR" in all -> ElmError.CAN_ERROR
            "BUFFER FULL" in all -> ElmError.BUFFER_FULL
            all.contains("?") && lines.size == 1 -> ElmError.UNKNOWN_COMMAND
            "NO DATA" in all -> ElmError.NO_DATA
            // 7F 01 12 etc.
            Regex("""^7F\s+[0-9A-F]{2}\s+[0-9A-F]{2}$""").matches(lines.first().uppercase()) ->
                ElmError.NEGATIVE_RESPONSE
            else -> null
        }
    }
}
```

Detalhes que **não** podem faltar:
- `SEARCHING...` é **ruído** e demora segundos; o `execute` de `0100` na descoberta usa timeout de 15 s.
- O eco é removido comparando a linha ao comando (não por posição).
- `STOPPED` ⇒ sintoma de concorrência; logar e não decodificar.
- Em `TIMEOUT`/`LINK_DEAD`: marcar `alive = false` e **fechar o transport**. Não reutilizar a sessão — a próxima resposta pode chegar atrasada e corromper o próximo comando.
- `stats` guarda: comandos, falhas por tipo, latência p50/p95 (janela móvel), para a UI de diagnóstico.

### 6.4 `PidScheduler` (round-robin com prioridade e orçamento)

```kotlin
package br.com.obd.scheduler

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class PidTask(
    val pid: Int,
    val priority: Int,        // 0 = mais importante
    val periodMs: Long,       // ex.: 200 = 5 Hz
    val timeout: Duration = 350.milliseconds,
    val enabled: Boolean = true,
)

data class CycleStats(
    val cycleMs: Long,
    val commands: Int,
    val skipped: Int,         // tarefas que estouraram o orçamento
    val timeouts: Int,
)

interface PidScheduler {
    fun setTasks(tasks: List<PidTask>)
    fun start(scope: CoroutineScope, onSample: suspend (PidReading) -> Unit)
    fun stop()
    val stats: StateFlow<CycleStats>
}

data class PidReading(
    val pid: Int,
    val payload: ByteArray,     // bytes após 41 <PID>
    val latency: Duration,
    val atMs: Long,
    val ecu: Int? = null,       // 7E8 etc., se ATH1
)
```

Algoritmo:

```
loop:
  t0 = now
  due = tasks.filter { now - lastRun[pid] >= period && now >= nextAllowed[pid] }
  due.sortBy { priority }
  for (task in due):
      if (elapsed > budgetMs) { skipped++; continue }
      result = session.execute("01%02X".format(pid), task.timeout)
      lastRun[pid] = now
      if (result is Lines) onSample(parse(result))
      if (result is Failure && result.error == NO_DATA) nextAllowed[pid] = now + 2000
  sleep until next due (mín. 10 ms)
```

Parâmetros de partida (ajustar após medição):

| Parâmetro | Valor | Por quê |
|---|---|---|
| `budgetMs` por ciclo | 250 ms | deixa 50 ms de folga em 300 ms de ciclo |
| período mínimo | 100 ms | abaixo disso o ELM não acompanha |
| `nextAllowed` após `NO_DATA` | 2 s | evita inundar o barramento com PID morto |
| timeout por PID | 350 ms | `AT ST` 200 ms + margem |
| período após 3 timeouts | 5 s + flag `STALE` | protege o ciclo |

Prioridade sugerida: RPM (0), velocidade (0), carga/TPS (1), coolant (2), IAT (3), nível (4), módulo V (5), MAF (1 se usado em consumo).

### 6.5 `Decoder` — PID → valor tipado

```kotlin
package br.com.obd.decode

import kotlin.math.roundToInt

enum class TelemetryUnit { RPM, KMH, CELSIUS, KPA, PERCENT, VOLT, GRAMS_PER_SEC,
                           LITERS_PER_HOUR, SECONDS, KILOMETERS, DEGREES, RATIO, NONE }

data class Decoded(val value: Double, val unit: TelemetryUnit)

interface PidDecoder {
    val pid: Int
    /** Quantidade de bytes de dados esperada; null = usa NOD/linha. */
    val expectedBytes: Int?
    fun decode(data: ByteArray): Decoded
}

private fun Byte.unsigned(): Int = toInt() and 0xFF

object Decoders {
    val byPid: Map<Int, PidDecoder> = listOf(
        object : PidDecoder { override val pid = 0x04; override val expectedBytes = 1
            override fun decode(data: ByteArray) =
                Decoded(data[0].unsigned() * 100.0 / 255.0, TelemetryUnit.PERCENT) },

        object : PidDecoder { override val pid = 0x05; override val expectedBytes = 1
            override fun decode(data: ByteArray) =
                Decoded(data[0].unsigned() - 40.0, TelemetryUnit.CELSIUS) },

        object : PidDecoder { override val pid = 0x0C; override val expectedBytes = 2
            override fun decode(data: ByteArray) =
                Decoded((data[0].unsigned() * 256 + data[1].unsigned()) / 4.0, TelemetryUnit.RPM) },

        object : PidDecoder { override val pid = 0x0D; override val expectedBytes = 1
            override fun decode(data: ByteArray) =
                Decoded(data[0].unsigned().toDouble(), TelemetryUnit.KMH) },

        object : PidDecoder { override val pid = 0x42; override val expectedBytes = 2
            override fun decode(data: ByteArray) =
                Decoded((data[0].unsigned() * 256 + data[1].unsigned()) / 1000.0, TelemetryUnit.VOLT) },
        // ... um objeto por PID da tabela §4.2 (todos com fórmula confirmada)
    ).associateBy { it.pid }
}
```

Regras:
- `decode` **nunca** lança: retorna `null`/erro tipado se `data.size` não bater com `expectedBytes` (resposta truncada).
- Conversão de unidade só na UI; o domínio usa SI/°C/km/h.
- `timestampMs` é do **início** da requisição (não do decode) e `latencyMs` acompanha a amostra.
- Decoders do Modo 02 reutilizam os mesmos objetos, com `mode = 0x02`.

### 6.6 `VehicleProfile`

```kotlin
package br.com.obd.profile

enum class AdapterCapability {
    AT_AL, AT_CAF0, AT_CFC0, AT_CTM5, AT_CS, AT_IGN, AT_AT2,
    RESPONSE_PENDING_78, CAN_29BIT, ELM_V2X,
}

data class VehicleProfile(
    val key: String,                       // VIN ou "MAC" quando VIN não lido
    val vin: String?,
    val protocolNumber: String,            // "6", "8", ...
    val supportedPids: Set<Int>,
    val unsupportedPids: Set<Int>,
    val readiness: ReadinessSnapshot?,     // PID 01
    val adapterId: String,
    val adapterCapabilities: Set<AdapterCapability>,
    val updatedAtEpochMs: Long,
)

interface VehicleProfileRepository {
    suspend fun load(key: String): VehicleProfile?
    suspend fun save(profile: VehicleProfile)
}

/** Probe executado 1× por adaptador. */
class AdapterProbe(private val session: ElmSession) {
    suspend fun probe(): Set<AdapterCapability> = buildSet {
        fun ok(cmd: String) = runCatching { session.execute(cmd, 500.milliseconds) }
            .getOrNull() is ElmResult.Lines

        if (ok("AT@1")) add(AdapterCapability.AT_AL)          // @1 responde
        if (ok("ATCAF0")) { add(AdapterCapability.AT_CAF0); session.execute("ATCAF1") }
        if (ok("ATCTM5")) { add(AdapterCapability.AT_CTM5); session.execute("ATCTM1") }
        if (ok("ATCS")) add(AdapterCapability.AT_CS)
        // "ELM327 v2." no ATI => família 2.x (provável suporte a 7F xx 78)
        val ati = (session.execute("ATI") as? ElmResult.Lines)?.lines?.firstOrNull().orEmpty()
        if (ati.contains("v2.")) { add(AdapterCapability.ELM_V2X); add(AdapterCapability.RESPONSE_PENDING_78) }
    }
}
```

> O probe acima é um **esqueleto**: `ATCAF0`/`ATCTM5` mudam o estado e precisam ser revertidos. Implementar em transação (executar, verificar `OK`, reverter) e nunca durante um ciclo de PIDs.

### 6.7 `ConnectionManager` — reconexão com backoff e link morto

```kotlin
package br.com.obd.connection

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val attempt: Int) : ConnectionState
    data class Connected(val profile: VehicleProfile, val adapter: String, val volts: Double?) : ConnectionState
    data class Reconnecting(val attempt: Int, val nextDelayMs: Long, val reason: String) : ConnectionState
    data class Failed(val reason: String, val attempts: Int) : ConnectionState
}

class ExponentialBackoff(
    private val initialMs: Long = 500,
    private val factor: Double = 2.0,
    private val maxMs: Long = 15_000,
    private val jitterRatio: Double = 0.2,
) {
    fun delayMs(attempt: Int, random: kotlin.random.Random = kotlin.random.Random.Default): Long {
        val base = (initialMs * factor.pow(attempt)).toLong().coerceAtMost(maxMs)
        val jitter = (base * jitterRatio * (random.nextDouble() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(initialMs)
    }
    private fun Double.pow(n: Int) = Math.pow(this, n.toDouble())
}

class ConnectionManager(
    private val transportFactory: TransportFactory,
    private val openSession: (Transport) -> ElmSession,
    private val bootstrap: ElmBootstrapper,
    private val profiles: VehicleProfileRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state

    private val backoff = ExponentialBackoff()
    private var consecutiveFailures = 0
    private var consecutiveTimeouts = 0

    suspend fun connect() { /* abre transport, bootstraps, descobre perfil, emite Connected */ }

    /** Chamado pelo scheduler/health-check. */
    suspend fun onCommandResult(error: ElmError?) {
        when (error) {
            null -> { consecutiveFailures = 0; consecutiveTimeouts = 0 }
            ElmError.TIMEOUT, ElmError.NO_DATA -> {
                if (++consecutiveTimeouts >= 3) reconnect("3 timeouts seguidos")
            }
            ElmError.LINK_DEAD, ElmError.UNABLE_TO_CONNECT, ElmError.CAN_ERROR ->
                reconnect(error.name)
            else -> Unit
        }
    }

    private suspend fun reconnect(reason: String) {
        val attempt = ++consecutiveFailures
        val waitMs = backoff.delayMs(attempt)
        _state.value = ConnectionState.Reconnecting(attempt, waitMs, reason)
        delay(waitMs)
        connect()
    }
}
```

Regras de reconexão:
- **Gatilhos:** `IOException` no transport; 3 timeouts/no-data seguidos; `ATI` falhando no health-check (a cada 10 s quando ocioso); `AT RV` < 10,5 V por 3 leituras (indica ignição desligada → não reconectar em loop, esperar 30 s).
- **Limite:** após 6 tentativas, `Failed("verifique ignição/adaptador")`; a UI oferece "tentar de novo" e o app **para** de consumir bateria.
- **Jitter** obrigatório (evita thundering herd quando o app volta do background junto com outros apps OBD).
- **Nunca** reconectar durante o `PidScheduler` ativo sem parar o scheduler antes.
- Ao reconectar, **re-validar** o protocolo (o carro pode ter sido desligado e religado; `AT RV` é o teste rápido).

### 6.8 Concorrência: as 8 regras que evitam 90 % dos bugs

1. Um único `ElmSession` por conexão; **mutex** em `execute`.
2. Scheduler é o **único** dono do canal de PIDs; comandos AT de manutenção passam por uma fila prioritária (`adminChannel`) que pausa o scheduler.
3. Nenhum byte pode ser enviado enquanto há comando em voo (qualquer char ⇒ `STOPPED`).
4. Cancelamento de coroutine **fecha o transport** (é a única forma de interromper `read()` de RFCOMM).
5. Após timeout, a sessão é considerada suja: fechar/reabrir, nunca "tentar ler o que sobrou".
6. `ATZ` só no boot/reconexão, nunca no meio do ciclo.
7. Leituras e escritas do transport sempre em `Dispatchers.IO`.
8. O scheduler mede a latência **de cada** comando e adapta o período (se p95 > 400 ms, reduzir taxa e emitir evento de degradação).

### 6.9 Dados velhos (`STALE`) vs sem dado

```kotlin
enum class SampleState { FRESH, STALE, NO_DATA, UNSUPPORTED, ERROR }

data class Sample(
    val pid: Int,
    val value: Double?,
    val unit: TelemetryUnit,
    val state: SampleState,
    val timestampMs: Long,
    val latencyMs: Long,
    val rawHex: String,
)

class SampleStore(private val clock: () -> Long = System::currentTimeMillis) {
    private val map = mutableMapOf<Int, Sample>()

    fun put(s: Sample) { map[s.pid] = s }

    fun snapshot(staleAfterMs: Map<Int, Long>): Map<Int, Sample> = map.mapValues { (pid, s) ->
        val limit = staleAfterMs[pid] ?: 2_000
        if (s.state == SampleState.FRESH && clock() - s.timestampMs > limit) {
            s.copy(state = SampleState.STALE)
        } else s
    }
}
```

Política de UI (obrigatória):

| Estado | Significado | UI |
|---|---|---|
| `FRESH` | leitura recente dentro do período esperado | valor normal |
| `STALE` | tinha valor, passou do limite (ex.: 3× o período) | valor em cinza/opaco + ícone de "congelado"; tooltip "última leitura há Xs" |
| `NO_DATA` | ECU não respondeu (ainda pode voltar) | "—" e badge "sem resposta" |
| `UNSUPPORTED` | PID não existe no carro | oculto (ou "n/d" em tela de diagnóstico) |
| `ERROR` | falha de decode/negativa inesperada | "erro" + detalhe em log |

Regras:
- **Nunca** interpolar/animar um valor `STALE` como se fosse novo (um RPM congelado em 3.000 parece "motor acelerando").
- Limite de staleness default: `max(3 × periodMs, 1.500 ms)`.
- Ao reconectar, **limpar** todos os samples (não herdar valores da sessão anterior).

### 6.10 Custo real: quantos PIDs cabem em um ciclo

**Premissa:** o gargalo é o ELM327 (firmware + UART + interpretação), **não** o CAN a 500 kbps. O barramento transmitiria centenas de requisições/s; o dongle entrega **10–20 comandos/s** em condições reais `[HIPÓTESE consolidada — ver cálculo]`.

Decomposição do tempo por comando (do `write` até o `>`):

| Parcela | Estimativa | Base |
|---|---|---|
| TX UART (requisição 5–8 bytes @ 38400 8N1) | 1,3–2,1 ms | 10 bits/byte ⇒ 3.840 B/s |
| Processamento do ELM327 (clone v1.x) | 10–25 ms | firmware, formatação da linha, cópia de buffers `[HIPÓTESE]` |
| Barramento CAN (frame 8 bytes @ 500 k) | ~0,3 ms | 130 bits ≈ 0,26 ms |
| Reação do ECU | 2–15 ms | típico de ECU Toyota; primeiro pedido após boot é mais lento `[HIPÓTESE]` |
| RX UART (resposta 12–20 bytes) | 3,1–5,2 ms | idem UART |
| Overhead BT/RFCOMM + scheduler | 10–30 ms | RTT do link + espera de período `[HIPÓTESE]` |
| **Total por comando** | **~30–75 ms** | soma; usar **60 ms** como constante |

Cálculo:

```
t_cmd (clone típico, BT) ≈ 50–85 ms  → 12–20 cmd/s sustentados
t_cmd (dongle bom, v2.x, BT) ≈ 25–40 ms → 25–35 cmd/s
mínimo teórico no fio (UART+CAN) ≈ 5–7 ms → o barramento NÃO é o gargalo
constante de planejamento: MAX_CMD_S = 15 (clone) / 25 (dongle bom)
```

Por que a latência de **uma** requisição pode ser 20 ms e o **sustentado** cair para 12–20 cmd/s? Porque:
- após a resposta, o ELM espera um tempo adicional (adaptive timing) para ver se vem mais alguma coisa;
- ele formata a linha (`printf`) e copia buffers antes de liberar o próximo comando;
- em multi-frame, espera os CFs;
- o scheduler dorme até o próximo período e há overhead de coroutine/BT.
Por isso **medir** é obrigatório: o `SessionStats` deve expor p50/p95 e o scheduler deve se autoajustar.

Tabela de ciclos com `t_cmd = 60 ms` (clone em BT) — valores conservadores:

| Nº de PIDs no ciclo | Tempo de ciclo | Taxa do conjunto | Comandos/s |
|---|---|---|---|
| 2 | ~125 ms | ~8 Hz | ~16 |
| 3 | ~185 ms | ~5,4 Hz | ~16 |
| 4 | ~245 ms | ~4 Hz | ~16 |
| 6 | ~365 ms | ~2,7 Hz | ~16 |
| 8 | ~485 ms | ~2 Hz | ~16 |
| 12 | ~725 ms | ~1,4 Hz | ~16 |

Com um dongle bom (25 cmd/s), os mesmos ciclos ficam ~40 % mais rápidos: 4 PIDs ≈ 160 ms (6 Hz), 8 PIDs ≈ 320 ms (3 Hz).

**Perfil recomendado para o painel do Yaris (clone em BT, conservador):**

| Tier | PIDs | Período | Comandos/s |
|---|---|---|---|
| A (fluido) | `0x0C` RPM, `0x0D` velocidade, `0x11` TPS (ou `0x04` carga) | 250 ms (4 Hz) | 12 |
| B (lento) | `0x05` coolant, `0x0F` IAT, `0x42` tensão | 2.000 ms (0,5 Hz) | 1,5 |
| C (sob demanda) | `0x10` MAF, `0x5E` fuel rate, `0x2F` nível | 1.000 ms quando a tela correspondente está aberta | +6 no máximo |
| **Total típico** | 6 PIDs fixos | — | **~13,5 cmd/s** |

Isso deixa ~15 % de margem sobre os 15 cmd/s de um clone. Com dongle bom (25 cmd/s), subir o Tier A para 5 Hz (15 cmd/s) e o Tier B para 1 Hz (3 cmd/s) = 18 cmd/s, ainda com folga. Se o p95 passar de 300 ms, o scheduler deve automaticamente: (a) subir o período do Tier A para 350 ms; (b) desligar o Tier C; (c) mostrar indicador de "telemetria degradada".

**Otimização possível (validar):** o ELM327 tem suporte a "Multiple PID Requests" (seção do datasheet) e algumas ECUs respondem a mais de um PID por requisição. Testar `010C0D` e verificar se vêm duas respostas `41 0C …` e `41 0D …`. Se funcionar, o custo por PID cai ~40 % `[HIPÓTESE — validar no carro; não é J1979 estrito]`.

---

## 7. Testabilidade

### 7.1 Estratégia em três níveis

| Nível | O que testa | Como | Roda em |
|---|---|---|---|
| **Unitário** | parser, decoders, bitmask, DTC, backoff, scheduler | JVM puro (`test/`), sem Android | CI, notebook |
| **Integração** | `ElmSession` + `Transport` + boot + reconexão | **emulador ELM327 em TCP local** (`127.0.0.1:35000`) | CI, emulador Android |
| **Sistema** | app completo, latência real, UX | carro real + checklist (§9.2) | Yaris |

O ponto-chave é que **nada na camada de protocolo depende de Bluetooth**: como `Transport` é uma interface e o ELM327 é só texto sobre um stream, um servidor TCP que fala ELM327 é indistinguível do dongle para todo o código acima.

### 7.2 Emulador de ELM327 (TCP local)

Especificação do emulador (implementar em Kotlin/JVM, módulo `:tools:elm-emulator`, ou em Python para o CI):

```
USO: elm-emulator --port 35000 --scenario scenarios/yaris2021.json --fragment-ms 3
```

Requisitos:

1. Aceitar **uma** conexão por vez (como o dongle real); recusar a segunda ou encerrar a primeira (configurável).
2. Ler bytes até `\r`; interpretar a linha (case-insensitive, espaços ignorados).
3. Responder **sempre** terminando em `\r\r>` (ou `\r\n\r\n>` se `ATL1`).
4. Máquina de estados mínima:

| Comando | Estado/ação |
|---|---|
| `ATZ` | reset; responde `ELM327 v1.5` (configurável) e `>`; pode responder lixo antes |
| `ATI` | devolve a string de versão do cenário |
| `AT@1` / `AT@2` | string de descrição / id |
| `ATE0/1`, `ATL0/1`, `ATS0/1`, `ATH0/1`, `ATAT0/1/2` | `OK` e muda formatação da saída |
| `ATSP0` | `OK`; estado "auto" |
| `ATSP n` | `OK`; fixa protocolo; se `n` inválido, `?` |
| `ATDP` / `ATDPN` | descrição / `A6` ou `6` |
| `AT RV` | `12.6V` (configurável, com queda durante "crank") |
| `ATST hh` | `OK`; guarda timeout |
| `ATCAF1` / `ATCFC1` | `OK` |
| `0100` | bitmask do cenário; se protocolo auto e `searchDelayMs > 0`, emite `SEARCHING...` antes |
| `01xx` | valor do cenário (ou `NO DATA` / `7F 01 12` / atraso / lixo, conforme regra) |
| `03/07/0A` | DTCs do cenário |
| `04` | `44` e limpa DTCs (simula perda de readiness) |
| `0902` | VIN multi-frame (com `:` se CAF1, ou frames crus se CAF0) |
| `09xx` | CALID/CVN/ECU name |
| desconhecido | `?` |

5. **Injeção de falhas por regra** (o coração dos testes): cada regra do cenário pode ter `fragments`, `delayMs`, `dropBytes`, `noPrompt`, `duplicate`, `interleave`, `disconnectAfterMs`.
6. Escrever bytes em pedaços (`fragment-ms`) para simular BLE e reproduzir respostas partidas — o reader do app **tem** de aguentar.

Exemplo de cenário (JSON):

```json
{
  "name": "yaris-2021-br",
  "adapter": "ELM327 v2.1",
  "protocol": "6",
  "searchDelayMs": 900,
  "pids": {
    "0100": { "response": "41 00 BE 1F A8 13" },
    "010C": { "response": "41 0C 1A F8", "delayMs": 8 },
    "0105": { "response": "41 05 7B" },
    "010D": { "response": "41 0D 00" },
    "0101": { "response": "41 01 82 07 65 04" }
  },
  "dtcs": {
    "03": { "response": "43 02 01 33 04 20" },
    "07": { "response": "47 01 33" },
    "0A": { "response": "4A 00 00" }
  },
  "vin": { "response": "49 02 01 39 42 52 42 41 33 48 45 30 4D 30 31 32 33 34 35 36" },
  "rules": [
    { "match": "010F", "response": "NO DATA" },
    { "match": "0110", "response": "7F 01 12" }
  ]
}
```

### 7.3 Formato de transcript para replay/golden tests

Formato **JSON Lines** (uma sessão por arquivo, `.jsonl`), legível por humano e por CI. Campos obrigatórios marcados com `*`:

| Campo | Tipo | Descrição |
|---|---|---|
| `type`* | string | `session`, `cmd`, `resp`, `delay`, `disconnect`, `note` |
| `value`* | string | comando (sem `\r`) ou resposta bruta **incluindo** `\r`/`>` |
| `atMs` | int | timestamp relativo ao início (para asserts de latência) |
| `fragments` | int[] | tamanhos dos pedaços em que a resposta deve ser escrita |
| `error` | string | `timeout`, `garbage`, `truncated`, `echo`, `stopped` |
| `expect` | string | resultado esperado: `ok`, `no_data`, `unsupported`, `link_dead`, `reconnect` |

Exemplo (`transcripts/boot-ok.jsonl`):

```jsonl
{"type":"session","name":"boot-ok","adapter":"ELM327 v2.1","protocol":"6","baud":38400}
{"type":"cmd","value":"ATZ","atMs":0}
{"type":"resp","value":"ELM327 v2.1\r\r>","atMs":820}
{"type":"cmd","value":"ATE0","atMs":900}
{"type":"resp","value":"OK\r\r>","atMs":915}
{"type":"cmd","value":"ATL0","atMs":930}
{"type":"resp","value":"OK\r\r>","atMs":945}
{"type":"cmd","value":"ATS0","atMs":960}
{"type":"resp","value":"OK\r\r>","atMs":975}
{"type":"cmd","value":"ATH0","atMs":990}
{"type":"resp","value":"OK\r\r>","atMs":1005}
{"type":"cmd","value":"ATAT1","atMs":1020}
{"type":"resp","value":"OK\r\r>","atMs":1035}
{"type":"cmd","value":"ATST 32","atMs":1050}
{"type":"resp","value":"OK\r\r>","atMs":1065}
{"type":"cmd","value":"ATSP0","atMs":1080}
{"type":"resp","value":"OK\r\r>","atMs":1095}
{"type":"cmd","value":"0100","atMs":1110}
{"type":"resp","value":"SEARCHING...\r41 00 BE 1F A8 13\r\r>","atMs":2100,"fragments":[3,5,4]}
{"type":"cmd","value":"ATDPN","atMs":2120}
{"type":"resp","value":"A6\r\r>","atMs":2135}
{"type":"cmd","value":"ATSP6","atMs":2150}
{"type":"resp","value":"OK\r\r>","atMs":2165}
{"type":"cmd","value":"AT RV","atMs":2180}
{"type":"resp","value":"12.6V\r\r>","atMs":2195}
```

Reprodutor de transcript (`TranscriptTransport`): lê o arquivo, responde ao comando enviado pela `ElmSession` com a `resp` correspondente, aplicando `fragments` (escrevendo em pedaços com `delayMs`) e `error`. Se o comando não existir no transcript, responde `?` e o teste falha com diff — é assim que se detecta mudança de sequência de boot.

Casos de uso:
- **Golden test:** rodar a `ElmSession` sobre o transcript e comparar os `PidReading` com valores esperados (tolerância 0).
- **Regressão de UI:** alimentar o `SampleStore` com o transcript e comparar o snapshot.
- **Latência:** `atMs` do transcript define o esperado; medir o processamento do app (não confundir com latência do carro).

### 7.4 PTY e alternativas

- **Linux/macOS (CI):** `socat -d -d pty,raw,echo=0,link=/tmp/ttyOBD pty,raw,echo=0` cria um par de PTYs; o emulador abre um lado, o app (JVM) o outro. Útil para testar a `SerialTransport` (Java `comm`/jSerialComm).
- **Android:** não há PTY acessível sem root. Portanto, no Android o teste de integração usa **TCP** (o emulador roda em `127.0.0.1` no próprio dispositivo ou em `adb reverse tcp:35000 tcp:35000` a partir do host).
- **Fake transport:** para testes unitários de `ElmSession`, um `Transport` que devolve um `ByteArrayInputStream` com a resposta e captura o que foi escrito. Não precisa de emulador.

### 7.5 Casos de erro a cobrir (obrigatórios)

| # | Cenário injetado | O que o app deve fazer | Onde testar |
|---|---|---|---|
| 1 | Desconexão no meio de um frame (`close` após 3 bytes de uma resposta) | `LINK_DEAD`; amostra descartada; reconexão com backoff; **não** decodificar lixo | integração |
| 2 | Resposta parcial (sem `>`) | timeout → sessão suja → reconectar; não vazar a resposta para o próximo comando | integração |
| 3 | Lixo no buffer (`\r\r>` ou `OK\r\r>`) | `clean()` remove vazios; comando segue | unitário |
| 4 | Timeout total (nenhum byte) | `TIMEOUT`; 3 seguidos ⇒ reconexão | integração |
| 5 | Eco ligado (`010C\r41 0C…`) | primeira linha descartada por igualdade ao comando | unitário |
| 6 | Multi-frame intercalado (7E8 + 7E9) | parser escolhe `7E8` quando `ATH1`; com `ATH0`, primeira linha válida; ignora CF órfão | integração |
| 7 | `NO DATA` e depois sucesso | não marcar `UNSUPPORTED` na primeira falha; retry com backoff | integração |
| 8 | `7F 01 12` | `UNSUPPORTED` definitivo e persistido no perfil | unitário |
| 9 | `SEARCHING...` por 4 s | timeout de descoberta de 15 s; sem falso erro | integração |
| 10 | `STOPPED` | erro de concorrência: abortar comando, logar, **não** repetir imediatamente | integração |
| 11 | VIN com padding `00` e quebras | decodifica 17 chars ASCII, valida regex | unitário |
| 12 | Resposta truncada de `0902` (falta 1 CF) | `TRUNCATED`; repetir 1×; se persistir, VIN = null (não inventar) | integração |
| 13 | `AT RV` = 9,8 V (crank) | estado "ignição instável"; suspender ciclo; aguardar 2 s | integração |
| 14 | Duas respostas para o mesmo PID (7E8 e 7E9) | usa a do motor; registra a duplicata em debug | unitário |

### 7.6 Critérios de aceite da camada

1. Nenhum teste unitário depende de Android/Bluetooth/carro.
2. 100 % dos decoders da tabela §4.2 têm teste com pelo menos 2 vetores (mínimo e típico) e um vetor de borda (0x00, 0xFF, tamanho errado).
3. O parser rejeita (sem crash) todas as 14 injeções acima.
4. `ElmSession` nunca envia dois comandos simultâneos — comprovar com teste que dispara 20 `execute` concorrentes e verifica a ordem serial no transport fake.
5. Reconexão: teste com 6 falhas seguidas verifica delays crescentes com jitter dentro da faixa e desiste no limite.
6. `PidScheduler` respeita período/prioridade e registra `skipped` quando estoura orçamento.
7. Transcript de boot completo reproduzido byte a byte (sequência de comandos idêntica à §2.1).

---

## 8. URLs consultadas

Fontes primárias (spec/datasheet/código):

1. ELM327 datasheet oficial (94 páginas, texto extraído integralmente): https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf
2. ELM327 AT Command Table oficial (versão em que cada comando surgiu): https://cdn.sparkfun.com/assets/4/e/5/0/2/ELM327_AT_Commands.pdf
3. Wikipedia — OBD-II PIDs (tabela J1979, Service 01/02/03/09, bitmasks, DTC, PID 01): https://en.wikipedia.org/wiki/OBD-II_PIDs
4. Wikipedia — ELM327 (protocolos suportados, histórico de versões, clones): https://en.wikipedia.org/wiki/ELM327
5. Wikipedia — ISO 15765-2 / ISO-TP (SF/FF/CF/FC, PCI, STmin): https://en.wikipedia.org/wiki/ISO_15765-2
6. Wikipedia — Data Link Connector (J1962, Type A/B): https://en.wikipedia.org/wiki/Data_link_connector
7. Wikipedia — List of Bluetooth profiles (SPP, UUID): https://en.wikipedia.org/wiki/List_of_Bluetooth_profiles
8. python-OBD — `obd/decoders.py` (fórmulas reais): https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/decoders.py
9. python-OBD — `obd/commands.py` (tabela de comandos/tamanhos): https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/commands.py
10. python-OBD — `obd/elm327.py` (sequência de boot, auto-baud, ATSP0/ATTP, clean de resposta): https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/elm327.py
11. python-OBD — `obd/protocols/protocol_can.py` (7E8/7DF, PCI, multi-frame): https://raw.githubusercontent.com/brendan-w/python-OBD/master/obd/protocols/protocol_can.py
12. kotlin-obd-api — `ObdDeviceConnection.kt` (mutex, read policy, prompt): https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/connection/ObdDeviceConnection.kt
13. kotlin-obd-api — `RegexPatterns.kt` (SEARCHING/NO DATA/STOPPED/7F…): https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/command/RegexPatterns.kt
14. kotlin-obd-api — `Enums.kt` (protocolos, adaptive timing, monitores): https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/command/Enums.kt
15. kotlin-obd-api — `TroubleCodes.kt` (03/07/0A, multi-frame DTC): https://raw.githubusercontent.com/eltonvs/kotlin-obd-api/master/src/main/kotlin/com/github/eltonvs/obd/command/control/TroubleCodes.kt
16. obd-java-api — README (sequência de recuperação ATD/ATZ/…): https://raw.githubusercontent.com/pires/obd-java-api/master/README.md
17. ISO 15765-4:2021 (escopo): https://www.iso.org/standard/78384.html
18. Android — `BluetoothGatt` (requestMtu/getMtu, notificações): https://developer.android.com/reference/android/bluetooth/BluetoothGatt

Fontes secundárias (comportamento de campo, UUIDs, portas):

19. ELM327 commands/pinout/protocolos: https://elm327obd2.com/elm327-commands/
20. Wi-Fi 192.168.0.10:35000 (Car Scanner): https://www.carscanner.info/wifi/
21. Wi-Fi 192.168.0.10:35000 (manual Interfuse): https://interfusellc.com/elm327/manual/wifi-windows/
22. HM-10 FFE0/FFE1: https://www.tinkered.ai/components/hm-10
23. HM-10 UUIDs longos (StackExchange): https://electronics.stackexchange.com/questions/323071/send-custom-gatt-characteristics-using-hm-10-actually-a-cc2541
24. Manual HM-10 (service FFE0/FFE1): https://manuals.plus/m/20769fb4e4aa5f2aeb37ccb86a04a4207e6de47e12bdb3239a614015b9bd010c

> O documento do EPA (`https://www.epa.gov/sites/default/files/2016-04/documents/420f14032.pdf`) não pôde ser lido nesta sessão (o `https://www.epa.gov/obd` redireciona para a página de transporte estadual). Nada neste documento depende dele: as definições de J1979 usadas vieram da tabela pública da Wikipedia e dos decoders do python-OBD.

---

## 9. Lacunas e o que falta validar no carro

### 9.1 Lacunas declaradas

| # | Assunto | Status | Como fechar |
|---|---|---|---|
| L1 | Protocolo do Yaris 2021 BR = CAN 11/500 (`ATSP6`) | `[HIPÓTESE]` forte | `ATSP0` + `0100` + `ATDPN` (§1.3) |
| L2 | Formato textual exato do multi-frame do ELM (`014` + linhas `0:`) | `[HIPÓTESE]` | `0902` com `ATCAF1`/`ATH0` e inspeção do bruto |
| L3 | "Truncamento em 32 bytes" de clones | `[NÃO CONFIRMADO]` | teste `0902`/`0904` em cada dongle; medir tamanho recebido |
| L4 | Portas Wi-Fi alternativas (2000/23/8080) | `[HIPÓTESE]` | testar a lista no dongle real |
| L5 | UUIDs BLE além de FFE0/FFE1 | `[HIPÓTESE]` | `discoverServices()` e logar UUIDs |
| L6 | Latências (15–40 ms etc.) | `[HIPÓTESE]` | medir p50/p95 com `SessionStats` em 3 dongles |
| L7 | Suporte a `0x5E` (fuel rate) no Yaris | `[NÃO CONFIRMADO]` | `015E` |
| L8 | Quantidade de sensores O₂ (0x14/0x15) | `[NÃO CONFIRMADO]` | `0113`/`0114`/`0115`/`011C` |
| L9 | Presença de byte de contagem no Modo 07 | `[HIPÓTESE]` | `07` no carro e comparar com o parser tolerante |
| L10 | Múltiplos PIDs por requisição (`010C0D`) | `[HIPÓTESE]` | teste A/B |
| L11 | Convivência USB + Android Auto via hub | `[HIPÓTESE]` | teste físico (baixa prioridade) |
| L12 | Semântica exata de `0x11` (TPS) vs `0x49` no Toyota | `[NÃO CONFIRMADO]` | comparar valores em marcha lenta/WOT |

### 9.2 Checklist de validação no carro (30–40 min, motor quente)

Objetivo: transformar as lacunas em fatos e gravar o **transcript de ouro** do veículo.

1. **Conexão física**: plugar o dongle, ligar ignição (motor desligado), parear/associar.
2. **Boot**: `ATZ` → anotar string do `ATI`; `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATAT1`, `ATST 32`.
3. **Protocolo**: `ATSP0`; `0100` (cronometrar o `SEARCHING...`); `ATDP` e `ATDPN` → **anotar**.
4. **Fixar**: `ATSP6`; `0100`; `ATDPN` deve retornar `6` (sem `A`).
5. **Headers**: `ATH1`; `0100` → confirmar `7E8`; `010D` e `010C` com headers.
6. **Bitmask**: `0120`, `0140`, `0160` (só se o anterior indicar suporte) → anotar conjunto completo.
7. **PIDs**: `0104 0105 010B 010C 010D 010F 0110 0111 0114 0115 012F 0133 0142 015C 015E 011F 0131 010E` → anotar hex e decodificar à mão 2–3.
8. **Multi-frame**: `0902` (VIN), `0904`, `0906`, `090A`; cronometrar; verificar se veio `:`/`014`.
9. **DTC**: `0101` (MIL/readiness), `03`, `07`, `0A` → anotar.
10. **Tensão**: `AT RV` com motor ligado (esperado 13,5–14,4 V) e desligado.
11. **Carga**: abrir a tela de consumo e medir latência p95 por 2 min (`SessionStats`) → ajustar `periodMs`.
12. **Stress**: deixar rodando 10 min, anotar reconexões/timeouts; simular desligar o carro e religar.
13. **Salvar** o transcript completo em `transcripts/yaris-2021-real.jsonl` e usar como golden test.
14. **Repetir** o teste de protocolo com o motor frio (ECU pode responder diferente no primeiro minuto).

### 9.3 Ordem de implementação recomendada

1. `Transport` + `SppTransport` + `SerialElmSession` + `ResponseParser` (com testes unitários das injeções §7.5).
2. Emulador TCP + transcript de boot (§7.2/7.3) — **antes** de tocar no carro.
3. `ElmBootstrapper` + `AdapterProbe` + descoberta de protocolo.
4. `Decoders` dos PIDs §4.2 (com vetores de teste) + `parseSupported`.
5. `PidScheduler` + `SampleStore` + política `STALE`.
6. `VehicleProfile` (cache VIN/PIDs/protocolo) + `ConnectionManager` (backoff).
7. Diagnóstico: `03/07/0A`, `02`, `09`, `01 01`.
8. Só então UI de painel e a tela de diagnóstico.

### 9.4 Apêndice — referência rápida de comandos

| Objetivo | Comando |
|---|---|
| Reset | `ATZ` (ou `ATWS`) |
| Silenciar eco/linhas/espaços | `ATE0`, `ATL0`, `ATS0` |
| Headers | `ATH0` / `ATH1` |
| Protocolo automático / fixo / descoberto | `ATSP0` / `ATSP6` / `ATSPA6` |
| Ver protocolo | `ATDP` / `ATDPN` |
| Timeout | `ATST 32` (×4 ms) |
| Adaptive timing | `ATAT1` |
| Formatação CAN | `ATCAF1`, `ATCFC1` |
| Filtrar ECU | `ATCRA 7E8` (reset: `ATAR`) |
| Tensão | `AT RV` |
| RPM | `010C` |
| Velocidade | `010D` |
| Coolant | `0105` |
| Carga | `0104` |
| TPS | `0111` |
| MAP | `010B` |
| IAT | `010F` |
| MAF | `0110` |
| O₂ B1S1 | `0114` |
| Nível | `012F` |
| Baro | `0133` |
| Tensão módulo | `0142` |
| Óleo | `015C` |
| Fuel rate | `015E` |
| Suportados 01–20 | `0100` |
| Status/MIL | `0101` |
| DTCs confirmados/pendentes/permanentes | `03` / `07` / `0A` |
| Limpar DTCs | `04` (**com confirmação**) |
| Freeze frame (RPM, frame 0) | `020C00` |
| VIN | `0902` |
| Calibration ID | `0904` |
| CVN | `0906` |
| Nome do ECU | `090A` |

---

**Fim do documento.** Este é o contrato da camada OBD2/ELM327; qualquer implementação deve conseguir passar nos critérios do §7.6 sem alterar as fórmulas do §4.2 e sem violar as regras de concorrência do §6.8.
