# Painel OBD2 no Android Auto de uma central original Toyota Yaris 2021 (BR) — relatório técnico

**Autor:** Agente 1 — Especialista em Android Auto
**Data da coleta:** 12/11/2026
**Escopo:** Android Auto (projeção do celular) — **não** Android Automotive OS / Google built-in.
**Regra de leitura:** `[CONFIRMADO: URL]` = li a página/código e a afirmação está lá; `[HIPÓTESE]` = inferência técnica minha, não documentada; `[NÃO CONFIRMADO]` = não achei fonte ou o fetch falhou.

---

## Resumo executivo (veredito)

1. **Sim, é possível** mostrar um painel OBD2 próprio no Android Auto **sem publicar na Play Store** — mas **não** por sideload de APK. O caminho real é **Internal App Sharing / trilha de teste interna** do Play Console: instala "pela Play", sem revisão pública, e a central aceita.
2. **Não é possível desenhar gauges livres.** Templates da Car App Library aceitam texto, ícones, listas, grids, panes e mensagens — **não** aceitam `View` customizada nem `Canvas`. A única superfície de desenho livre (`ACCESS_SURFACE`) existe para **mapas** de apps de navegação/POI/clima, com regra explícita de conteúdo.
3. **Sideload não aparece na central.** O "Unknown sources" do modo desenvolvedor cobre **media, messaging notifications e parked apps**; a documentação diz expressamente que **não vale para apps da Car App Library** (templated).
4. Evidência de campo (AA 17.7, out/2026): o validador do AA nega pacotes `TEMPLATE` e `PROJECTION` no "Unknown sources"; só passa quem tem instalador "Play Store", head unit de desenvolvedor ou root.
5. **Melhor aposta para o seu caso:** app `androidx.car.app` na categoria **IOT** (ou NAVIGATION, se você aceitar o escopo de navegação), distribuído por **Internal App Sharing**, com gauges como **GridTemplate/PaneTemplate** (ícones + textos) e, se aceitar o risco, `MapWithContentTemplate` para desenho livre.
6. **Espelhamento** (AAAD/Fermata Auto/Screen2Auto/AA Mirror) funciona **hoje só com contorno**: instalador "Play" (KingInstaller), dongle com developer mode ou root. Sem isso, é `Package DENIED`.
7. **DHU comprova UI e manifest; não comprova distribuição** — o DHU é um "developer head unit" que ignora as checagens de instalador, exatamente o que a central real faz.
8. **Risco central:** os gauges atualizando em tempo real contrariam as regras de distração do Google (refreshes são "throttled"; fluxo ≤5 passos). Um painel de 10 Hz não é o caso de uso previsto pela plataforma.

---

## 1. É possível exibir painel OBD2 personalizado no Android Auto sem publicar na Play Store?

**Resposta curta:** sim, com três condições cumulativas.

**Condição A — o app precisa ser de uma categoria suportada.** Não existe categoria "dashboard", "telemetria" ou "OBD". As categorias da Car App Library são: `NAVIGATION`, `POI`, `IOT`, `WEATHER`, `MEDIA`, `MESSAGING`, `CALLING` [CONFIRMADO: https://developer.android.com/training/cars/apps/library/set-up-project]. A diretriz PC-1 diz que "o app não pode incluir funcionalidades fora dos tipos de app destinados a carros" [CONFIRMADO: https://developer.android.com/docs/quality-guidelines/car-app-quality]. Para um leitor ELM327, o encaixe menos artificial é **IOT** ("permite ao usuário executar ações em dispositivos conectados"), como já fazem vários projetos (ver §5).

**Condição B — instalação por fonte confiável.** "Para testar em veículos reais, você deve instalar o app de uma fonte confiável, como o Google Play, com uma exceção [unknown sources]. Você pode usar Internal App Sharing ou Internal Test Track para distribuir o app para dispositivos sem passar pelo processo de revisão do Google Play." [CONFIRMADO: https://developer.android.com/training/cars/testing]. A lista de fontes confiáveis é **Google Play** e **ONE store** [CONFIRMADO: https://developer.android.com/training/cars/platforms/android-auto].

**Condição C — o app precisa declarar suporte a Android Auto.**
`<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc"/>` e, em `automotive_app_desc.xml`, `<automotiveApp><uses name="template"/></automotiveApp>` [CONFIRMADO: https://developer.android.com/training/cars/apps/auto].

**Sobre a revisão:** a tabela de "App review depends on release track type" mostra **Internal sharing (Android Auto only) → None** e **Internal testing → None**; closed testing é não-bloqueante; open/production são bloqueantes [CONFIRMADO: https://developer.android.com/training/cars/distribute].

**O que NÃO funciona:** instalar o APK por `adb install` ou por gerenciador de arquivos e esperar que apareça na central. Isso é explicitamente negado (§4).

**Custo/atrito:** exige conta de desenvolvedor Google Play (taxa única + verificação de identidade) [NÃO CONFIRMADO — não li a página de cadastro nesta pesquisa].

---

## 2. Arquitetura do Android Auto e o que a Car App Library permite/proíbe

### 2.1 Arquitetura (projeção)

Android Auto "oferece uma experiência otimizada para o motorista para usuários que têm um celular Android com o app Android Auto e uma central compatível. Eles usam seu app na tela do carro conectando o celular. Você habilita o Android Auto a se conectar com o app do celular criando **services** que o Android Auto usa para exibir uma interface otimizada" [CONFIRMADO: https://developer.android.com/training/cars/platforms/android-auto].

Consequências arquiteturais relevantes:

- **O app roda no celular.** A central é um display + input. Não há como instalar nada na central original Toyota por esse caminho.
- **A UI é renderizada pelo host** (o app Android Auto no celular), a partir de *templates* serializados que o seu app envia. Por isso "não existe `View` customizada": o app nunca desenha na tela do carro, exceto na `Surface` de mapas.
- **Os dados do OBD2 têm que estar no celular** (Bluetooth/USB para o ELM327) e o app precisa continuar vivo enquanto o celular projeta — inclusive com a tela do celular apagada. Serviço em foreground é o padrão.
- Detecção de projeção: `CarConnection` retorna `CONNECTION_TYPE_PROJECTION`; `displayId != DEFAULT_DISPLAY` [CONFIRMADO: https://developer.android.com/training/cars/parked/auto].

### 2.2 O que cada categoria pode renderizar

| Categoria (`androidx.car.app.category.*`) | O que renderiza | Observações |
|---|---|---|
| `NAVIGATION` | `NavigationTemplate` (mapa full-screen + action strip + card de rota), `MapWithContentTemplate`; exige `NAVIGATION_TEMPLATES` e `ACCESS_SURFACE` para desenhar | Categoria com maior liberdade gráfica (surface) |
| `POI` | `PlaceListMapTemplate`, `MapWithContentTemplate` (`MAP_TEMPLATES`) | Lista de locais ao lado de mapa |
| `IOT` | Templates de uso geral: List/Grid/Pane/Message/Search/Tab | Sem mapa, sem surface |
| `WEATHER` | `MapWithContentTemplate` + conteúdo de clima | Regras rígidas de anotações/legendas |
| `MEDIA` | `MediaPlaybackTemplate` + árvore de browse do `MediaBrowserService` | Artwork e metadados |
| `MESSAGING` | Notificações de mensagem + mensageria templated | Só Android Auto (não AAOS) |
| `CALLING` | Experiência de chamadas | — |

[CONFIRMADO: https://developer.android.com/design/ui/cars/guides/templates/overview e .../set-up-project]

**Categorias "parked" (fora da Car App Library):** Video (só AAOS), Games (AA e AAOS), Browsers (só AAOS) [CONFIRMADO: https://developer.android.com/training/cars/parked]. **Games é a única categoria parked suportada no Android Auto** [CONFIRMADO: https://developer.android.com/training/cars/parked/auto].

### 2.3 Proibições que importam para um painel OBD2

- **Sem UI livre em templates:** só os componentes previstos (texto, `CarIcon`, `GridItem`, `Row`, toggles em linhas de lista, action strip). Seção "Template-specific requirements" lista apenas os 11 templates [CONFIRMADO: .../design/ui/cars/guides/ux-requirements/overview].
- **Fluxo de tarefa ≤ 5 passos** e, sem o recurso *Adaptive task limits*, o 5º passo não pode terminar em template baseado em lista [CONFIRMADO: .../ux-requirements/overview].
- **Sem conteúdo fora do tipo do app** (PC-1) [CONFIRMADO: quality guidelines].
- **Ao dirigir, o app não pode pedir permissão de runtime na tela do carro** (Android 15): aparece diálogo mandando aceitar no celular [CONFIRMADO: .../platforms/android-auto].
- **Intents para abrir outros apps são bloqueados** (exceto web/Play, que abrem no celular) [CONFIRMADO: idem].
- **Atualização de conteúdo é limitada:** "as atualizações de conteúdo de template podem tirar a atenção do motorista; alguns tipos de atualização são limitados... Atualizações que são *refreshes* são menos disruptivas e **podem ser throttled**" [CONFIRMADO: https://developer.android.com/design/ui/cars/guides/foundations/define-user-tasks].

---

## 3. Os templates permitem gauges/gráficos próprios? (limites concretos)

### 3.1 Lista de templates

**Uso geral (qualquer categoria):** List, Grid, Sectioned Item, Pane, Message, Long Message, Search, Sign-in, Tab.
**Propósito específico:** Navigation (mapa full-screen), Place List (mapa + lista — POI), Map + Content (Navigation/POI/Weather), Media Playback (Media) [CONFIRMADO: https://developer.android.com/design/ui/cars/guides/templates/overview].

### 3.2 Limite duro: quota de templates

"O host limita o número de templates a exibir para uma dada tarefa a **no máximo cinco**, dos quais o último deve ser um destes tipos: `NavigationTemplate`, `PaneTemplate`, `MessageTemplate`, `MediaPlaybackTemplate`, `SignInTemplate`, `LongMessageTemplate`." Se a quota estourar, "o host exibe uma mensagem de erro ao usuário antes de fechar o app". Há exceções: *refreshes* (mesmo tipo + mesmo conteúdo principal) não contam; voltar (pop) devolve quota; templates de reset (ex.: Navigation) reiniciam a quota [CONFIRMADO: https://developer.android.com/training/cars/apps/library/template-restrictions].

### 3.3 O que dá para "desenhar" dentro de um template

- **Ícones/imagens:** `CarIcon` envolvendo `IconCompat`; recomendação de usar **vetores** para evitar problemas de escala; *themed drawables* via `androidx.car.app.theme` [CONFIRMADO: https://developer.android.com/reference/androidx/car/app/model/CarIcon].
- **Tamanho mínimo** de imagens/ícones: "SHOULD meet or exceed 36 x 36 dp" (é mínimo, não máximo) [CONFIRMADO: .../ux-requirements/overview].
- **Tamanho máximo de ícone:** **NÃO CONFIRMADO.** O javadoc de `CarIcon` não fixa limite de pixels; a doc fala em múltiplas densidades.
- **Quantidade de itens em grid/lista:** definida **pelo host**, em runtime, via `ConstraintManager.getContentLimit(...)`; "o host define esses limites, que não podem ser modificados por apps" [CONFIRMADO: https://developer.android.com/training/cars/apps/library/constraints-api].
- **Gráficos/telas customizadas:** **não existem** templates com `View`/`Canvas` arbitrário. Não há `WebView`, nem `SurfaceView` em templates comuns.

### 3.4 A exceção real: `ACCESS_SURFACE` (mapas)

`NavigationTemplate` e `MapWithContentTemplate` (Navigation/POI/Weather) permitem obter uma `Surface` do host via `SurfaceCallback` (`onSurfaceAvailable`, `onSurfaceDestroyed`, `onVisibleAreaChanged`, `onStableAreaChanged`). Você pode desenhar:
- direto com **Canvas**, ou
- **qualquer `View`** (inclusive `ComposeView`) via `VirtualDisplay` + `Presentation` [CONFIRMADO: https://developer.android.com/training/cars/apps/library/draw-maps].

Isso é tecnicamente um "painel livre". Mas há três cercas:
1. Requer categoria Navigation/POI/Weather + permissão do template + `androidx.car.app.ACCESS_SURFACE` [CONFIRMADO: idem].
2. O host desenha UI **por cima** da surface; você recebe as áreas visível/estável [CONFIRMADO: idem].
3. Regra de conteúdo: **"MUST: desenhar somente conteúdo de mapa e conteúdo relacionado à direção na surface"** (navegação) e **"somente mapa e conteúdo relacionado ao clima"** (weather) [CONFIRMADO: .../ux-requirements/overview].

**Interpretação [HIPÓTESE]:** velocímetro/conta-giros são "conteúdo relacionado à direção" em sentido literal, então um app de navegação *poderia* argumentar que gauges na surface são permitidos. Na prática isso é frágil: o app teria que ser, de fato, um app de navegação (PC-1 + requisitos de categoria), e a revisão do Play julgaria o conjunto. Para uso pessoal via Internal App Sharing, não há revisão — o limite passa a ser técnico, não editorial.

### 3.5 Rate limit de atualização

- Documentado: refreshes **podem ser throttled**; com *Adaptive task limits*, "os refreshes são throttled durante a condução" [CONFIRMADO: .../plan-task-flows e .../define-user-tasks].
- **Número explícito (Hz, ms, "1x por segundo"): NÃO CONFIRMADO.** Não encontrei nenhum valor publicado nas páginas que li.
- Para desenho na surface (navegação), não encontrei limite de FPS documentado — **NÃO CONFIRMADO**.

**Consequência prática [HIPÓTESE]:** um gauge a 10–20 Hz atualizando *template* está fora do previsto e provavelmente será suavizado/derrubado pelo host; desenho na surface é o único lugar onde taxa alta faz sentido.

---

## 4. Modo desenvolvedor do Android Auto, "Unknown sources" e versões

### 4.1 Como habilitar (passos oficiais)

1. Settings → Apps → Android Auto → Advanced → Additional settings in the app (Android 10+) ou menu → Settings (Android 9-).
2. Seção **About** → toque em **Version** 10 vezes.
3. Diálogo "Allow development settings?" → OK. [CONFIRMADO: https://developer.android.com/training/cars/testing]

O modo desenvolvedor dá acesso a: **overlay de debug** (mostra o número de steps na tela), **Start head unit server** (necessário para o DHU por ADB), **Unknown sources**, entre outras opções [CONFIRMADO: .../testing, .../testing/dhu e .../template-restrictions].

### 4.2 O que "Unknown sources" faz — e o que NÃO faz

Texto oficial, decisivo: *"O Android Auto tem uma opção de desenvolvedor que permite rodar apps que não foram instalados de uma fonte confiável. Essa configuração se aplica a apps **media**, **messaging notifications** e **parked**, mas **não se aplica a apps construídos com a Android for Cars App Library**."* [CONFIRMADO: https://developer.android.com/training/cars/testing]

Ou seja: **um app templated (o seu painel OBD2) não passa por aí.** O que passa: media apps (MediaBrowserService), notificações de mensagens e parked apps.

### 4.3 Confirmação independente do código do AA (AA 17.7, out/2026)

Issue técnica do Fermata detalha o validador (`CAR.VALIDATOR`) do Android Auto 17.7, com logs:

> "Unknown sources" só admite **MEDIA, NAVIGATION (legacy), NOTIFICATION, SMS, SERVICE, OEM e native apps**. **PROJECTION → não. TEMPLATE (Car App Library) → não. MESSAGING → não.**
> Instalação por shell/sideload termina em `W CAR.VALIDATOR: Package DENIED; failed all other checks`.
> Passa quem: tem instalador registrado como Play Store (KingInstaller), está na biblioteca Play do usuário com o mesmo certificado, é head unit de desenvolvedor, ou tem AA patchado com root.
[CONFIRMADO: https://github.com/AndreyPavlenko/Fermata/issues/846 — aberta/fechada em out/2026]

### 4.4 Limitações por versão

- **Android (celular):** Android Auto exige **Android 9 (API 28)** ou superior [CONFIRMADO: .../platforms/android-auto].
- **Android 14+:** restrição de sideload mais dura (installer identity via `getInstallSourceInfo`); reportada como "the known Android 14+ sideload restriction" [CONFIRMADO: https://github.com/AndreyPavlenko/Fermata/issues/829].
- **Android 15+:** AA passa a rodar **Activities nativas** na tela, mas **só parado**, e "o Android Auto sai automaticamente do app quando detecta movimento do veículo" [CONFIRMADO: .../platforms/android-auto e .../parked/auto]. Também no Android 15 não se aceita permissão de runtime na tela do carro.
- **Versão do AA:** o comportamento do validador é do app Android Auto (17.7 no relato), não do Android. Atualizações do AA podem mudar as regras sem aviso (ver §6).

### 4.5 Manifest necessário (resumo)

```xml
<meta-data android:name="com.google.android.gms.car.application"
           android:resource="@xml/automotive_app_desc"/>
<meta-data android:name="androidx.car.app.minCarApiLevel" android:value="..."/>
<service android:name=".MyCarAppService" android:exported="true"
         android:label="@string/app_name" android:icon="@drawable/ic">
  <intent-filter>
    <action android:name="androidx.car.app.CarAppService"/>
    <category android:name="androidx.car.app.category.IOT"/>
  </intent-filter>
</service>
```
`automotive_app_desc.xml`: `<automotiveApp><uses name="template"/></automotiveApp>` [CONFIRMADO: .../apps/auto]. Permissões de template por categoria: `androidx.car.app.NAVIGATION_TEMPLATES`, `MAP_TEMPLATES`, `MEDIA_TEMPLATES` etc. [CONFIRMADO: .../draw-maps].

---

## 5. Projetos open source reais (OBD2 + Android Auto)

Método: busca na API do GitHub (`q=android auto obd`), leitura do `AndroidManifest.xml` bruto de cada repo e verificação de atividade. **Nenhum** foi validado por mim numa central real.

| Repo (URL) | Último push | ★ | Evidência de manifest | Veredito |
|---|---|---|---|---|
| https://github.com/borconi/obd2aa | 2022-11-30 | 139 | `CarAppService`, `NAVIGATION_TEMPLATES` + `ACCESS_SURFACE`, `<uses name="template"/>` no `automotive_app_desc`, plugin do Torque | **O mais próximo do conceito** (desenha na surface). **Abandonado desde 2022** |
| https://github.com/CalmAussie/obd2-reader | 2026-11-12 | 5 | `category.IOT`, Car App Library, MIT | Ativo; **issue #8 aberta (11/11/2026): "Android 16 app not appearing in android auto"** |
| https://github.com/alequab/Zakhm | 2026-11-12 | 0 | `category.IOT`, Kotlin/Compose, ELM327 + BLE | Recente, sem usuários; sem relato de funcionamento em central |
| https://github.com/mtgofa/obd2-app | 2026-10-07 | 0 | `category.NAVIGATION` (comentário no manifest: "a única com superfície de desenho livre") | Experimental |
| https://github.com/curtinengineering/MXTelemetry | 2026-11-04 | 0 | `NAVIGATION` + `IOT` | Experimental, específico de MX-5 |
| https://github.com/javirmmn/CustomOBDDashboard | 2026-07-28 | 0 | `NAVIGATION` + legado `com.google.android.gms.car.category.NAVIGATION` | Experimental |
| https://github.com/ccasadei/niro-phev-monitor | 2026-11-09 | 0 | `NAVIGATION` | Experimental, Kia Niro |
| https://github.com/bobphrapples/IoniqVitals | 2026-08-19 | 0 | `category.MEDIA` + `MediaBrowserService` + `MEDIA_TEMPLATES`; comentário no manifest: *"sideloaded apps only list via the media browser on this DHU. (IOT + vending installer spoof tried twice, both DENIED.)"* | **Evidência direta do problema**; gauges como tiles no browse de mídia |
| https://github.com/Dappermanphoto/CarToon | 2026-11-12 | 0 | Manifest não está em caminho padrão — não consegui ler | **NÃO CONFIRMADO** |
| https://github.com/IrvingGuerra/litedash | 2026-11-12 | 52 | Launcher para **head unit Android** com OBD2/TPMS | **Não é Android Auto** — é central Android (outra coisa) |

**Conclusão da §5:** existe código real e ativo que coloca OBD2 dentro do AA — mas (a) na forma de **templates**, nunca de painel livre; (b) **nenhum** relata funcionamento comprovado em central original via sideload; (c) o padrão observado é "aparece no DHU / na lista do celular e **não** aparece no carro".

---

## 6. Alternativas fora do caminho oficial

Contexto técnico que explica todas elas (AA 17.7): o validador checa, na ordem, existência do pacote → **identidade do instalador** (`getInitiatingPackageName`/`getInstallingPackageName`) → blocklist do servidor → capability declarada no `automotive_app_desc` → **head unit de desenvolvedor (pula tudo)** → restrições de HU → flags do servidor/lista de instaladores permitidos/Play "vouches" → allowlist → **Unknown sources** (só para certos tipos) → `Package DENIED` [CONFIRMADO: Fermata #846].

| Alternativa | Como funciona | Funciona hoje? | Root? | Riscos | Fonte |
|---|---|---|---|---|---|
| **AAAD** (Android Auto Apps Downloader) | Baixa e instala apps de terceiros "do jeito certo" para aparecerem no AA; licença PRO 3,99 €; grátis = 1 download/30 dias | Parcial. Repo ativo (push 27/09/2026, 3.248★), mas o próprio README diz: *"Android 14+: versões abaixo de 2.0 não são compatíveis, compatibilidade ainda incerta em 2.x.x"* | Não | Play Protect reclama; pode parar de funcionar a qualquer update do AA; "não garante que os apps funcionem" | https://github.com/shmykelsa/AAAD |
| **Fermata Auto / Fermata Mirror** | Player de mídia + espelhamento; declara `CATEGORY_PROJECTION_OEM` (tipo PROJECTION) | Mídia sim; Auto/Mirror **não** em carro real com sideload puro | Não (mas precisa de contorno) | PROJECTION não é aceito por "Unknown sources"; exige KingInstaller/dev HU/root | https://github.com/AndreyPavlenko/Fermata + issue #846 |
| **Screen2Auto / AA Mirror / AA Mirror Plus / AAStream** | Espelhamento de tela (tipo PROJECTION) | Só via AAAD/KingInstaller/root; sem repo oficial (mirrors não oficiais: `androidautohacks/screen2auto`, `aahacks/Screen2Auto`) | Não com KingInstaller; sim no método antigo | Mesmo `Package DENIED`; projeto historicamente instável; métodos morrem a cada update do AA | AAAD README + #846 |
| **KingInstaller** | Instala APK "como se viesse da Play Store" (intents, Shizuku ou root) | Sim, e é o contorno mais usado; push 10/11/2026, 644★ | Não (Shizuku) ou sim | Não resolve APKs split (`cannot be made visible in Android Auto`); cada update precisa ser reinstalado igual | https://github.com/fcaronte/KingInstaller |
| **AA AIO Tweaker / AA-Tweaker** | Patch em Google Play Services/AA para liberar funções | Sim em aparelhos com root; último push **30/05/2023** (568★) | **Sim** (LSPosed/Xposed) | Root + módulo; risco de bootloop; desatualizado frente ao AA 17.x | https://github.com/shmykelsa/AA-Tweaker |
| **Dongle com developer mode (ex.: AAWireless)** | Faz a central se apresentar como "developer head unit"; o validador pula checagens | Sim — citado como "a única configuração que faz o Fermata Auto aparecer de forma confiável" | Não | Depende de flag server-side do Google (pode sumir); não é a central original | Fermata #846 |
| **Parked app (jogo) com Activity nativa** | `CAT_LAUNCHER` numa Activity; AA roda a UI do app na tela | Sim, **mas só parado** (Android 15+); sai ao detectar movimento; "Unknown sources" **vale** para parked | Não | Inútil como painel de condução; revisão exige categoria Games | https://developer.android.com/training/cars/parked/auto |
| **Cavalo de troia media/POI** | App de mídia mostra dados em tiles/imagens no browse | Sim para o tipo MEDIA (passa pelo Unknown sources) | Não | Só UI de mídia; atualização de imagem é lenta; não é painel | IoniqVitals manifest + doc de testing |

**Riscos gerais (todos os métodos não oficiais):** (1) quebra a cada atualização do AA/Android; (2) Play Protect e possível banimento de conta Google (para AAAD, o README admite ter sido colocado em blocklist uma vez); (3) APKs de origem duvidosa = risco de segurança real; (4) nada disso afeta a garantia do **carro**, mas root afeta a do celular; (5) "funciona hoje" ≠ "funciona no seu Yaris", porque o validador e a versão do AA variam por aparelho.

---

## 7. Desktop Head Unit (DHU): o que comprova e o que não comprova

**O que é:** emulador de head unit que roda em Windows/macOS/Linux, instalado pelo SDK Manager (`SDK_LOCATION/extras/google/auto/`), conectado ao celular por Accessory Mode (AOA, `./desktop-head-unit --usb`) ou por ADB tunneling (exige **developer mode** + **Start head unit server**) [CONFIRMADO: https://developer.android.com/training/cars/testing/dhu].

**Comprova:**
- Se o manifest está correto (categoria, `automotive_app_desc`, `minCarApiLevel`, `CarAppService`).
- Se os templates renderizam, se a navegação entre telas respeita a quota de 5, se o overlay de debug mostra os steps.
- Comportamento nas resoluções canônicas: **Small Landscape 800x480**, **Wide Landscape 1920x1080**, **Portrait 1920x1080** [CONFIRMADO: .../parked/auto].
- Callbacks de surface, dark theme, `ConstraintManager`.

**NÃO comprova:**
- **Que o app vai aparecer na central real.** O DHU é justamente um "developer head unit" (`Google / Desktop Head Unit`), e nesse caso **todas as checagens de instalador são puladas** [CONFIRMADO: Fermata #846]. Ou seja: **o DHU mostra o que o carro vai negar.** Essa é a armadilha número 1 do projeto.
- Comportamento da central Toyota (resolução real, touch, botões físicos, latência).
- O link OBD2 real (Bluetooth/USB para o ELM327, taxa de PIDs, reconexão).
- Thermal/performance do celular projetando + lendo OBD.
- Se a versão de AA do celular do usuário tem as mesmas regras.

---

## 8. Veredito: maior probabilidade real no Yaris 2021

**Ranking para "painel OBD2 aparecer na central original Toyota, por cabo USB":**

**(a) Viável e comprovada — nenhuma alternativa entrega "painel OBD2 customizado em AA" com prova pública em central original.** A única coisa comprovada é "apps de mídia/templated aprovados aparecem". Por honestidade, não classifico nada como (a) para o objetivo completo.

**(b) Viável com restrições — a melhor aposta:** app Car App Library em **IOT** (ou Weather/POI, se quiser surface), distribuído por **Internal App Sharing / trilha interna**. Restrições: painel só com componentes de template (sem gauges circulares desenhados); refreshes sujeitos a throttle; exige conta Play; categoria precisa ser defensável.
Rota secundária em (b): **app de mídia** com tiles de OBD (padrão IoniqVitals) — passa pelo "Unknown sources", mas a UI é de mídia e a atualização é lenta.

**(c) Experimental:** `MapWithContentTemplate` + `ACCESS_SURFACE` para desenhar gauges de verdade (tecnicamente suportado; conteúdo "drive-related" é zona cinzenta); dongle com developer mode; KingInstaller + Fermata Auto/Mirror; AAAD.

**(d) Inviável:** `adb install`/sideload de app templated esperando que apareça na central; qualquer esperança de rodar `View`/`Canvas`/`WebView` dentro de um template; AAOS como solução para essa central (é outra plataforma, para carro com Android embarcado).

**Recomendação prática:** prototipe o painel em **IOT + GridTemplate/PaneTemplate** primeiro (rápido, sem risco), valide no DHU **e** depois via Internal App Sharing no carro. Se a fidelidade gráfica for requisito, aí sim avalie `MapWithContentTemplate` como app de navegação — sabendo que está usando a brecha da surface.

---

## CORREÇÃO (revisão de 12/11/2026, após evidência de campo do usuário)

**A conclusão "a única rota sem publicar é Internal App Sharing" estava incompleta.** Existe uma segunda rota real e ativa: **forjar o registro de instalação** de modo que o *package installer* do Android seja o iniciador e a Play Store o instalador registrado.

- Mecanismo no AA (17.7): o validador lê `getInstallSourceInfo().getInitiatingPackageName()`; **somente se** o iniciador for `com.google.android.packageinstaller` ele usa `getInstallingPackageName()`. Um `pm install -i com.android.vending` por shell **não funciona**, porque o iniciador é o shell [CONFIRMADO: https://github.com/AndreyPavlenko/Fermata/issues/846].
- KingInstaller faz isso abrindo o instalador do sistema com `EXTRA_INSTALLER_PACKAGE_NAME`/`EXTRA_NOT_UNKNOWN_SOURCE`; verificação por `dumpsys`: `installerPackageName=com.android.vending` + `initiatingPackageName=com.google.android.packageinstaller` [CONFIRMADO: issue #846, seção "KingInstaller, explained"].
- Ferramentas novas, ativas: **UnknownAuto** (248★, MIT, push 12/11/2026) que declara explicitamente resolver o caso de **apps da Car App Library** via Shizuku, e **AAEnabler** (219★, push 06/11/2026) [CONFIRMADO: https://github.com/GitMag/UnknownAuto e https://github.com/malebuffy/AAEnabler].
- **Performance-Monitor-AAOBD** é o único repo que encontrei com gauges reais numa central real (VW) — mas **não via Car App Library**: usa `com.google.android.apps.auto.sdk.CarActivity`, `<uses name="projection"/>`, `CATEGORY_PROJECTION`/`CATEGORY_PROJECTION_OEM` e a permissão `com.google.android.gms.permission.CAR_VENDOR_EXTENSION`, e o README manda instalar via AAEnabler porque "Android Auto only shows apps installed from a store" [CONFIRMADO: https://github.com/Le-F-Sur-GitH/Performance-Monitor-AAOBD].

Ou seja: a leitura da documentação continua correta (Unknown sources **não** cobre Car App Library), mas "não dá para instalar fora da Play" era falso — o que o AA checa é o **registro de instalação**, não a assinatura nem a posse na biblioteca Play.

## Tabela comparativa final

| Alternativa | Como funciona | Exige root? | Exige Play Store? | Risco | Status | Fonte |
|---|---|---|---|---|---|---|
| App `androidx.car.app` (IOT/Weather/POI) via Internal App Sharing | Templates renderizados pelo host; instalação "pela Play" sem revisão | Não | **Sim** (conta dev + trilha interna) | Baixo; UI limitada a templates | **Viável com restrições** | testing + distribute |
| App `androidx.car.app` (NAVIGATION) + `ACCESS_SURFACE` | Desenho livre (Canvas/Compose via VirtualDisplay) na área de mapa | Não | Sim | Médio: regra "só mapa/conteúdo de direção"; categoria discutível | **Experimental** | draw-maps + ux-requirements |
| App templated por **sideload** (`adb`/APK) | — | Não | Não | — | **Inviável** (Unknown sources não cobre TEMPLATE) | testing + Fermata #846 |
| App de **mídia** com tiles OBD (IoniqVitals) | MediaBrowserService + imagens | Não | Não (com Unknown sources) | Médio: UI de mídia, updates lentos, opção pode sumir | **Experimental** | manifest IoniqVitals |
| **Fermata Auto / Mirror** | Player + espelhamento (tipo PROJECTION) | Não* | Não* | Alto: `Package DENIED` sem contorno | **Experimental** | Fermata #846 |
| **AAAD** | Instala apps de terceiros "do jeito certo" | Não | Não | Alto: Play Protect, quebra em updates, "compatibilidade incerta" no Android 14+ | **Experimental** | AAAD README |
| **KingInstaller** | Instala como se viesse da Play | Não (Shizuku) / Sim | Não | Médio-alto: split APK não funciona; reinstalar a cada update | **Viável com restrições** (para mídia/projeção) | KingInstaller README |
| **AA-Tweaker / AIO Tweaker** | Patch em GMS/AA | **Sim** (LSPosed) | Não | Alto: root, bootloop, projeto parado em 2023 | **Experimental** | repo AA-Tweaker |
| **Screen2Auto / AA Mirror Plus** | Espelhamento (PROJECTION) | Depende do método | Não | Alto: sem repo oficial, métodos morrem | **Experimental** | AAAD README + #846 |
| **Parked app (Games) com Activity** | AA roda a Activity nativa | Não | Sim (para distribuir) | Baixo tecnicamente, **inútil dirigindo** | **Inviável para o objetivo** | parked/auto |
| **DHU** (teste) | Emula head unit | Não | Não | Não prova nada sobre a central real | **Ferramenta, não solução** | testing/dhu + #846 |

\* Fermata Auto é gratuito e open source; a exigência de "Play" vem do validador do AA, não de licença.

---

## URLs consultadas (o que cada uma confirmou)

**developer.android.com**
1. https://developer.android.com/training/cars/apps/library — visão geral; app deve pertencer a categoria suportada.
2. https://developer.android.com/training/cars/apps/library/template-restrictions — quota de 5 templates; últimos tipos permitidos; refreshes/back/reset.
3. https://developer.android.com/training/cars/apps/library/set-up-project — lista canônica de categorias (`NAVIGATION`, `POI`, `IOT`, `WEATHER`, `MEDIA`, `MESSAGING`, `CALLING`).
4. https://developer.android.com/training/cars/testing — **"Unknown sources" não se aplica a apps da Car App Library**; passos do developer mode; fontes confiáveis.
5. https://developer.android.com/training/cars/testing/dhu — instalação e uso do DHU; Accessory Mode/ADB tunneling.
6. https://developer.android.com/training/cars/distribute — formulários do Play Console; revisão por tipo de trilha (Internal sharing = None).
7. https://developer.android.com/training/cars/platforms/android-auto — projeção; AA exige Android 9+; `automotive_app_desc` por categoria; parked activities no Android 15+; trusted stores.
8. https://developer.android.com/training/cars/parked — tabela de categorias parked (Video/Games/Browsers) por plataforma.
9. https://developer.android.com/training/cars/parked/auto — `CAT_LAUNCHER`; **Games é a única categoria parked no AA**; resoluções canônicas do DHU.
10. https://developer.android.com/training/cars/car-ready-mobile-apps — programa só para AAOS com Google built-in; "Android Auto será suportado depois".
11. https://developer.android.com/training/cars/apps/auto — meta-data + `<uses name="template"/>`.
12. https://developer.android.com/training/cars/apps/iot — categoria IOT e exemplo de GridTemplate.
13. https://developer.android.com/training/cars/apps/library/draw-maps — permissões de surface; Canvas/VirtualDisplay; `onVisibleAreaChanged`/`onStableAreaChanged`.
14. https://developer.android.com/training/cars/apps/library/refresh-template — `invalidate()`/`onGetTemplate`.
15. https://developer.android.com/training/cars/apps/library/constraints-api — limites de itens definidos pelo host.
16. https://developer.android.com/design/ui/cars/guides/templates/overview — lista de templates.
17. https://developer.android.com/design/ui/cars/guides/ux-requirements/overview — fluxo ≤5 passos; 36x36dp; regras de surface (mapa/direção; mapa/clima).
18. https://developer.android.com/design/ui/cars/guides/app-types/other-apps — categorias aprovadas além de navegação (POI, IoT, weather).
19. https://developer.android.com/design/ui/cars/guides/foundations/define-user-tasks — refreshes "podem ser throttled".
20. https://developer.android.com/design/ui/cars/guides/ux-requirements/plan-task-flows — definição de refresh; Adaptive task limits sob critério do OEM.
21. https://developer.android.com/docs/quality-guidelines/car-app-quality — PC-1; categorias suportadas; DD-3/DO-2.
22. https://developer.android.com/reference/androidx/car/app/model/CarIcon — vetores, themed drawables; **sem limite de pixel documentado**.
23. https://developer.android.com/reference/androidx/car/app/model/ListTemplate — definição de refresh em listas.

**GitHub (repos e issues)**
24. https://github.com/AndreyPavlenko/Fermata/issues/846 — validador do AA 17.7; quais tipos o "Unknown sources" aceita; rotas que funcionam.
25. https://github.com/AndreyPavlenko/Fermata/issues/829 — Android 14+ sideload restriction; `Uses for TEMPLATE not defined`.
26. https://github.com/AndreyPavlenko/Fermata — ativo (push 10/11/2026), 1.507★.
27. https://github.com/shmykelsa/AAAD — ativo (push 27/09/2026), 3.248★; limitações Android 14+.
28. https://github.com/shmykelsa/AA-Tweaker — 568★, último push 30/05/2023.
29. https://github.com/fcaronte/KingInstaller — 644★, push 10/11/2026; split APK não visível no AA.
30. https://github.com/borconi/obd2aa — OBD2AA histórico; `NAVIGATION_TEMPLATES` + `ACCESS_SURFACE`.
31. https://github.com/CalmAussie/obd2-reader — IOT; issue #8 "Android 16 app not appearing in android auto" (11/11/2026).
32. https://github.com/alequab/Zakhm, https://github.com/mtgofa/obd2-app, https://github.com/curtinengineering/MXTelemetry, https://github.com/javirmmn/CustomOBDDashboard, https://github.com/ccasadei/niro-phev-monitor — projetos experimentais (manifests lidos).
33. https://github.com/bobphrapples/IoniqVitals — MEDIA + MediaBrowserService; comentário sobre sideload e "IOT + vending installer spoof … DENIED".
34. https://github.com/IrvingGuerra/litedash — launcher de central Android (não é AA).

---

## O que NÃO foi possível confirmar / precisa de teste físico

**Não confirmado (e por quê):**
1. **Especificação da central original do Yaris 2021 BR** (se é "Toyota Play" com AA por cabo, resolução, comportamento com apps de terceiros). As buscas retornaram apenas centrais *aftermarket*. Tratei como premissa do enunciado. **Precisa confirmação no carro.**
2. **Rate limit numérico de refresh de template** (Hz/segundos). A doc só diz "throttled". Se isso importa para o seu gauge, só medindo.
3. **Limite máximo de tamanho de `CarIcon`** (px/dp). Javadoc não fixa; só há mínimo de 36x36dp.
4. **Limite exato de itens em Grid/List** por veículo — é definido pelo host; os valores mínimos estão em `integers.xml` do AOSP, que não consegui ler (referência via `cs.android.com` é JS).
5. **Se a Internal App Sharing aceita um app IOT sem revisão no caso concreto** — a tabela diz "None", mas a política pode ter nuances não documentadas nessa página.
6. **Comportamento da central Toyota ao reconhecer um app templated vindo de Internal App Sharing.** Nenhum relato público que eu tenha achado; é o teste decisivo.
7. **`Dappermanphoto/CarToon`**: manifest fora do caminho padrão; não verifiquei se é realmente app AA ou AAOS.
8. **Falhas de fetch relevantes:** as páginas do devsite via ferramenta de fetch direta vinham truncadas (contornei via `Invoke-WebRequest` + `.md.txt`); as páginas de *reference* (javadoc) são renderizadas por JS e não são legíveis por fetch estático — usei a variante `.md.txt` quando existia. Não usei XDA/Reddit/4pda: os resultados de busca eram dominados por lojas; as issues do GitHub deram evidência mais precisa e datada.

**Precisa de teste físico no carro (roteiro mínimo):**
1. Build `IOT` + `GridTemplate` com dados simulados → rodar no **DHU** (800x480 e 1920x1080).
2. Subir o APK em **Internal App Sharing** → instalar pela Play no celular → conectar por cabo na central.
3. Se **aparecer** e renderizar: medir latência de atualização e ver se o host faz throttle (comparar com painel local no celular).
4. Se **não aparecer**: repetir com o mesmo APK assinado e enviado como **Internal Test Track** (closed) para descartar a hipótese "internal sharing não habilitado".
5. Só então avaliar a variante `NAVIGATION` + `MapWithContentTemplate` (desenho livre) — e medir se a surface aceita a taxa de quadros necessária.
6. Em paralelo, validar o link OBD2 (ELM327) com a tela do celular apagada e AA ativo (serviço em foreground), que é onde projetos assim costumam falhar.
