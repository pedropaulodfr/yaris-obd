# Layouts do painel

Os **12 layouts aprovados no laboratório** (`docs/mockups`) estão implementados no app. A troca é feita no celular (*Estilo do painel*) e aplica na central em até 1 segundo, com o cabo conectado.

As imagens em [`docs/img/estilos2/`](../img/estilos2/) são renderizadas pelo **próprio código** que roda na central — não são mockups.

| Layout | Quando usar |
|---|---|
| **Agulhas** | Uso geral. Dois relógios com agulha + coluna de apoio |
| **Agulhas Grande** | Mostradores enormes dominando a tela; demais indicadores em faixa fina embaixo |
| **Fitas** | Leitura de precisão: escalas que rolam |
| **Cockpit** | Dirigir com foco em rotação (luzes de troca, SHIFT) |
| **Eco** | Economia: anel de eficiência e consumo |
| **Térmico** | Acompanhar aquecimento e faixas de operação |
| **Tendência** | Ver para onde os valores estão indo (últimos 30 s) |
| **Radar** | Leitura de conjunto normalizada em 6 eixos |
| **Diagnóstico** | Códigos de falha, MIL e saúde do enlace |
| **Viagem** | Computador de bordo: distância, tempo, médias |
| **Diurno** | Tema claro para sol forte |
| **HUD** | Só o essencial, em tamanho enorme |
| **Mosaico** | Cartões de tamanhos diferentes |

## Origem dos dados
| Campo | Fonte | Observação |
|---|---|---|
| RPM, velocidade, temperaturas, MAP, MAF, carga, acelerador, tensão, nível, etanol, avanço | PIDs OBD-II (Modo 01) | Medição da ECU |
| Consumo instantâneo/médio, autonomia | **Estimativa** a partir do MAF + teor de etanol (PID 0x52) | Rotulado como "est." nas telas |
| Distância da viagem, tempo, velocidade média | Integração no app | Derivado das leituras |
| Marcha | **Só no simulador** | Com scanner real aparece "—": o OBD padrão não expõe marcha |
| Estado da ventoinha | **Não disponível** | Mostrado como "—" no Térmico |
| Códigos de falha | Modo 03/07 | Lidos da ECU |

## Regras que valem em todos os layouts

1. **Leitura em menos de 1 segundo**: velocidade > rotação > temperaturas > resto.
2. **Tema escuro e contraste alto** (exceto o Diurno, proposital).
3. **Dígitos tabulares** — números não mudam de largura ao atualizar.
4. **Cor nunca é o único indicador**: sempre com rótulo e número.
5. **Indisponível ≠ zero**: sem leitura, aparece `—`.
6. **Aviso de dado simulado/velho** no topo, com texto.
7. **Sem animação decorativa**.

## Como adicionar um layout

1. Crie uma classe em `render/styles/` estendendo `StyleBase`.
2. Implemente `drawBody(...)`; cabeçalho, rodapé, avisos e primitivos (mostrador com agulha, fita, anel, sparkline, cartão) já vêm da base.
3. Registre no enum `DashboardStyle` e no `when` de `DashboardRenderer.create(...)`.
