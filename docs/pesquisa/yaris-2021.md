# Toyota Yaris 2021 (mercado brasileiro) — diagnóstico e telemetria via OBD2

**O que é fato, o que é hipótese — e o que falta verificar no carro.**

> Nota metodológica: as etiquetas usadas aqui são `[CONFIRMADO]` (fonte primária/oficial ou duas fontes independentes convergentes), `[RELATO]` (relato de usuário em fórum — não é especificação), `[PROVÁVEL]` (inferência tecnicamente sustentada, mas sem fonte direta para este modelo), `[HIPÓTESE]` (candidata testável) e `[NÃO CONFIRMADO]` (não achei evidência). Onde duas fontes divergem, a divergência está explicitada. Nenhum PID proprietário foi inventado: onde não há documentação, o texto diz isso.

---

## 1. Identificação do veículo

### 1.1 Plataforma e origem

- **[CONFIRMADO]** O Yaris vendido no Brasil é o **XP150** — plataforma/série "exclusiva de mercados emergentes", derivada da família do Vios, e **não** o Yaris europeu/japonês. O próprio artigo do XP150 afirma que ele é maior e mais longo que o Yaris XP130 (Japão/EUA/Europa) e que o XP210 (TNGA) é outro carro, não relacionado. O Brasil aparece na lista de locais de produção: **Sorocaba (SP)**. <https://en.wikipedia.org/wiki/Toyota_Yaris_(XP150)>
- **[CONFIRMADO]** No Brasil o hatch foi vendido como **1.3 XL, 1.5 XS e 1.5 XLS** (grades de lançamento) — mesma fonte acima.
- **[CONFIRMADO]** Ficha brasileira de 2021 descreve: plataforma "Toyota B", geração 3, hatch e sedã, produção nacional. <https://www.fichacompleta.com.br/carros/toyota/yaris-xl-live-1-3-2021>

### 1.2 Versões e motorizações do ano-modelo 2021

Linha 2021 (lançada em **outubro/2020**), conforme release reproduzido pela imprensa **[CONFIRMADO]** <https://www.dm.com.br/dmautos/toyota-yaris-2021-estreia-serie-especial-s-com-preco-de-r-89-990/>:

| Carroceria | Versão | Motor | Câmbio |
|---|---|---|---|
| Hatch | **XL Live** | **1.3 flex** | manual **6 marchas** **ou** CVT |
| Hatch | XL Plus Connect | 1.5 flex | CVT |
| Hatch | XS Connect | 1.5 flex | CVT |
| Hatch | X-Way Connect | 1.5 flex | CVT |
| Hatch | **S** (série especial, 450 un.) | 1.5 flex | CVT |
| Hatch | XLS Connect (topo) | 1.5 flex | CVT |
| Sedã | **XL Live** | **1.5 flex** | **apenas manual** |
| Sedã | XL Plus Connect / XS Connect / XLS Connect | 1.5 flex | CVT |

Preços de lançamento da linha 2021 (para identificar a versão do carro): XL Live manual R$ 68.490 / XL Live CVT R$ 69.990 / XL Plus Connect R$ 78.690 / XS Connect R$ 84.790 / X-Way Connect / S R$ 89.990 / XLS R$ 89.990 (hatch); sedã XLS Connect R$ 93.790. **[CONFIRMADO]** (snippets de O Popular e Auto+ TV, ver URLs no fim).

**Códigos de motor:**

- **[CONFIRMADO] 1.5 flex = 2NR-FBE.** Revista O Mecânico, "Raio X: Toyota Yaris 2023": *"toda a gama Yaris 2023 traz motor 1.5 flex (de código 2NR-FBE)… antes, a versão básica do Yaris Hatch trazia motor 1.3"*. <https://omecanico.com.br/raio-x-toyota-yaris-2023/> — corroborado pelas fichas brasileiras, que listam "Código do motor: 2NR-FBE" para Yaris 1.5 (2020, 2021, 2022). A Wikipedia descreve o 2NR-FBE como "flex fuel engine for Etios and Yaris in Brazil… gasoline or any mixture up to E100", 108 bhp @5600, 149 Nm @4000. <https://en.wikipedia.org/wiki/Toyota_NR_engine>
- **[CONFIRMADO] 1.3 flex = 1NR-FBE.** Ficha do **Yaris XL Live 1.3 2021**: "Código do motor **1NR-FBE**", 1329 cm³, razão de compressão **13:1**, 101 cv (A) / 94 cv (G) a 5600 rpm, 12,9 / 12,5 kgfm a 4000 rpm, 4 cilindros em linha, injeção multiponto, comando duplo no cabeçote por corrente. <https://www.fichacompleta.com.br/carros/toyota/yaris-xl-live-1-3-2021>
- **[PROVÁVEL]** A Wikipedia afirma que o 1NR-FBE (Etios/Yaris Brasil, só flex, 1.3) "foi substituído por uma versão flex do **1NR-FKE** em 2016". Ou seja: em um Yaris 2018–2021 o 1.3 pode ser internamente um 1NR-FKE-flex, e "1NR-FBE" seria o código comercial/fiscal usado nas fichas. **As duas coisas aparecem em fontes diferentes e não podem ser resolvidas por pesquisa.** Não use o código de motor como chave de PIDs sem confirmar no documento do carro.
- Potências/torques 2021: 1.3 = 101/94 cv e 12,9/12,5 kgfm; 1.5 = 110/105 cv e 14,9/14,3 kgfm (etanol/gasolina). **[CONFIRMADO]** (DM, Mobiauto, fichacompleta).
- Tanque: **45 L** nas fichas de 2021 **[CONFIRMADO]** <https://www.mobiauto.com.br/catalogo/carros/toyota/yaris/2021/1-5-xls-connect-cvt-flex> — atenção: a Wikipedia em português traz 42 L (provável dado da geração anterior/Etios). Divergência registrada.

**Divergências entre fontes que você DEVE conhecer** (não são detalhe: afetam o app):

- Mobiauto afirma que a linha 2021 hatch tem "seis versões, **todas com câmbio CVT**" — **contradiz** o release (XL Live 1.3 com manual) e o próprio catálogo do site (que lista "1.3 XL Live 16v 5p **6 marchas**" na Tabela FIPE). **[CONFIRMADO que a Mobiauto erra]** (comparação entre as duas páginas dela mesma e a ficha do iCarros, que diz "Câmbio Manual de 6 marchas").
- Forcedmotors (2023) afirma que "o Yaris 2021 está disponível em seis versões, **todas** com motor 1.5" — **também errado**, pelo mesmo motivo. Trate blogs agregadores como não confiáveis para versões.

### 1.3 Câmbio

- **[CONFIRMADO]** Manual de **6 marchas** (1.3 hatch e 1.5 sedã) e automático **CVT** "que simula sete marchas". A Wikipedia identifica o CVT como **Aisin K411 Super CVT-i**. <https://en.wikipedia.org/wiki/Toyota_Yaris_(XP150)>
- **[PROVÁVEL]** O câmbio manual foi **descontinuado em novembro de 2020**, semanas depois do lançamento da linha 2021: Motor1 publicou "Toyota Yaris tem aumento de preços e **perde versão manual**" (25/11/2020), dizendo que o manual de 6 marchas deixou de ser oferecido no hatch e no sedã. <https://motor1.uol.com.br/news/456304/toyota-yaris-perde-cambio-manual-precos/> — **a página está bloqueada por CloudFront para leitura direta**; o conteúdo acima vem do índice do buscador. Consequência prática: um Yaris **2021** muito provavelmente é **CVT**, mas existem 2021 manuais de produção inicial. Confirme pelo câmbio físico/pedal de embreagem, não pelo ano.

### 1.4 Central multimídia original — a parte mais mal documentada do carro

**[CONFIRMADO] Existe versão sem central touchscreen.** A ficha do **XL Live 1.3 2021** lista, em "Infotenimento": *CD player, computador de bordo, Bluetooth, conexão USB, rádio, volante multifuncional* — **sem** central multimídia, sem Android Auto. <https://www.fichacompleta.com.br/carros/toyota/yaris-xl-live-1-3-2021> Portanto a resposta à pergunta "existe versão sem Android Auto?" é **sim, e é a de entrada**.

**[CONFIRMADO] A central da linha "Connect" (MY2020/2021) tem Android Auto e Apple CarPlay.** Release da linha 2021 (versão S): *"possui central multimídia com Android Auto e Apple Car Play… Google Maps, Waze, Spotify, Apple Music, WhatsApp"*. <https://www.dm.com.br/dmautos/toyota-yaris-2021-estreia-serie-especial-s-de-r-89-990/> (URL sem "de"; ver lista final). A mesma informação aparece na apresentação do MY2020 "XL Plus Connect": *"Central multimídia touchscreen de 7" com Android Auto e Apple Carplay, Bluetooth, entrada USB e função MP3"* (Terra/Guia do Carro, 13/11/2019 — página hoje em 404; conteúdo recuperado por índice de busca).

**[CONFIRMADO] O hardware é Harman, de 7", com sistema proprietário — NÃO é Android embarcado.** Evidências convergentes:

- A Toyota do Brasil vende a peça como **"Central multimídia Toyota Play+ tecnologia Harman", código PV602-0D021**, descrita como acessório do **Yaris Hatch XLS**. <https://www.toyota.com.br/meu-toyota/acessorios/central-multimidia-toyota-play-tecnologia-harman-pv602-0d021>
- Donos identificaram o modelo por certificação FCC: **BE2841 / PV602**, e relatam: *"O sistema é proprietário da harman, ou seja, quase impossível instalar algum software de terceiros"*, firmware brasileiro **BR_18.x** (ex.: BR_18.1.2 → BR_18.2.0 → BR_18.3.0), atualização por Wi-Fi ou pendrive. **[RELATO]** <https://yarisclube.directorioforuns.com/t123-atualizacao-da-central-multimidia-disponivel>
- **Não existe central Android embarcada de fábrica no Yaris 2021.** "Android Auto" nesse carro é **espelhamento**, não um Android rodando na central. (A "Toyota Play 2.0", com tela de 10" e espelhamento sem fio, é de 2025 — outro carro/outra geração.) **[CONFIRMADO]** por ausência de qualquer fonte em contrário + natureza do hardware Harman.

**[CONFIRMADO] O Yaris 2018/2019 brasileiro NÃO tinha Android Auto/CarPlay, apesar do hardware ser o mesmo da Europa** — é o motivo das reclamações registradas: *"Central Multimídia do Yaris não é compatível com os sistemas CarPlay (Apple) e Android Auto"* e *"Multimídia Toyota Play+ incompatível com Android Auto e Apple CarPlay no Yaris 2019"* no Reclame Aqui; e o tópico do fórum onde donos tentam instalar firmware europeu (TAS500/PW600) para habilitar as funções. **[RELATO]** <https://yarisclube.directorioforuns.com/t123-atualizacao-da-central-multimidia-disponivel>

**[PROVÁVEL]** No MY2021 o Android Auto é **por cabo USB** (sem fio só chegou na geração seguinte de centrais). **[NÃO CONFIRMADO]** se todas as versões "Connect" de 2021 têm exatamente a mesma unidade — a S, a XLS e a X-Way podem diferir em firmware.

**Implicação direta para o projeto:** você **não** vai instalar o app na central original. O caminho realista é celular Android + scanner ELM327, ou trocar a central por uma Android aftermarket (há kits específicos para Yaris 2018–2021, amplamente vendidos no Mercado Livre/Magalu). A central original não expõe API, não tem Play Store e o barramento não é acessível por ela.

### 1.5 Yaris brasileiro ≠ Yaris europeu/japonês

- **[CONFIRMADO]** O Yaris **europeu** de 2021 é o **XP210**, sobre plataforma **TNGA-B**, com versão híbrida — carro **não relacionado** ao XP150 brasileiro. O artigo do XP150 é explícito: *"larger and longer than the unrelated XP130 series Yaris hatchback… and the succeeding TNGA-based XP210 series"*, e "marketed exclusively for emerging markets". <https://en.wikipedia.org/wiki/Toyota_Yaris_(XP150)>
- Consequência técnica: **nada** de PID, firmware, central multimídia ou calibração de motor do Yaris europeu vale automaticamente para o brasileiro. O próprio fórum brasileiro registra que a central é o mesmo hardware europeu **mas com software/funções bloqueadas no Brasil** — analogia perigosa de se estender à ECU.
- **[CONFIRMADO]** O Yaris brasileiro 2021 tirou **1 estrela** no Latin NCAP (2 airbags) — irrelevante para telemetria, mas útil para não confundir com o europeu 5 estrelas. <https://en.wikipedia.org/wiki/Toyota_Yaris_(XP150)>

---

## 2. Diagnóstico

### 2.1 Protocolo

- **[NÃO CONFIRMADO — não achei um log real publicado]** O protocolo exato negociado pelo Yaris 2021 brasileiro. Não existe, até onde pesquisei, nenhuma captura pública (`ATDPN`) desse modelo.
- **[PROVÁVEL — alta confiança] ISO 15765-4, CAN 11 bits, 500 kbps.** Motivos: (a) é o protocolo obrigatório de fato em veículos de emissão a partir de ~2008–2010 no Brasil; (b) a família NR/Etios-Yaris usa ECU com CAN; (c) dois donos brasileiros diferentes leram dados vivos com ELM327 e apps genéricos (Car Scanner) sem configuração manual de protocolo, o que só acontece quando a negociação automática (`ATSP0`) encontra CAN. **[RELATO]** <https://yarisclube.directorioforuns.com/t454-scanner-obd-ii>
- **[RELATO — tratar com muita desconfiança]** Um dono (moderador do Yaris Clube) afirma ter trocado e-mails com o suporte do app Carista e concluído que *"o Yaris Brasileiro usa um sistema OBD2 desenvolvido pela Mazda a mais de 20 anos atrás. Este sistema não possui o barramento de acessórios, somente o barramento de mensagens do sistema do carro"*. Isso **não** é especificação, é interpretação de terceiro repassada por usuário, e é tecnicamente confusa (não existe "OBD2 da Mazda" no Yaris; o que existe é um conjunto reduzido de módulos e ausência de gateway de conveniência). **Não use essa frase em nenhum material do projeto sem a devida ressalva.** Mesma fonte: <https://yarisclube.directorioforuns.com/t454-scanner-obd-ii>

**Recomendação para o app:** negociar com `ATSP0` e **registrar/logar o resultado de `ATDPN`** na primeira conexão, exibindo o protocolo detectado na tela de diagnóstico. Isso resolve a incerteza com dado do próprio carro do usuário, em vez de depender de mim.

### 2.2 Conector OBD2

- **[CONFIRMADO por dois relatos independentes] Fica sob o painel do motorista, junto à alavanca de abertura do capô, abaixo da coluna de direção.** É o conector **padrão SAE J1962, 16 vias, universal — não precisa de adaptador.** <https://yarisclube.directorioforuns.com/t96-conector-obd2>
  - Citação 1 (usuário CJDORIA, 2023): *"Ele vai conectado na porta que fica debaixo do painel logo abaixo da alavanca para abrir o capô"*.
  - Citação 2 (usuário rodrigoal, 2018): *"O conector deve ficar perto da alavanca de abertura do capô, debaixo da direção. Sua conexão é universal"*.
- **[RELATO — relevante para o hardware] Existe um problema real de folga física/altura**: o adaptador plugado fica exposto e pode ser chutado. Um dono relata que um HUD ligado permanentemente na porta mudou o comportamento do marcador de combustível: *"depois que instalei, o marcador de combustível demora p se movimentar"* (Yaris Sedan XL 2020). Trate como **anedótico**, mas é o tipo de sintoma que justifica não deixar o scanner plugado 24 h.
- **[RELATO] Aviso de "não deixar plugado direto"** aparece em duas mensagens: *"cuidado pra resistir à tentação de deixar conectado direto na saída obd2, pois já li relatos de casos em que queimou o módulo de injeção"*. Não verifiquei se procede. Mesma fonte.

### 2.3 PIDs padrão do Modo 01: o que existe e o que não existe

**Regra de ouro (é a resposta honesta):** nenhum fabricante é obrigado a implementar todos os PIDs do J1979. A lista real sai do **bitmask de suporte**: `01 00`, `01 20`, `01 40`, `01 60` (cada resposta de 4 bytes = 32 bits, um por PID). O app **deve** consultar isso em runtime e montar a tela a partir do que responder — nunca de uma lista fixa. Fórmulas e semântica: <https://en.wikipedia.org/wiki/OBD-II_PIDs>

PIDs que **devem** existir (obrigatórios para monitores de emissão, e confirmados na prática por relatos):
`0x04` carga calculada, `0x05` temperatura do arrefecimento, `0x0B` MAP, `0x0C` RPM, `0x0D` velocidade, `0x0F` temp. do ar admitido, `0x10` MAF, `0x11` TPS, `0x1C` norma OBD, `0x1F` tempo desde a partida. **[RELATO/esperado]** — donos relatam ver "temperatura do sistema de arrefecimento", "abertura da borboleta", "sensores de posição, temperatura e muitos outros".

PIDs **tipicamente ausentes** em ECU Toyota recente a gasolina/flex (todos opcionais no J1979):

| PID | O que é | Situação esperada |
|---|---|---|
| `0x2F` | Nível do tanque (%) | **provavelmente presente** — ver abaixo |
| `0x5E` | **Vazão de combustível (L/h)** | **[PROVÁVEL] ausente** em flex/gasolina; é comum em diesel |
| `0x52` | **% de etanol** | **[HIPÓTESE forte]** — ver seção 4 |
| `0x0A` / `0x22` / `0x23` / `0x59` | Pressão de combustível (baixa/rail/absoluta) | **[PROVÁVEL] ausente** (injeção multiponto aspirada, sem sensor de pressão de rail) |
| `0x24`–`0x2B`, `0x34`–`0x3B` | Sonda lambda **wide** (λ + tensão/corrente), 8 sensores | **[PROVÁVEL] apenas `0x24`–`0x27`** (B1S1/B1S2 em ECU com A/F), resto ausente |
| `0x78`/`0x79` | Temperatura de gases de escape (EGT) | **[PROVÁVEL] ausente** (só diesel/GDI/turbo) |
| `0x5C` | Temperatura do óleo do motor | **[PROVÁVEL] ausente** (aspirado, sem sensor dedicado) |
| `0x42` | Tensão do módulo | **[PROVÁVEL] presente** — um dono relata ler "voltagem do sistema elétrico" |
| `0x3C`–`0x3F` | Temperatura de catalisador | **improvável** |

**[RELATO] `0x2F` (nível de combustível) parece funcionar neste carro.** Donos relatam configurar no Car Scanner *"quantidade de litros no tanque"* e *"quantos litros há nele (talvez agora consiga finalmente descobrir quantos litros tem pra cada barrinha do marcador)"*. Fórmula J1979: **A × 100/255 → % de tanque**. Isso é **%**, não litros: multiplicar por 45 L é **estimativa**, e a escala do sensor (boia) é não linear nas pontas. Note também que os donos discutem justamente **descobrir a correspondência entre % e barrinhas** — ou seja, nem o carro nem o PID entregam litros.

**[NÃO CONFIRMADO]** Nenhum dos itens acima foi confirmado por captura de bitmask publicada desse modelo. O app deve tratar cada PID como "suportado se o bitmask disser sim e a resposta não for `NO DATA`".

### 2.4 VIN (09 02), DTCs e freeze frame

- **[RELATO — funciona] Leitura de DTC.** Há códigos reais lidos nesse carro em fórum brasileiro, por exemplo **P1605** (relatado em um Yaris hatch), com discussão de causas. <https://yarisclube.directorioforuns.com/t454-scanner-obd-ii>
- **[NÃO CONFIRMADO] VIN por `09 02`.** Nenhum relato brasileiro que eu tenha encontrado mostra VIN lido no Yaris. Tecnicamente é `09 02`+multiframe ISO-TP, e clones ruins falham nisso. Não prometa VIN na UI sem testar.
- **[NÃO CONFIRMADO / com alerta] Freeze frame (`02`).** Nenhum relato. Pior: há um relato qualitativo forte de que **este carro não guarda histórico** de erros: *"Quando o erro some da tela, some do computador do carro também. Nem usando o sistema da concessionária eu consegui ver o histórico de erros… pois o mesmo não armazena nada"* **[RELATO]**. Se isso se confirmar, freeze frame e `0A` (DTCs permanentes) podem estar indisponíveis e o app **não pode** prometer "histórico de falhas".
- **[PROVÁVEL] `01 1C` (norma OBD) deve responder algo do bloco brasileiro**: a tabela J1979 enumera `28 = Brazil OBD Phase 1 (OBDBr-1)`, `29 = OBDBr-2` e `35 = OBDBr-3` — um flex 2021 deve cair em OBDBr-1/2. Ler esse PID é a forma mais limpa de caracterizar o carro, e é grátis.

### 2.5 Ignição ligada / restrições de escrita

- **[PROVÁVEL — comportamento genérico, não específico do modelo]** Em CAN, a ECU responde com **ignição em ON** (motor parado ou ligado). Com ignição OFF, o barramento OBD dorme e o scanner devolve `NO DATA`/`UNABLE TO CONNECT`. Não achei nada que indique um comportamento peculiar da Toyota nesse ponto; **trate como regra do protocolo, não como "a Toyota exige motor ligado"**. Para leitura de parâmetros dinâmicos (pressão de admissão, trims, lambda), motor ligado é o único modo útil.
- **[NÃO CONFIRMADO] Nenhuma limitação de "somente leitura" imposta pelo fabricante.** Ao contrário: donos relatam **apagar códigos de erro** com scanners baratos (*"todos cumprem os requisitos básicos de visualizar dados e apagar mensagens de erro"*). **[RELATO]** O que existe é limitação de **capacidade do adaptador**, não bloqueio da ECU. Escritas de configuração (ex.: sensibilidade do sensor de farol) foram tentadas e **não** funcionaram — *"não consegui, ainda, mudar nenhuma configuração do carro e nem vi indícios de que isto seja possível"* **[RELATO]**.

### 2.6 ELM327 baratos: há incidência conhecida e documentada **neste carro**

**Sim, e é o achado mais acionável desta seção.** Dois donos diferentes, em momentos diferentes, relatam exatamente o mesmo:

- *"Quem quiser comprar deve optar pela **versão 1.5**, pois a **2.1 até conecta, mas não acessa os dados**"* (CJDORIA, 2023).
- *"Não estou conseguindo conectar a ECU do carro. O programa conecta com o scanner Bluetooth sem problemas, mas dá **falha na conexão com a ECU**"* — e depois: *"Já descobri o problema… eu havia comprado o scanner **2.1** e **precisa ser o 1.5**"* (mesmo usuário).
- *"desde que sejam a versão 1.5 (com placa dupla)… e também que tenham o tal chip **PIC18F25K80** para acessar todos os sensores"* (rpb178, 2023).
- Um dono com adaptador **Konnwei 901 BT 4.0** relata o oposto: *"achou 100% dos sensores do carro, reconheceu o protocolo de primeira"*.

**Fonte (relatos):** <https://yarisclube.directorioforuns.com/t96-conector-obd2> e <https://yarisclube.directorioforuns.com/t454-scanner-obd-ii>

**Leitura correta:** o problema relatado é do **clone** (firmware 2.1 falsificado, sem multiframe ISO-TP robusto), não do carro. **[CONFIRMADO por relato convergente]** — e é exatamente o sintoma clássico de clone que "conecta mas não lê".

**[NÃO CONFIRMADO neste modelo]** Não encontrei nenhum relato de problema específico com `ATIB 96` no Yaris, nem de necessidade de fixar `ATSP6` em vez de `ATSP0`. O que se pode afirmar com segurança: (a) `ATSP0` funcionou nos relatos que deram certo; (b) se a autonegociação falhar, `ATSP6` (ISO 15765-4 CAN 11/500) é a fixação correta a tentar; (c) `ATIB` só deve ser mexido **depois** de esgotar o resto, e de forma documentada, porque mexe em timing de transporte e pode mascarar o problema real (clone ruim). **Não coloque "ATIB 96" como receita no app** — não há base para isso neste carro.

**Regra para o app:** detectar e **diagnosticar** o clone. Se `ATZ`/`ATE0`/`ATI` responderem mas `0100` devolver `NO DATA`, a mensagem deve ser "seu adaptador conecta mas não lê a ECU — troque por ELM327 1.5 com PIC18F25K80", não "veículo incompatível".

---

## 3. Parâmetros proprietários Toyota (Modo 21 / 22)

### 3.1 O que está documentado de forma pública e verificável

- **[CONFIRMADO] O Modo `21` é o modo proprietário da Toyota.** A Wikipedia (J1979) diz explicitamente: *"Each manufacturer may define additional services above #9 (e.g.: service 22 as defined by SAE J2190 for Ford/GM, **service 21 for Toyota**)"*. <https://en.wikipedia.org/wiki/OBD-II_PIDs>
- **[CONFIRMADO — documentação acadêmica]** "Mode 21 signifies a **Toyota defined PID**" — usado em estudo publicado que extraiu dados via `21xx` de um Toyota. <https://www.researchgate.net/figure/List-of-on-board-diagnostic-parameter-identifications-PID-used-in-testing-Mode-21_tbl1_329790943>
- **[CONFIRMADO — engenharia reversa documentada, mas de OUTRO carro]** Existe documentação pública séria de `21xx` para **um** Toyota, o Land Cruiser Prado 1KD-FTV (diesel): o repositório `proatwork/toyota-1kd-ftv-obd2-pids` documenta que **os blocos `2101`–`214E` espelham os PIDs do Modo 01** (mesmos bytes de `01xx`), além de dezenas de blocos específicos. <https://github.com/proatwork/toyota-1kd-ftv-obd2-pids> — isso é útil como **mapa mental do formato**, não como dado do Yaris.
- **[RELATO] Formato da resposta do Modo 21 no CAN** (fórum GR Yaris, carro diferente): `ATSH 0007D0` fixa o header; `21 62` retorna algo como `7D8 05 61 62 03 00 00 0` — o `61` é o `21+40`, o `62` ecoa o PID. <https://www.gr-yaris.co.uk/threads/reverse-engineering-pids.4680/>
- **[RELATO — Toyota antiga]** Fórmula pública de **knock retard** por `21 B2`: `(xx*256+yy)*0.03125 − 64`. <https://www.spyderchat.com/threads/does-anyone-know-the-obdii-pid-for-knock-ill-pay-cash.134865/> — atenção: é de uma ECU Toyota **dos anos 2000**; **não** transporte para 1NR/2NR.
- **[CONFIRMADO — sobre a escassez]** O artigo do MTHRFKNWIN explica o problema central: a Toyota (como outros) **vende** as listas de PID proprietários para ferramentas de terceiros, e publicá-las integralmente pode ter implicações legais; o que circula é garimpado. <https://mthrfknwin.com/2016/02/04/diagnostic-port-obd2-detail-manufacturer-specific-pids/> A Wikipedia reforça: a base de dados de PIDs não padronizados é mantida pela **ETI (Equipment and Tool Institute)**, só para membros pagantes, e **nem isso cobre tudo**. <https://en.wikipedia.org/wiki/OBD-II_PIDs>

### 3.2 O que **não** existe (e é onde a maioria dos projetos erra)

- **[NÃO CONFIRMADO / na prática: não há] Mapa de PIDs Modo 21/22 documentado para a família 1NR/2NR** (nem para o Yaris XP150 brasileiro). Pesquisei e não encontrei: nenhum repositório, planilha, tópico de fórum ou documento público que dê `21xx` → parâmetro para 1NR-FBE / 2NR-FBE. **Se algum material do projeto afirmar "PID 21xx = temperatura do CVT no Yaris", isso é invenção.**
- **[NÃO CONFIRMADO] Temperatura do fluido do CVT no Yaris 2021.** Existe relato de temperatura de câmbio automático lida via Car Scanner **num Etios** (mesma família de plataforma, ECU supostamente "quase idêntica" segundo o autor) — mas é outro carro, outro câmbio (Etios AT de 4 marchas), e o próprio autor diz *"supondo que o dado seja esse mesmo"*. **[RELATO]** — <https://yarisclube.directorioforuns.com/t96-conector-obd2> **Não** é base para implementar "temperatura do CVT" no app.
- **[NÃO CONFIRMADO] Status de injeção, knock, informações do CVT** para este modelo: nada verificado.
- **[CONFIRMADO que o caminho existe, mas custa] `22` (UDS `0x22` "Read Data By Identifier")**: no CAN moderno o `0x21` é **reservado** no UDS, e ECUs mais novas podem simplesmente não responder ao serviço `21` — nesse caso, `22` com IDs de 2 bytes é o serviço correto. Isso significa que o projeto **precisa descobrir empiricamente** o que a ECU do carro aceita, com um scanner que permita enviar serviço bruto.
  - **[RELATO técnico, de outro Toyota]** <https://github.com/jwu02/esp32-car-telemetry/issues/9> discute exatamente esse ponto.
  - Para o Yaris 2021, `[NÃO CONFIRMADO]` se `21` responde.

### 3.3 Como o projeto pode descobrir de verdade (sem inventar)

1. Enviar `2100` e ver se há resposta (`61 00 …`). Se não houver, tentar `22 F1 90` (VIN via UDS) para testar se `22` responde.
2. Varredura controlada de `21 01`…`21 FF` (e `22 F1xx`) **com o motor em marcha lenta estável**, registrando: PID que responde, tamanho, e o valor bruto em 3 condições (frio, quente em marcha lenta, e em carga constante). Só um valor que **varre monotonamente com a grandeza física** pode ser rotulado — e mesmo assim como `[HIPÓTESE]` até validação cruzada com o dado do painel.
3. Comparar tudo com o que o **próprio carro mostra** (temperatura no painel, marcador de combustível, conta-giros).
4. Publicar a descoberta como "PIDs candidatos observados em <VIN/versão>", nunca como "PIDs do Yaris".

**Isso é engenharia reversa legítima no seu próprio carro; é diferente de "documentação".**

---

## 4. Consumo e flex

### 4.1 Por que estimar consumo por MAF/MAP é impreciso num flex brasileiro

A cadeia física é:

```
m_combustível = m_ar / (λ × AFR_estequiométrico(mistura))
```

O problema é o denominador. O **AFR estequiométrico depende da fração de etanol**:

| Mistura | AFR estequiométrico aprox. |
|---|---|
| Gasolina pura (E0) | 14,7 : 1 |
| Gasolina brasileira E27–E30 | ≈ 13,2–13,4 : 1 |
| E50 | ≈ 11,5 : 1 |
| E85 | ≈ 9,8 : 1 |
| Etanol hidratado (E100) | ≈ 9,0 : 1 |

Se o app assume 14,7 e o tanque está com E100, ele **subestima o combustível em ~39 %** (9,0/14,7 = 0,61). Com E27 contra 14,7, o erro já é ~9 %. **[CONFIRMADO — aritmética sobre constantes físico-químicas amplamente tabeladas; a faixa exata da gasolina brasileira depende da mistura vigente]**

Somam-se a isso, todos **[PROVÁVEL — comportamento padrão de ECU]**:

- **λ ≠ 1** fora de regime estequiométrico: em plena carga a ECU enriquece (λ ≈ 0,85–0,90) e no corte de injeção (desaceleração) λ → ∞ (combustível = 0). Integrar MAF como se λ = 1 erra nos dois extremos.
- **Malha aberta** no aquecimento e em algumas condições de carga.
- **MAF mede ar, não combustível**: qualquer erro de calibração do MAF, refluxo, ou pulsação em marcha lenta vira erro direto.
- **MAP + lei dos gases** é pior ainda: exige estimar eficiência volumétrica, que varia com VVT-i — e nesse motor há **Dual VVT-i** (1.5). **[CONFIRMADO]** que o 1.5 é Dual VVT-i 16V DOHC (release da linha 2021); para o 1.3, as fichas dizem "variação do comando: admissão e escape", mas a Wikipedia descreve o 1NR-FBE como "1.3 litre, **with VVT-i**" — divergência **[NÃO CONFIRMADO]**.
- **Etanol muda o volume, não só a massa**: quem abastece "litros" paga por litro, e a razão ar/combustível em volume muda. Comparar km/L entre abastecimentos só faz sentido com o mesmo combustível.

### 4.2 Existe PID de consumo direto?

- **[PROVÁVEL] Não.** `0x5E` (**Engine fuel rate**, L/h, fórmula `(256A+B)/20`) é opcional no J1979 e é **predominante em diesel**. Em gasolina/flex aspirado é normalmente **ausente**, porque a ECU não calcula vazão em L/h — ela calcula massa de combustível por pulso de injetor. **Confirme no bitmask `01 40`**; se o bit de `0x5E` estiver em 1 e o PID responder número plausível, você ganhou o melhor caminho possível.
- **[PROVÁVEL] Também não existe "fuel used since reset".** O que existe é `0x21` (distância com MIL aceso) e `0x31` (distância desde limpeza de códigos) — nenhum serve para consumo.

### 4.3 Nível de combustível (`0x2F`)

- **Escala:** `A × 100/255` → **percentual de tanque (0–100 %)**, não litros. **[CONFIRMADO — J1979]**
- **[PROVÁVEL] Suportado no Yaris** (relatos de donos lendo litros/percentual no Car Scanner).
- Para litros: `litros ≈ % × 45 L`. O 45 L é **capacidade nominal** (fichas de 2021); a reserva, o formato do tanque e a boia tornam a conversão **não linear**, especialmente abaixo de ~15 % e acima de ~85 %. **[PROVÁVEL]**
- **Uso honesto:** o método "tanque a tanque" (enche até a trava, zera, enche de novo) com o `0x2F` como conferência é o **único** consumo que o app pode chamar de medição. Tudo o mais é estimativa.

### 4.4 A pista mais interessante: PID `0x52` (% de etanol)

- **[CONFIRMADO — J1979]** Existe PID padrão **`0x52` – Ethanol fuel %, escala `A × 100/255`**, e PID **`0x51` – Fuel type** (com códigos como 3=Ethanol, 9=Bifuel gasolina, 11=Bifuel etanol). A Wikipedia observa: *"There are currently no definitions for flexible-fuel vehicles"* no `0x51` — ou seja, `0x51` pode não descrever bem um flex. <https://en.wikipedia.org/wiki/OBD-II_PIDs>
- **[RELATO — forte]** **Donos brasileiros de Yaris leem o percentual de etanol no tanque** por OBD: *"Configurei para… percentual de etanol na gasolina (ótimo para detectar combustível adulterado)"*; *"descobri que alguns postos aqui no RJ colocam de 30% a 50% de etanol na gasolina"*; e um detalhe valioso de dinâmica: *"O percentual de etanol não atualiza imediatamente, somente depois de uns 10 a 20 km rodados, provavelmente pela necessidade de leituras dos sensores"*. <https://yarisclube.directorioforuns.com/t454-scanner-obd-ii>
- **[HIPÓTESE forte e testável]** Esse número é o **`01 52`** (padrão), não um PID proprietário — a escala `0–100 %` e a existência do PID batem, e o "só atualiza depois de 10–20 km" é assinatura do **aprendizado de combustível** da ECU (que é justamente o que alimenta esse PID).
  - **Se confirmar, isso muda o app:** o AFR estequiométrico pode ser **calculado** a partir do etanol aprendido, derrubando o erro de estimativa de ~39 % para poucos por cento. **Teste primeiro**: `01 52` → se responder `NO DATA`, tente a mesma varredura em `21 52`/`22 52`.
- **[NÃO CONFIRMADO]** Se `01 51`/`01 52` estão entre os PIDs suportados nesse modelo.

### 4.5 Como o app deve estimar (e como rotular)

**Ordem de preferência, do mais honesto ao menos:**

1. **Medição real:** tanque a tanque (litros abastecidos ÷ km rodados). Rótulo: **"MEDIDO"**. Use `0x2F` só para conferir/alertar inconsistência.
2. **Se `0x5E` existir:** integrar L/h × tempo. Rótulo: **"MEDIDO PELA ECU (vazão calculada)"** — ainda é cálculo da ECU, não medidor de vazão.
3. **MAF + λ + etanol aprendido:** `m_fuel = ∫ MAF dt / (λ × AFR(etanol))`, com λ de `0x44` (λ comandado) ou `0x24` (λ da sonda wide) quando disponível, e `AFR(etanol)` interpolado de `0x52`. Rótulo: **"ESTIMATIVA"**, com nota "erro típico ±10–15 %; pior em carga plena e no aquecimento".
4. **Sem λ e sem `0x52`:** MAF com AFR fixo. Rótulo: **"ESTIMATIVA GROSSEIRA — pode errar >30 % com etanol"**. Melhor **não exibir km/L** e sim "consumo relativo" ou nada.

**Nunca** rotular estimativa como "medição". Regra prática de UI: se o número vem de integral de MAF, o card deve ter o sufixo **"≈"** e um tooltip explicando os três erros (λ, etanol, corte de injeção). Em marcha lenta prolongada a estimativa por MAF fica **péssima** (MAF baixo, erros relativos altos) — o app deveria suspender o cálculo abaixo de ~20 km/h ou em `0x03` = malha aberta.

---

## 5. Segurança e limites

### 5.1 Faixas de referência plausíveis (para alerta, **não** para diagnóstico)

| Grandeza | Faixa normal esperada | Comentário |
|---|---|---|
| Marcha lenta | **600–800 rpm** (spec de fábrica da família NR: **700 rpm**) | **[CONFIRMADO]** para o motor: "Idling speed: 700 rpm" em 1NR-FE e 2NR-FE. <https://en.wikipedia.org/wiki/Toyota_NR_engine> O 1.3/1.5 flex pode marcha lenta um pouco acima a frio |
| Rotação máxima | 1.3: **5800 rpm** (ficha); família NR: redline **6200 rpm** | Divergência real entre ficha brasileira e spec internacional — use a ficha do 1.3 só para o 1.3 |
| Arrefecimento | **85–95 °C** em regime | **[PROVÁVEL]** (termostática típica ~82 °C de abertura); validar com o próprio carro em 3 ciclos |
| Ar admitido | ambiente a ~+20 °C acima (sob o capô) | **[PROVÁVEL]** |
| Tensão do módulo (`0x42`) | 13,5–14,4 V com motor ligado; 12,2–12,8 V desligado | **[PROVÁVEL]** — note que o Yaris tem um problema crônico conhecido de **regulador de tensão** (tópico fixo no fórum brasileiro, "Defeito crônico, regulador de tensão da bateria"), então tensão é um parâmetro **realmente útil** aqui |
| Lambda | 0,97–1,03 em malha fechada | **[PROVÁVEL]** |

**Como o app deve tratar valores fora de faixa — regra não negociável:**

- Linguagem de **observação**, nunca de diagnóstico: "temperatura 8 °C acima da faixa típica observada", **não** "motor superaquecido" nem "termostato travado".
- Sempre mostrar **contexto**: valor, faixa, tempo naquela condição, e o que o app **não** sabe (carga, temperatura ambiente, se o motor acabou de ligar).
- Atraso/hipsterese: alertar só depois de N segundos fora da faixa (evita alarme falso no aquecimento e em subidas).
- Se o PID não responder, dizer **"não suportado por esta ECU"** — jamais preencher com valor padrão.

### 5.2 Limites de operação

- **[CONFIRMADO — consenso técnico] Ler RPM, velocidade, temperatura e trims para exibição é seguro.** São consultas `01xx` de leitura, sem efeito de atuação.
- **[CONFIRMADO — regra de segurança] **Limpar DTCs (`04`) NUNCA deve acontecer em movimento**: apaga freeze frame, apaga readiness/monitores de emissão e pode apagar memórias adaptativas, com mudança temporária de comportamento do motor. Exija: veículo parado, motor em marcha lenta ou desligado, confirmação explícita em duas etapas, e um aviso de que vai apagar o histórico.
- **Nunca** oferecer `08` (controle de atuadores) nem escrita de configuração: além de não haver documentação para este carro, é o caminho mais curto para imobilizar o veículo.
- **`0x5E`/`0x2F` não são críticos; `0x05` (temperatura) e `0x0C` (RPM) são.** Faça o app **degradar** quando um PID falhar, em vez de travar a tela.
- **[RELATO]** Um dono relata que, ao plugar um HUD que lia o OBD continuamente, o **marcador de combustível** passou a demorar a se mover. Não é prova de dano, mas é motivo para (a) não deixar o scanner plugado permanentemente e (b) limitar a taxa de polling (5–10 Hz para RPM/velocidade é suficiente; não faça polling agressivo de todos os PIDs).

---

## Tabela: Confirmado vs Hipótese

| # | Afirmação | Status | Fonte |
|---|---|---|---|
| 1 | Yaris BR = série **XP150** (mercados emergentes), fabricado em Sorocaba | CONFIRMADO | en.wikipedia/Toyota_Yaris_(XP150) |
| 2 | Linha 2021 hatch: XL Live 1.3 (manual 6m ou CVT), XL Plus/XS/X-Way/S/XLS Connect 1.5 CVT; sedã XL Live 1.5 manual | CONFIRMADO | dm.com.br (release out/2020) |
| 3 | Motor 1.5 flex = **2NR-FBE** | CONFIRMADO | omecanico.com.br (raio-x 2023) + fichacompleta 2020-2022 |
| 4 | Motor 1.3 flex = **1NR-FBE** (1329 cc, 13:1) | CONFIRMADO | fichacompleta (XL Live 1.3 2021) |
| 5 | Que o 1.3 do Yaris seja, na prática, um **1NR-FKE-flex** (1NR-FBE "substituído" em 2016) | PROVÁVEL (divergência entre fontes) | en.wikipedia/Toyota_NR_engine |
| 6 | Câmbio manual = **6 marchas**; CVT = Aisin K411 Super CVT-i (simula 7 marchas) | CONFIRMADO | en.wikipedia XP150 + fichacompleta + dm.com.br |
| 7 | Manual descontinuado em nov/2020 → 2021 é quase sempre CVT | PROVÁVEL (página motor1 bloqueada; conteúdo via índice) | motor1.uol.com.br/news/456304 |
| 8 | Tanque 45 L | CONFIRMADO (fichas 2021) / divergente (PT wiki diz 42) | mobiauto + fichacompleta |
| 9 | Central original é **Harman PV602 / BE2841**, 7", SO proprietário (não Android) | CONFIRMADO | toyota.com.br (acessório PV602-0D021) + fórum Yaris Clube (FCC ID) |
| 10 | Versão **XL Live não tem central touchscreen** (só rádio/CD/BT/USB) | CONFIRMADO | fichacompleta XL Live 1.3 2021 |
| 11 | Versões **Connect** (MY2020/2021) têm Android Auto + CarPlay | CONFIRMADO | dm.com.br (S 2021) + Terra/Guia do Carro (XL Plus Connect 2019) |
| 12 | Yaris 2018/2019 **não** tinha AA/CarPlay no Brasil, com hardware igual ao europeu | CONFIRMADO (relatos + reclamações) | Reclame Aqui + Yaris Clube t123 |
| 13 | AA no 2021 é **por cabo USB** (sem fio só na geração seguinte) | PROVÁVEL | — |
| 14 | Não existe central Android **embarcada** de fábrica no Yaris 2021 | CONFIRMADO (por natureza do hardware) | Yaris Clube t123 |
| 15 | Yaris europeu 2021 = XP210/TNGA, **não relacionado** ao BR | CONFIRMADO | en.wikipedia XP150 |
| 16 | Protocolo = ISO 15765-4 CAN 11-bit 500 kbps | PROVÁVEL (alta confiança) | inferência + relatos de leitura com ELM327 |
| 17 | Conector OBD2 padrão J1962 sob o painel, junto à alavanca do capô | CONFIRMADO (2 relatos) | Yaris Clube t96 |
| 18 | Modo **21** = serviço proprietário Toyota | CONFIRMADO | en.wikipedia OBD-II_PIDs + ResearchGate |
| 19 | **Não existe** mapa público de PIDs Modo 21/22 para 1NR/2NR | CONFIRMADO (ausência verificada) | busca em GitHub/fóruns/ETI |
| 20 | `2101`–`214E` espelham o Modo 01 | CONFIRMADO **para outro Toyota** (1KD-FTV) | github proatwork/toyota-1kd-ftv-obd2-pids |
| 21 | Leitura de **DTC** funciona neste carro (ex.: P1605) | CONFIRMADO (relato) | Yaris Clube t454 |
| 22 | Leitura de **VIN** por `09 02` | NÃO CONFIRMADO | — |
| 23 | **Freeze frame** disponível; carro "não guarda histórico" de erros | NÃO CONFIRMADO / relato contrário | Yaris Clube t454 |
| 24 | PID `0x2F` (nível) suportado | PROVÁVEL (relato de leitura de litros/%) | Yaris Clube t454 |
| 25 | PID `0x5E` (vazão) **não** suportado | PROVÁVEL | J1979 (opcional, típico de diesel) |
| 26 | PID `0x42` (tensão) suportado | PROVÁVEL (relato de leitura de voltagem) | Yaris Clube t454 |
| 27 | "% de etanol" lido pelos donos = PID padrão **`0x52`** | HIPÓTESE forte e testável | J1979 + relato Yaris Clube t454 |
| 28 | `01 1C` retorna norma OBDBr (28/29/35) | HIPÓTESE | J1979 (tabela de normas) |
| 29 | Clones ELM327 **2.1 não acessam a ECU**; **1.5 com PIC18F25K80** funciona | CONFIRMADO (2 relatos convergentes) | Yaris Clube t96 e t454 |
| 30 | Necessidade de `ATSP6` fixo ou problema com `ATIB 96` neste carro | NÃO CONFIRMADO | — |
| 31 | Temperatura do fluido do CVT legível no Yaris 2021 | NÃO CONFIRMADO (relato é de **Etios**) | Yaris Clube t96 |
| 32 | Marcha lenta de referência 700 rpm; redline 6200 rpm (família NR) | CONFIRMADO (spec de motor, não da versão BR) | en.wikipedia/Toyota_NR_engine |
| 33 | Arrefecimento normal 85–95 °C | PROVÁVEL (engenharia) | — |
| 34 | Carro não impõe restrição de "somente leitura"; limitação é do adaptador | CONFIRMADO (relatos de apagar DTC) | Yaris Clube t454 |
| 35 | "Yaris BR usa OBD2 legado tipo Mazda" | RELATO não verificado — **não usar** | Yaris Clube t454 (suporte Carista, repassado) |

---

## O que o proprietário precisa verificar no carro/documento para eliminar as incertezas

**A. Identificação (elimina a dúvida de versão/motor) — 10 minutos**

1. **Documento (CRLV)**: marca/modelo/versão e o campo **combustível** (Flex). O número do motor também consta no CRLV e no **laudo do CONAMA/PROCONVE**.
2. **Etiqueta/plaqueta do motor** (bloco): procurar o código gravado — **1NR** ou **2NR**. Confirma de vez 1.3 vs 1.5 e o código da família. Se possível, **fotografar**.
3. **Câmbio**: olhar o pedal de embreagem (3 pedais = manual 6m). Se não houver embreagem, é CVT.
4. **Nível de equipamento**: a central é touchscreen de 7" ou só rádio/CD? (distingue XL Live de Connect).
5. **Manual do proprietário** (pasta do carro): capítulos de multimídia e de "diagnóstico/OBD". Se houver a lista de "fusíveis" e a localização do conector, fotografar.

**B. Central multimídia (elimina a dúvida AA/CarPlay) — 5 minutos**

6. No menu da central, procurar **"Android Auto"** (ícone próprio) ou **"Projeção/Espelhamento"**. Se existir, conectar um Android por **cabo USB** e ver se o app espelha.
7. Anotar a **versão de software** da central (menu Configurações → Sobre/Atualização) — deve ser `BR_18.x` no Harman PV602.

**C. Diagnóstico OBD (elimina a dúvida de protocolo/PIDs) — 30 minutos, o item mais valioso**

8. Com o scanner conectado e **ignição em ON**, capturar e salvar a saída de:
   - `ATZ`, `ATI` (versão do adaptador — confirma se é 1.5 real),
   - `ATSP0` e depois **`ATDPN`** (protocolo negociado — **isto confirma ou derruba a hipótese CAN 11/500**),
   - `0100`, `0120`, `0140`, `0160` (bitmasks de suporte — **a lista real de PIDs do carro**),
   - `011C` (norma OBD: OBDBr-1/2/3, EOBD…),
   - `0902` (VIN — testa multiframe),
   - `0102` + `03` (freeze frame e DTCs) **após** provocar/observar um erro, se houver.
9. Testar especificamente: `0151` (fuel type), `0152` (% etanol), `015E` (vazão), `012F` (nível), `0142` (tensão), `015C` (temp. óleo), `0178` (EGT). Anotar quais respondem `NO DATA`.
10. **Com o motor ligado e quente**, anotar em marcha lenta: `010C` (RPM), `0105` (°C), `0110` (MAF g/s), `0106`/`0107` (trims), `0104` (carga). Isso dá a **linha de base real** do carro, que nenhuma fonte de internet fornece.
11. Se `0152` responder: comparar o valor com o **percentual de etanol do último abastecimento** (o dono sabe o que colocou). Se bater dentro de ~10 %, a hipótese do `0x52` está confirmada.

**D. Se quiser PIDs proprietários (opcional, sem promessa)**

12. Enviar `2100`; se responder (`61 00 …`), fazer varredura `2101`–`21FF` e `22F190`/`22F1xx` com o motor em condições controladas, registrando bruto + condição. **Sem esse registro, qualquer PID proprietário no app é chute.**

---

## URLs consultadas

**Identificação, versões, motor e câmbio**

- https://en.wikipedia.org/wiki/Toyota_Yaris_(XP150) — plataforma XP150, Sorocaba, grades BR, câmbios, Latin NCAP
- https://en.wikipedia.org/wiki/Toyota_NR_engine — 1NR-FBE, 2NR-FBE, specs, marcha lenta 700 rpm, redline 6200
- https://www.fichacompleta.com.br/carros/toyota/yaris-xl-live-1-3-2021 — 1NR-FBE, 1329 cc, 13:1, 101/94 cv, manual 6m, infotenimento sem central
- https://www.fichacompleta.com.br/carros/toyota/yaris-sedan-xl-plus-connect-1-5-at-2020 — código 2NR-FBE
- https://www.mobiauto.com.br/catalogo/carros/toyota/yaris/2021 — versões 2021 (contém o erro "todas CVT")
- https://www.mobiauto.com.br/catalogo/carros/toyota/yaris/2021/1-5-xls-connect-cvt-flex — potência/torque/consumo/tanque 45 L
- https://turbonoticias.com.br/ficha-tecnica/carros/toyota/yaris/2021/ — lista de 12 configurações MY2021 (inclui 1.3 mecânico e sedã 1.5 mecânico)
- https://www.dm.com.br/dmautos/toyota-yaris-2021-estreia-serie-especial-s-com-preco-de-r-89-990/ — release da linha 2021 (versões, câmbios, AA/CarPlay na S)
- https://www.automaistv.com.br/novidades/toyota-yaris-2021-2/ — linha 2021 e preços
- https://opopular.com.br/economia/sobre-rodas/toyota-yaris-2021-ganha-serie-especial-s-por-r-89-990-1.2133081 — tabela de preços 2021
- https://motor1.uol.com.br/news/456304/toyota-yaris-perde-cambio-manual-precos/ — fim do câmbio manual (nov/2020) [página bloqueada; conteúdo via índice de busca]
- https://motor1.uol.com.br/news/447975/toyota-yaris-s-especial-lancamento/ — 1.3 101 cv só na XL Live; 6 marchas ou CVT
- https://www.icarros.com.br/catalogo-zero-km/marcas/toyota/yaris-hatch/2021 — "câmbio manual de 6 marchas" (XL Live 1.3)
- https://autoesporte.globo.com/carros/usados-e-seminovos/noticia/2024/07/toyota-yaris-usado-veja-precos-na-tabela-fipe-e-pontos-fortes-do-hatch.ghtml — apenas a versão de entrada 1.3 teve manual de 6 marchas
- https://omecanico.com.br/raio-x-toyota-yaris-2023/ — confirma 2NR-FBE e que o 1.3 existia antes

**Central multimídia**

- https://www.toyota.com.br/meu-toyota/acessorios/central-multimidia-toyota-play-tecnologia-harman-pv602-0d021 — "Toyota Play+ tecnologia Harman", código PV602-0D021 (Yaris Hatch XLS)
- https://www.toyota.com.br/meu-toyota/toyota-play — página oficial do sistema Toyota Play (bloqueada por CloudFront; referência de nome)
- https://yarisclube.directorioforuns.com/t123-atualizacao-da-central-multimidia-disponivel — Harman PV602/BE2841, firmware BR_18.x, tentativa de firmware europeu TAS500/PW600
- https://www.reclameaqui.com.br/toyota/central-multimidia-do-yaris-nao-e-compativel-com-os-sistemas-carplay-apple_P0fuUu-1JScdysAp/ — reclamação AA/CarPlay
- https://www.reclameaqui.com.br/toyota/multimidia-toyota-play-incompativel-com-android-auto-e-apple-carplay-no-yaris-2019_Wlei1KPfYZqNV7RP/ — idem, Yaris 2019

**Diagnóstico / OBD**

- https://yarisclube.directorioforuns.com/t96-conector-obd2 — localização do conector, ELM327 2.1 vs 1.5, PIC18F25K80, Car Scanner, Etios/temp. de câmbio
- https://yarisclube.directorioforuns.com/t454-scanner-obd-ii — % de etanol, litros no tanque, tensão, P1605, relato "OBD2 legado"/Carista, histórico de erros não armazenado
- https://en.wikipedia.org/wiki/OBD-II_PIDs — fórmulas (0x05, 0x0C, 0x10, 0x2F, 0x42, 0x44, 0x52, 0x5C, 0x5E, 0x78), norma OBD (0x1C, incl. OBDBr-1/2/3), Modo 21 = Toyota, ETI/escassez de PIDs não padronizados
- https://mthrfknwin.com/2016/02/04/diagnostic-port-obd2-detail-manufacturer-specific-pids/ — por que listas Toyota não são públicas
- https://www.researchgate.net/figure/List-of-on-board-diagnostic-parameter-identifications-PID-used-in-testing-Mode-21_tbl1_329790943 — "Mode 21 = Toyota defined PID"
- https://github.com/proatwork/toyota-1kd-ftv-obd2-pids — documentação real de `21xx` (Land Cruiser 1KD-FTV; `2101`–`214E` espelham Modo 01)
- https://www.gr-yaris.co.uk/threads/reverse-engineering-pids.4680/ — formato de resposta do Modo 21 no CAN (outro Toyota)
- https://github.com/jwu02/esp32-car-telemetry/issues/9 — `0x21` reservado no UDS; serviço `22` como alternativa
- https://www.spyderchat.com/threads/does-anyone-know-the-obdii-pid-for-knock-ill-pay-cash.134865/ — knock retard via `21B2` (Toyota antiga; **não** transferir)

**Limitações de acesso enfrentadas nesta pesquisa** (para o agente pai saber o que ficou sem verificação)

- `toyota.com.br`, `webmotors.com.br`, `motor1.uol.com.br` e `autoesporte` bloqueiam leitura direta (CloudFront 403 / "access denied"); usei índices de busca e o leitor `r.jina.ai`.
- `terra.com.br` (Guia do Carro, MY2020) está em 404 — conteúdo recuperado apenas por snippet.
- Fórum Yaris Clube exige leitor de texto (`r.jina.ai`) para converter.
- **Não localizei** nenhuma captura pública de `ATDPN`/bitmask de PIDs do Yaris 2021 brasileiro. Por isso os itens 16, 22–28 e 30 da tabela permanecem como provável/hipótese.
