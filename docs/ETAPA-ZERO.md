# Etapa Zero — Relatório consolidado de viabilidade

**Projeto:** painel OBD2 para Toyota Yaris 2021 (BR), exibido na central original via Android Auto, instalação privada por APK, sem publicação na Play Store.
**Data:** 12/11/2026
**Regra de leitura:** `[CONFIRMADO]` = eu li a fonte primária (documentação oficial, código-fonte, manifesto de repositório ou o próprio aparelho); `[RELATO]` = relato de usuário/fórum, não é especificação; `[HIPÓTESE]` = inferência técnica sem prova; `[NÃO CONFIRMADO]` = não existe fonte ou o teste ainda não foi feito.

**Como este relatório foi produzido:** três agentes de pesquisa trabalharam em paralelo (Android Auto; OBD2/ELM327; Yaris 2021), com instrução explícita de citar fonte por afirmação e de marcar o que não conseguiram confirmar. Depois eu conferi pessoalmente os pontos que decidem o projeto (documentação oficial do Android Auto, código-fonte da Car App Library 1.7.0, manifestos dos repositórios), questionei o Agente 1 quando a conclusão dele conflitou com evidência de campo, e compilei um APK real de prova de conceito neste computador. As divergências e como foram resolvidas estão na seção 11.

---

## 0. Resposta direta

1. **É possível exibir um painel OBD2 próprio na central do Yaris via Android Auto?** Sim, tecnicamente — mas por um caminho estreito, não oficial e que depende de um teste que só a sua central pode dar.
2. **Sideload comum de APK não funciona.** Está escrito na documentação do Google e confirmado no código do Android Auto: a opção "Fontes desconhecidas" cobre apps de mídia, notificações e "parked" — **não** cobre apps feitos com a Car App Library.
3. **O desenho livre (gauges de verdade) existe**, e só num lugar: na `Surface` que o host entrega para templates de mapa (`ACCESS_SURFACE`). Em template comum não há Canvas, não há View customizada — só texto, ícone, lista e grade.
4. **A porteira não é a Play Store em si: é o registro de instalação.** O Android Auto verifica se o app parece ter sido instalado pelo instalador de pacotes do sistema a pedido da Play. Isso pode ser produzido fora da Play com ferramenta open source (Shizuku), e é o que explica o único app que encontrei com gauges OBD2 funcionando em central real.
5. **O que não posso afirmar:** que a sua central específica vai listar o app. Isso depende de um teste físico de 10 minutos, e o simulador oficial (DHU) **não serve como filtro** — ele pula justamente a checagem que a central faz.

> ### ✅ Comprovado no veículo em 13/11/2026
>
> O app **apareceu na grade de apps da central original do Yaris 2021, abriu e desenhou o painel** com os dados do adaptador virtual.
>
> A previsão deste relatório se confirmou ponto a ponto: a instalação por ADB foi recusada silenciosamente (`initiatingPackageName=com.android.shell`) e só funcionou depois de instalada com `initiatingPackageName=com.google.android.packageinstaller` e `installerPackageName=com.android.vending`, via Shizuku + UnknownAuto.
>
> Receita completa e medições: [`docs/instalacao-android-auto.md`](instalacao-android-auto.md).

> ### ✅ Comprovado em campo com scanner físico em 18/11/2026
>
> O app rodou na central e **conversou com o scanner OBD2 real**, exibindo dados do veículo — não mais simulação. Isso encerra as três incertezas que a Etapa Zero não podia resolver sozinha: o caminho de exibição, a camada de transporte e a decodificação dos PIDs.
>
> O que passou a estar comprovado de ponta a ponta: instalação aceita pelo Android Auto → template + Surface desenhando na central → transporte Bluetooth → sessão ELM327 → decodificação J1979 → painel.

---

## 1. Hardware verificado neste projeto

Verificado por mim, com `adb`, nos aparelhos conectados a este computador.

| Item | Situação | Consequência |
|---|---|---|
| **Redmi Note 13 Pro** (`2312DRA50G`) | Android 16 (API 36), arm64-v8a, **Android Auto 18.0.664264** instalado `[CONFIRMADO — adb]` | É o aparelho viável. Acima de qualquer requisito de versão. |
| **Samsung Galaxy J4 Core** (`SM-J410G`) | Android 8.1 (API 27), `ro.config.low_ram=true`, 858 MB de RAM, armeabi-v7a, **sem Android Auto** `[CONFIRMADO — adb]` | **Inviável.** Android Auto exige Android 9+ na documentação atual `[CONFIRMADO: developer.android.com/training/cars/platforms/android-auto]` e, na prática, as versões atuais do app pedem Android 11+. Um aparelho Android Go de 2016 não roda projeção de forma utilizável. |
| **Central do Yaris** | Harman **PV602 / BE2841**, 7", firmware proprietário **BR_18.x**, **não é Android** `[CONFIRMADO — pesquisa Agente 3 + fontes]` | Não há como instalar nada nela. "Android Auto" nesse carro é projeção por cabo USB. |
| **Scanner** | ELM327 comprado, ainda não entregue `[informado pelo usuário]` | A camada OBD2 será implementada e testada contra simulador até o adaptador chegar. |

Confirmação importante dada pelo usuário: **o Android Auto já funciona hoje na central dele**. Isso remove o maior risco do projeto (central sem projeção) e reduz a Etapa Zero a uma pergunta única: *o app aparece na grade do carro?*

---

## 2. O que o Android Auto permite (verificado na documentação oficial)

### 2.1 É projeção, não instalação

O app roda **no celular**. A central é tela e entrada. O app não desenha na tela do carro: ele envia *templates* serializados, e o app Android Auto no celular renderiza.
`[CONFIRMADO: developer.android.com/training/cars/platforms/android-auto]`

Três consequências que definem a arquitetura:

- o scanner OBD2 tem de conversar com o **celular** (por isso Bluetooth; a porta USB está ocupada pela projeção);
- o app precisa continuar vivo com a tela do celular apagada → serviço em foreground quando houver coleta;
- Android Automotive OS (carro com Android embarcado) é **outra plataforma** e não é o caso do Yaris.

### 2.2 Categorias existentes — não existe "painel" nem "telemetria"

Constantes reais da biblioteca `androidx.car.app`, lidas do código-fonte `[CONFIRMADO: androidx.car.app 1.7.0, CarAppService.java]`:

`NAVIGATION`, `POI`, `IOT`, `WEATHER`, `PARKING`, `CHARGING`, `SETTINGS`, `MESSAGING`, `CALLING`.

Não há categoria para "dashboard", "OBD" ou "telemetria". O encaixe menos artificial é **IOT**; a categoria com maior liberdade gráfica é **NAVIGATION**, por ser a que a documentação associa ao desenho em `Surface`.

### 2.3 Templates: o que dá e o que não dá

- Quota: **no máximo 5 templates por tarefa**, e o último precisa ser de um tipo específico; estourar faz o host fechar o app `[CONFIRMADO: .../library/template-restrictions]`.
- Conteúdo de template: texto, ícones (`CarIcon` sobre `IconCompat`), listas, grades, panes, mensagens, botões. **Não existe View customizada nem Canvas dentro de template.**
- Atualização: `Screen.invalidate()` invalida o conteúdo, mas não há taxa garantida — a documentação só diz que refreshes "podem ser limitados" durante a condução, **sem nenhum número publicado** `[CONFIRMADO a existência da limitação / NÃO CONFIRMADO o valor]`.

Conclusão parcial: um painel de gauges com agulha, arco e atualização contínua **não cabe** no modelo de template.

### 2.4 A única brecha de desenho livre: `ACCESS_SURFACE`

Texto da documentação `[CONFIRMADO: developer.android.com/training/cars/apps/library/draw-maps]`:

> "Navigation, point of interest (POI), and weather apps using the following templates can draw maps by accessing a Surface."

| Template | Permissão exigida | Categoria associada |
|---|---|---|
| `NavigationTemplate` | `androidx.car.app.NAVIGATION_TEMPLATES` | Navigation |
| `MapWithContentTemplate` | `NAVIGATION_TEMPLATES` **ou** `MAP_TEMPLATES` | Navigation, POI, Weather |
| `MapTemplate` (obsoleto) | `NAVIGATION_TEMPLATES` | Navigation |

E, além da permissão do template, `androidx.car.app.ACCESS_SURFACE`. Uma vez com a `Surface`, a documentação permite **duas** formas de desenhar:

1. direto no `Canvas`; ou
2. **qualquer `View` — inclusive `ComposeView` — via `VirtualDisplay` + `Presentation`** `[CONFIRMADO: mesma página, seção "Use a virtual display to render content"]`.

E o host informa as áreas visível e estável (`onVisibleAreaChanged`, `onStableAreaChanged`), porque ele desenha o chrome do template por cima.

Isto é o mecanismo que torna o projeto possível: um conta-giros desenhado à mão, atualizando a 10-12 Hz, é exatamente o tipo de coisa que só cabe aqui.

**Cerca importante:** as regras de qualidade do Google dizem que a surface deve conter "mapa e conteúdo relacionado à direção" (navegação) ou "mapa e clima" (weather) `[CONFIRMADO: quality guidelines / ux-requirements]`. Para uso pessoal, sem revisão da Play, esse limite é editorial e não técnico — mas é a razão pela qual esse caminho não seria aprovado num app comercial. **Este projeto é pessoal e não será publicado**, então a restrição não bloqueia.

### 2.5 A porteira real: "fonte confiável"

Texto oficial, decisivo `[CONFIRMADO: developer.android.com/training/cars/testing]`:

> "To test your app in real vehicles, you must install it from a trusted source such as Google Play, with one exception (...). This setting [unknown sources] applies to media, messaging notifications, and parked apps **but doesn't apply to apps built using the Android for Cars App Library**."

E as fontes confiáveis são **Google Play** e **ONE store** `[CONFIRMADO: .../platforms/android-auto]`.

Ou seja: **"ativar Fontes desconhecidas no modo desenvolvedor" não resolve** para um app de template. Foi essa a conclusão que o Agente 1 trouxe e que eu confirmei na letra da documentação.

**Como o validador decide** (engenharia reversa publicada, Android Auto 17.7) `[CONFIRMADO: github.com/AndreyPavlenko/Fermata/issues/846]`:

1. lê `getInstallSourceInfo(pkg)`;
2. usa `getInitiatingPackageName()` e **só se** o iniciador for `com.google.android.packageinstaller` consulta `getInstallingPackageName()`;
3. depois: blocklist → capability no `automotive_app_desc` → *developer head unit* (pula tudo) → "Play responde por ele" → instalador `com.android.vending` assinado pelo Google → allowlist → Fontes desconhecidas → **negado**.

Duas consequências práticas e não óbvias:

- **`adb install -i com.android.vending` não engana o Android Auto.** O `-i` grava o instalador, mas o iniciador de um install por shell é o próprio shell. Precisa ser o instalador do sistema iniciando a instalação com `EXTRA_INSTALLER_PACKAGE_NAME` + `EXTRA_NOT_UNKNOWN_SOURCE` — é o que Shizuku + UnknownAuto/AAEnabler fazem.
- **O DHU (Desktop Head Unit) não serve como filtro.** Ele é um *developer head unit*, e nesse caso o Android Auto **pula todas as checagens de instalador** — ou seja, o DHU mostra exatamente o app que a central real vai negar. Ele serve para validar desenho e manifesto, não distribuição.

---

## 3. Rotas para exibir sem publicar na Play Store

Classificação pedida: **viável e comprovada**, **viável com restrições**, **experimental**, **inviável**.

| # | Rota | Como funciona | Conta Play? | Risco | Classificação |
|---|---|---|---|---|---|
| R1 | **Internal App Sharing / trilha interna** | Sobe o build no Play Console e instala por link; a central vê como instalação da Play; **sem revisão** (`Internal sharing → None`) `[CONFIRMADO: .../distribute]` | **Sim** (conta de desenvolvedor) | Baixo tecnicamente; exige cadastro e verificação de identidade | **Viável e comprovada** (rota oficial) |
| R2 | **Marcador de instalação via Shizuku** — UnknownAuto (MIT, ativo) ou AAEnabler | Instala pelo instalador do sistema marcando a origem; o validador do AA aceita | **Não** | Não oficial: pode quebrar em update do AA; ferramenta de terceiro | **Viável com restrições** (e é a rota sem custo) |
| R3 | **SDK de projeção legado** (`CarActivity`, `CATEGORY_PROJECTION_OEM`) | Activity nativa inteira na tela, sem template e sem quota | Não (mas depende de R2 ou R1 para instalar) | SDK não oficial, fork parado desde 2019; PROJECTION é tipo negado pelo "Fontes desconhecidas" | **Experimental** |
| R4 | **Espelhamento** (AAAD, Screen2Auto, AA Mirror, Fermata) | Reproduz a tela do celular inteira | Não | Instável, quebra a cada update, APKs de origem duvidosa, sem repo oficial dos principais | **Experimental** |
| R5 | **Central Android aftermarket** | Troca a central; app roda nela, sem Android Auto | Não | Custo; deixa de ser "central original" — **foge do requisito** | Fora do escopo (contingência) |
| — | Sideload comum (adb/gerenciador de arquivos) | — | — | — | **Inviável** `[CONFIRMADO]` |
| — | View/Canvas dentro de template | — | — | — | **Inviável** (não existe API) |
| — | Publicar na Play Store | — | Sim | Contraria o requisito | Descartado por decisão do projeto |

**Recomendação:** PoC pelas rotas **R2 + desenho em `Surface`**, com fallback de template simples. R1 fica como plano A oficial se você aceitar criar conta de desenvolvedor; é a única rota que o Google reconhece.

---

## 4. Projetos open source relevantes (verificados por mim na API do GitHub)

| Repositório | Estado | O que ele prova | O que ele NÃO prova |
|---|---|---|---|
| `borconi/obd2aa` (139★) | Parado desde 2022 | Que a combinação **Car App Library + `NAVIGATION_TEMPLATES` + `ACCESS_SURFACE`** foi usada por um app OBD2 real; manifesto lido por mim | Que ainda funcione no AA 18 |
| `Le-F-Sur-GitH/Performance-Monitor-AAOBD` (GPLv3) | **Ativo** (push 2026-09-22) | **A melhor evidência de campo:** gauges OBD2 na tela de uma central VW real, com LE/BT Classic, reconexão e fallback para clones; instruções explícitas de instalação via AAEnabler | Não usa Car App Library (usa o SDK de projeção legado); não testei no meu hardware |
| `GitMag/UnknownAuto` (248★, MIT) | **Ativo** (release nov/2026) | Que o problema é o **registro de instalação** e que ele pode ser produzido por Shizuku, offline, sem conta Google | Ainda não testei no Redmi |
| `malebuffy/AAEnabler` (219★) | Ativo (2026-11-06) | Mesma função, outra implementação | idem |
| `CalmAussie/obd2-reader` | Ativo | App IOT com 13 estilos de painel e Android Auto | Tem **issue #8 aberta (11/11/2026): "Android 16 app not appearing in android auto"** — mesma dúvida nossa |
| `bobphrapples/IoniqVitals` | Ativo | Comentário no manifesto do autor: sideload só lista via media browser; "IOT + spoof de instalador tentado duas vezes, ambas DENIED" | Contradiz parcialmente a R2 — por isso o teste é obrigatório |
| `AndreyPavlenko/Fermata` issues #829/#846 | Ativo | **documentam o validador do AA 17.7 com logs** | É relato de terceiro, não documentação |

Leitura honesta do conjunto: **existe prova de que um painel OBD2 já apareceu numa central real** (Performance-Monitor), mas cada relato aponta para um Android Auto diferente (17.7, 18.0, Android 16), e o único projeto com issue aberta sobre Android 16 é justamente um app IOT com AA. Não existe garantia — existe um teste barato.

---

## 5. O Toyota Yaris 2021 brasileiro (síntese do Agente 3)

Separando o confirmado do provável, porque aqui é onde mais se inventa:

**Confirmado**

- Plataforma **XP150**, fabricado em Sorocaba; **não** é o Yaris europeu (XP210/TNGA-B). Nada de PID, firmware ou central do europeu vale automaticamente.
- Ano-modelo 2021: **1.3 flex `1NR-FBE`** (hatch XL Live, manual de 6 marchas ou CVT) e **1.5 flex `2NR-FBE`** (demais versões, CVT Aisin K411).
- **Existe versão sem central touchscreen** (XL Live): só rádio/CD/BT/USB, sem Android Auto. A linha "Connect" tem central 7" **Harman PV602 / BE2841**, proprietária, com **Android Auto por cabo**.
- Conector OBD2 padrão J1962 sob o painel, junto à alavanca do capô `[RELATO convergente, 2 fontes]`.
- Leitura de DTC funciona nesse carro (exemplo real citado: P1605) `[RELATO]`.

**Provável / a confirmar no carro**

- Protocolo **ISO 15765-4 CAN 11 bits 500 kbps**. Não existe captura pública de `ATDPN` do Yaris 2021 BR — o app deve **logar e exibir o `ATDPN`** na primeira conexão para fechar isso com dado do próprio veículo.
- `0x2F` (nível de combustível) e `0x42` (tensão do módulo) suportados; `0x5E` (vazão de combustível) **provavelmente ausente**.

**Onde a maioria dos projetos erra**

- **Não existe nenhum mapa público de PIDs proprietários Toyota (Modo 21/22) para as famílias 1NR/2NR.** Nada de temperatura de CVT, knock ou status de injeção documentado para este carro. Qualquer material que afirme isso está inventando. O que existe (2101–214E espelhando o Modo 01) é documentado para outra família de motor (1KD-FTV).
- **Achado muito acionável:** dois relatos independentes de que **clones ELM327 v2.1 conectam mas não leem a ECU deste carro**, funcionando o **v1.5 com PIC18F25K80**. O app deve diagnosticar o adaptador ("conecta mas não responde à ECU — troque o adaptador"), em vez de culpar o carro.

**Consumo e flex — a parte mais útil**

- Assumir AFR 14,7 num flex brasileiro erra feio: com E100 o estequiométrico é ~9,0. Erro de ~39% na estimativa por MAF.
- **Hipótese forte e testável:** donos relatam ler **% de etanol** via OBD. A escala bate com o PID **padrão `0x52`** (`A*100/255`), e o comportamento "só atualiza depois de 10-20 km" é assinatura do aprendizado de combustível. Se confirmar, o AFR passa a ser **calculado** (E27 ≈ 13,3; E100 ≈ 9,0) e o erro da estimativa cai para poucos por cento.
- Hierarquia de rótulos obrigatória: tanque-a-tanque = **MEDIDO**; MAF + `0x52` = **ESTIMATIVA**; MAF com AFR fixo = **ESTIMATIVA GROSSEIRA** (melhor nem mostrar km/L).

---

## 6. Arquitetura recomendada

```
                 ┌──────────────────────────────────────────┐
  ELM327         │  camada OBD2 (celular)                   │
  (BT Classic) ──┤  Transport → ElmSession → PidScheduler   │
                 │           → Decoder → VehicleProfile     │
                 └──────────────────┬───────────────────────┘
                                    │ Telemetry (StateFlow)
                    ┌───────────────┴────────────────┐
                    │                                │
         ┌──────────▼──────────┐         ┌───────────▼───────────┐
         │ Android Auto        │         │ UI do celular         │
         │ SurfaceCallback     │         │ conexão, config,      │
         │ Canvas @ ~12 Hz     │         │ diagnóstico de DTC    │
         └─────────────────────┘         └───────────────────────┘
```

**Decisões e por quê**

1. **Bluetooth Classic (SPP) como transporte padrão**, com BLE como alternativa. Wi-Fi conflita com Android Auto sem fio; USB conflita com a projeção por cabo. O Agente 2 detalha o comparativo em `docs/especificacao-obd2-elm327.md`.
2. **Fila serializada no `ElmSession`.** O ELM327 não aceita dois comandos simultâneos; toda leitura passa por um mutex com timeout. Erro clássico de app amador é disparar PIDs em paralelo e receber `STOPPED`.
3. **Descoberta de PIDs em runtime** pelos bitmasks `01 00/20/40/60`. Nunca lista fixa — o Yaris não é obrigado a ter todos, e não sabemos quais tem.
4. **`null` = indisponível**, sempre. Zero é valor de motor legítimo; usar zero como "não tem" faz o painel mentir. Está codificado no tipo `Telemetry` e testado.
5. **Distinguir "dado velho" de "sem dado".** `Telemetry.isStale()` com limite de 3 s: conexão viva mas travada não pode continuar exibindo o último número como se fosse atual.
6. **Somente leitura no uso normal.** Nenhuma escrita na ECU sem ação explícita do usuário. Limpar DTC só com veículo parado, confirmação em duas etapas e explicação do que será apagado (freeze frame, readiness, adaptativos).
7. **Sem serviço em background desnecessário.** Sem internet, sem telemetria para servidor, sem analytics.
8. **Duas telas no carro:** painel (Surface) e diagnóstico (PaneTemplate). Se o host recusar o template de navegação, o app cai para um template de texto em vez de tela preta.
9. **Categoria configurável no build** (`-PcarCategory=...`), porque a combinação categoria↔template é o ponto não confirmado — e testar as duas variantes no carro custa um build.

---

## 7. Tecnologias

| Item | Escolha | Situação |
|---|---|---|
| Linguagem | Kotlin 2.2.20 | compilando |
| Build | Gradle 8.13 + AGP 8.13.2 | compilando (evitei AGP 9.x, novo demais) |
| SDK | compileSdk 36, targetSdk 36, minSdk 26 | compilando |
| UI no carro | `androidx.car.app:app:1.7.0` | compilando |
| Concorrência | kotlinx-coroutines | em uso |
| JDK | 17 (Temurin) | OK |
| Testes | JUnit4 (JVM) + roteiro físico | 9 testes passando |

APK gerado neste computador: `app/build/outputs/apk/debug/app-debug.apk` (4,4 MB). Manifesto conferido dentro do APK: serviço `ObdCarAppService` com ação `androidx.car.app.CarAppService`, categoria `androidx.car.app.category.IOT`, metadados `com.google.android.gms.car.application` e `androidx.car.app.minCarApiLevel`, permissões `ACCESS_SURFACE` e `NAVIGATION_TEMPLATES`.

---

## 8. Riscos e limitações

| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| Central não lista o app (validador de instalador) | **Alta** | Alto | R2 (UnknownAuto/AAEnabler) e, se falhar, R1 (Internal App Sharing) |
| Update do Android Auto quebra o caminho | Alta (crônico) | Médio | Fixar versão do AA; documentar reinstalação; opção R1 como rota estável |
| Categoria IOT não pode usar `NavigationTemplate` em runtime | Média / `NÃO CONFIRMADO` | Alto | Build alternativo com categoria `NAVIGATION`; fallback para template de texto |
| DHU dá falso positivo | Certa | Médio | Nunca tratar DHU como prova; só a central decide |
| Clone ELM327 não lê a ECU | **Alta** (2 relatos no Yaris) | Alto | Diagnóstico de adaptador no app; exigir v1.5 PIC18F25K80 |
| Estimativa de consumo errada no flex | Certa sem `0x52` | Médio | Rotular como estimativa; ler `0x52`; nunca exibir como medição |
| Limpar DTC apaga histórico/readiness | Certa | Alto | Só parado, confirmação dupla, texto do que será apagado |
| Painel a 10 Hz contraria regras de distração do Google | Certa | Baixo (uso pessoal) | Sem revisão da Play; uso pessoal declarado |
| Deixar scanner plugado 24 h | Baixa | Médio | Não deixar; relato de efeito no marcador de combustível `[RELATO]` |

---

## 9. Plano de implementação

| Fase | Entrega | Critério de aceite |
|---|---|---|
| **0. Viabilidade** ✅ | este relatório + APK de prova de conceito + pesquisa dos 3 agentes | feito |
| **1. Prova na central** ✅ | APK instalado via R2 e aberto na central do Yaris | **atingido em 13/11/2026:** app listado na grade, abriu e desenhou o painel |
| **2. Camada OBD2** ✅ | Transport BT Classic/BLE, `ElmSession`, bitmask, decoders, reconexão, emulador ELM327 | **atingido em 18/11/2026:** leitura real do veículo pelo scanner físico |
| **3. Painel** ✅ | painel no carro com valores reais, sem simulação | **atingido:** dados do scanner exibidos na central, com 12 layouts |
| **4. Diagnóstico** | DTC, descrição, MIL, freeze frame, limpeza com confirmação | leitura de DTC real; limpeza só parado |
| **5. Testes** | roteiro físico no Yaris + testes automatizados | checklist da seção 10.4 executado |
| **6. Entrega** | APK final + instruções + limitações | instalação reproduzível do zero |

---

## 10. Teste de conceito

### 10.1 O que já está pronto

O APK `app/build/outputs/apk/debug/app-debug.apk` já implementa:
- `CarAppService` + `Session` + `Screen` com `NavigationTemplate` e `SurfaceCallback`;
- painel desenhado em `Canvas` a ~12 Hz: velocidade, conta-giros com arco e zona vermelha, barras de água/acelerador/carga, rodapé com MAP, MAF, ar, bateria, combustível;
- aviso permanente e impossível de ignorar quando os dados são **simulação** ou estão **velhos**;
- tela de diagnóstico no carro mostrando resolução da superfície, quadros desenhados, fps e último erro — para você me dizer exatamente o que a central entregou;
- fallback para template de texto se o host recusar o template de navegação;
- 9 testes unitários cobrindo o que não pode quebrar (indisponível ≠ zero, saturação de escala, dado velho, plausibilidade da simulação).

**Nada disso é dado do veículo.** Enquanto o ELM327 não chega, o painel mostra `SIMULAÇÃO — SEM DADOS DO VEÍCULO` por cima de tudo. Foi a única forma honesta de validar o desenho e o caminho de projeção antes do hardware.

Renderização real, produzida por este código num Android 16 (não é mockup): [`docs/img/1920x720-dashboard-preview.png`](img/1920x720-dashboard-preview.png). Versões em 1280x720 e 800x480 na mesma pasta.

### 10.2 Teste A — a porteira (sem o carro, ~10 min)

> **Achado do ambiente:** no Redmi Note 13 Pro (HyperOS/Android 16) a instalação por ADB foi recusada com `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. Isso **não é problema do app**: é o ajuste "Instalar via USB" (Opções do desenvolvedor → Instalar via USB), que fica desligado por padrão nos Xiaomi e exige sua confirmação no aparelho. É o primeiro passo obrigatório no seu celular.

> **Medição real (12/11/2026), depois de liberar "Instalar via USB":** `adb install` concluiu com sucesso, e o registro de instalação ficou:
> `installerPackageName=null` · `initiatingPackageName=com.android.shell`
>
> Isso confirma, no aparelho de verdade, exatamente o que a pesquisa previa: **uma instalação por ADB deixa o registro como vinda do shell, e é esse registro que o Android Auto consulta**. Por isso o APK está instalado e funciona no celular, mas **não será listado na central** por essa via — é preciso refazer a instalação por UnknownAuto/AAEnabler (rota R2) ou Internal App Sharing (rota R1).

1. No Redmi: Opções do desenvolvedor → ligar **Instalar via USB** (e manter a depuração USB ligada).
2. Instalar o APK no Redmi pelo **UnknownAuto** (ou AAEnabler), via Shizuku.
3. Conferir o registro de instalação:
   ```
   adb shell dumpsys package com.pedro.yarisobd | Select-String "installerPackageName|initiatingPackageName"
   ```
   Precisa sair `installerPackageName=com.android.vending` **e** `initiatingPackageName=com.google.android.packageinstaller`. Se não sair, a rota R2 morreu nesse Android e vamos para R1.
4. Abrir Android Auto → **Personalizar launcher** e ver se "Yaris OBD" aparece e pode ser habilitado.

Se o app não aparecer nem no launcher, **não vale plugar no carro**.

### 10.3 Teste B — o desenho (DHU, opcional)

Só serve para ver o painel renderizando antes de entrar no carro. **Não valida a porteira** — o DHU pula as checagens. Se aparecer no DHU e não no carro, o problema é distribuição, não código.

### 10.4 Teste C — a central (é o que decide)

1. Cabo USB no Yaris, Android Auto abrindo normalmente.
2. Grade de apps → **Yaris OBD**.
3. Esperado: painel escuro com o aviso de simulação, velocidade e conta-giros se movendo, rodapé com valores.
4. Tocar em **Detalhes** → anotar: superfície (ativa/ausente), resolução e dpi, quadros desenhados, fps, último erro.
5. **Fotografar a tela.** Isso é a prova de conceito real.
6. Depois, com o ELM327: mesma tela, agora sem o aviso de simulação, com valores reais do carro.

### 10.5 Se falhar, na ordem

| Sintoma | Próximo passo |
|---|---|
| Não aparece no launcher do AA | trocar R2 por R1 (Internal App Sharing) |
| Aparece, abre, mas tela preta | capturar o "último erro" na tela de Diagnóstico; provavelmente o host recusou `NavigationTemplate` |
| Recusou o template | rebuildar com `-PcarCategory=androidx.car.app.category.NAVIGATION` |
| Ainda recusa | cair para o caminho de template simples (Pane/Grid) e reavaliar a ambição visual |
| Aparece e desenha, mas trava | reduzir taxa de quadros e investigar `lockCanvas` |

---

## 11. Divergências entre os agentes e como foram resolvidas

| Divergência | Versões | Resolução |
|---|---|---|
| **"Só Internal App Sharing resolve"** vs apps que funcionam instalados por ferramenta de terceiro | Agente 1 afirmou que só a Play resolvia; eu encontrei UnknownAuto (ativo, MIT), AAEnabler e um app com gauges em central real | Pedi revisão ao Agente 1. Ele **corrigiu**: o validador lê o *registro de instalação*, não a Play; o spoof é viável. Manteve, corretamente, que "Fontes desconhecidas" não cobre Car App Library. |
| `adb install -i com.android.vending` resolve? | eu supunha que sim | **Não resolve** — o AA usa o instalador *iniciador*; num install por shell, o iniciador é o shell. Corrigido antes de eu recomendar errado. |
| Versão mínima de Android | Agente 1: Android 9 (doc); notícias de 2025: Android 11 | A documentação atual de desenvolvimento ainda diz **Android 9**; o app Android Auto em si subiu o requisito. Irrelevante para o caso: o Redmi está no Android 16, o J4 Core está no 8.1 (fora dos dois). |
| Motorização do Yaris 2021 BR | Duas fontes públicas erradas (Mobiauto: "todas CVT"; Forcedmotors: "todas 1.5") | Agente 3 confrontou com release e FIPE: existe 1.3 manual/CVT e 1.5 CVT. Registrado como divergência de fonte, não como incerteza. |
| Categoria do app | Agente 1: IOT, "sem mapa, sem surface" | A categoria é checada na **revisão da Play**, não em runtime (o gate documentado é a permissão de manifesto). Como não haverá revisão, a combinação IOT + `NAVIGATION_TEMPLATES` deve renderizar `[HIPÓTESE]`. Por isso o build aceita as duas categorias. |
| DHU serve como teste? | senso comum: sim | **Não** para distribuição — developer head unit pula o validador. Confirmado na doc e no relato do validador. |

---

## 12. O que continua hipótese — e como fechar

| Incerteza | Como fechar | Onde |
|---|---|---|
| Protocolo real do carro (`ATDPN`) | ler e exibir na tela de diagnóstico na 1ª conexão | carro + ELM327 |
| `0x52` (% etanol) é PID padrão nesse carro | pedir `0152`; se `NO DATA`, tentar `21 52` / `22 52` | carro + ELM327 |
| VIN por `09 02`, freeze frame, DTCs pendentes/permanentes | pedir e registrar resposta crua | carro + ELM327 |
| Quais PIDs a ECU suporta | bitmasks `0100/0120/0140/0160` | carro + ELM327 |
| Categoria ↔ template no AA 18 | Teste A + Teste C | celular + carro |
| Se o app aparece na central original | Teste C | carro |
| Latência real do adaptador comprado | medir ciclo de PIDs | carro + ELM327 |

---

## Apêndice — documentação produzida

| Arquivo | Conteúdo |
|---|---|
| `docs/pesquisa/android-auto.md` | pesquisa completa do Agente 1 (~3.400 palavras) + seção de correção |
| `docs/pesquisa/yaris-2021.md` | pesquisa do Agente 3, com tabela confirmado↔hipótese (35 linhas) e checklist |
| `docs/especificacao-obd2-elm327.md` | especificação da camada OBD2 (~15,8 mil palavras): protocolo, AT commands, PIDs com fórmulas, DTC, arquitetura Kotlin, emulador e transcripts |
| `docs/research/raw/` | texto extraído das páginas oficiais usadas como fonte (auditável) |
| `app/` | projeto Android + APK de prova de conceito |
| `tools/getdoc.ps1` | utilitário que baixa e limpa HTML das fontes |

Todas as URLs consultadas estão listadas ao final de cada documento de pesquisa, com o que cada uma confirmou.
