# Simulador de scanner OBD2 (adaptador ELM327 virtual)

Ferramenta de desenvolvimento para testar o app **com o protocolo real**, antes de o scanner físico chegar.

A ideia central: em vez de injetar números falsos direto na tela, o app conversa com um **adaptador ELM327 emulado**, que responde ao protocolo de verdade (texto ASCII, hexadecimal, `NO DATA`, prompt `>`), alimentado por um **modelo de veículo em tempo real**. Assim, o mesmo código que vai ler o carro é o código que está sendo testado.

```
Modelo do veículo → Emulador ELM327 → transporte → ElmSession → decodificação → painel
   (20 Hz)            (protocolo)      (pipe)       (mutex)      (J1979)
```

## Como usar

1. Abra o app no celular. A tela inicial é o painel de controle do carro virtual.
2. Toque em **Ligar simulador**. O app executa a inicialização real (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`), descobre os PIDs suportados (`0100/0120/0140/0160`), lê o VIN (`0902`) e começa o laço de leitura.
3. O campo de estado mostra adaptador, protocolo negociado e quantos PIDs a "ECU" declarou.
4. **Dirija**: deixe **Ciclo de condução automático** ligado (aceleração → cruzeiro → frenagem → marcha lenta) ou arraste o **acelerador manual**.
5. **Diagnóstico**: os botões *Ler DTCs* e *Limpar DTCs* explicam o que fazem e pedem **confirmação em duas etapas** antes de executar (detalhes abaixo).
6. **No carro**: abra no Android Auto. O painel mostra os mesmos valores, com o aviso **ADAPTADOR VIRTUAL — DADOS SIMULADOS**.
7. **Console**: mostra cada comando enviado (`>>`) e a resposta crua (`<<`), no formato do adaptador.

### Diagnóstico: duas etapas obrigatórias

Não existe botão para **fabricar** falha. Injetar DTC foi removido da interface de propósito: um código falso na tela é indistinguível de um defeito real para quem está dirigindo.

O que existe são duas ações, e **ambas** seguem o mesmo fluxo:

1. **Aviso e decisão** — um diálogo explica o que a operação faz e quais são os impactos; o usuário escolhe *Continuar* ou *Cancelar*.
2. **Segunda confirmação** — um segundo diálogo, específico da ação, pede a confirmação final. **Só depois dele o comando é enviado à ECU.**

| Ação | O que faz | Impactos informados ao usuário |
|---|---|---|
| **Ler DTCs** | Modo 03 (confirmados) e Modo 07 (pendentes); mostra códigos e estado da MIL | Nenhum: é somente leitura, não altera injeção nem apaga memória |
| **Limpar DTCs** | Modo 04: apaga confirmados, pendentes, freeze frame e monitores de prontidão | **Única escrita na ECU**: não conserta o defeito, zera prontidão (pode reprovar inspeção), apaga o histórico e só pode ser feito com o veículo parado |

Travas adicionais implementadas:

- **Sem conexão**, nenhuma das duas ações executa: aparece um aviso explicando que não há o que ler na ECU.
- **Veículo em movimento**: se a velocidade lida for maior que 1 km/h, a limpeza é bloqueada com a explicação do motivo, antes de qualquer confirmação.

### Automação (adb)

```powershell
# Liga o simulador e grava as prévias do painel depois da conexão
adb shell am start -n com.pedro.yarisobd/.MainActivity --ez start_sim true --ez export_preview true

# Só as prévias
adb shell am start -n com.pedro.yarisobd/.MainActivity --ez export_preview true
```

## O que dá para testar de verdade

| Área | O que é exercitado |
|---|---|
| Inicialização | Reset, desligar eco, protocolo automático e fallback para `ATSP6` |
| Descoberta de PIDs | Máscaras `0100/0120/0140/0160` decodificadas bit a bit |
| Decodificação | Fórmulas do J1979 conferidas contra o valor do modelo |
| Laço de leitura | Rodízio de PIDs rápidos/lentos e orçamento de comandos por segundo |
| Latência | Cada comando custa 12 ms no "adaptador" (clone real é mais lento) |
| Erros | `NO DATA` para PID não suportado, `?` para comando inválido |
| Diagnóstico | Modo 03 (confirmados), 07 (pendentes), 04 (limpar), PID 0101 (MIL + contagem) |
| Painel | Atualização em tempo real com dados que passaram pelo protocolo |
| Interface | Aviso obrigatório de dados simulados |

## O que **não** é fiel (e não deve ser tratado como prova)

- **Formato multi-frame do VIN**: o layout `0:`, `1:` foi implementado a partir da documentação, mas **não foi validado contra um ELM327 físico**. Pode diferir em detalhes.
- **Não há barramento**: não existe arbitragem CAN, colisão, erro de ISO-TP, `BUS INIT`, nem adaptador ruim de verdade.
- **Clones incompatíveis**: o caso real mais comum (clone v2.1 que conecta e não lê a ECU) é simulado apenas pela mensagem de falha, não pelo comportamento elétrico.
- **Consumo**: o tanque baixa com AFR fixo só para dar realismo; não é uma estimativa de consumo do carro.
- **O modelo não é o Yaris**: é um carro flex genérico plausível. Nenhuma curva foi medida no seu carro.
- **A central não é testada**: isso continua dependendo do teste físico no Android Auto.

## Por que isso importa para o projeto

O caminho perigoso num app OBD2 é o intermediário: um erro de fórmula, de máscara de PID ou de sincronismo produz um número plausível e falso. Testar só a tela (com valores injetados) esconde exatamente esses erros. Aqui, `010C` → `410C226D` → 2204 rpm é verificado em cada elo, e os testes automatizados cobrem as fórmulas uma por uma.

## Mapa do código

| Arquivo | Papel |
|---|---|
| `sim/VehicleSimulator.kt` | Modelo do carro: velocidade, marchas, rpm, aquecimento, consumo, DTCs |
| `sim/Elm327Emulator.kt` | Protocolo ELM327: comandos AT, Modos 01/03/04/07/09, máscaras, formatação |
| `obd/ObdTransport.kt` | Interface de transporte + adaptador virtual + fila em memória |
| `obd/ElmSession.kt` | Conversa com o adaptador: mutex, timeout, leitura até o prompt `>` |
| `obd/Pids.kt` | Fórmulas de decodificação (SAE J1979) |
| `obd/ObdConnection.kt` | Inicialização, descoberta de PIDs, laço de leitura, DTCs |
| `obd/ConsoleLog.kt` | Buffer do console de comandos |
| `sim/SimulatorController.kt` | Liga/desliga e expõe controles para a interface |

## Testes

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

- `PidsTest` (10): cada fórmula de decodificação, valor a valor.
- `Elm327EmulatorTest` (13): eco, prompt, espaços, máscaras, `NO DATA`, modos 03/04, VIN, motor desligado.
- `VehicleSimulatorTest` (10): acelera, freia, aquece, marchas não oscilam, consumo plausível, falhas.
- `DtcCodesTest` (8): codificação das famílias P/C/B/U e ida-e-volta.
- `GaugeMathTest` (9): escalas, ângulos, indisponível ≠ zero, dado velho.
- `ObdIntegrationTest` (5): **a cadeia inteira**, sem Android — handshake, PIDs descobertos, laço de leitura publicando valores, leitura/limpeza de DTC e comparação numérica entre o que o painel mostra e o estado do modelo.

Total: **55 testes**, todos rodando em JVM (`.\gradlew.bat :app:testDebugUnitTest`), sem emulador nem dispositivo.

## Próximo passo natural

Quando o scanner chegar, o único código novo é um `ObdTransport` de Bluetooth (Classic SPP ou BLE) — a especificação está em [`docs/especificacao-obd2-elm327.md`](especificacao-obd2-elm327.md). O resto do caminho já estará testado por este simulador.
