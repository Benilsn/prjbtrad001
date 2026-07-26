# PrjBTrad001 — Bot de trend following com backtest

Bot de trading de criptomoedas construído em torno de **uma** estratégia simples e
verificável: cruzamento de duas médias móveis exponenciais (EMA), operando em
timeframes altos.

> ⚠️ **Aviso**: trading envolve risco real de perda. Este projeto é experimental
> e educacional. Nenhum backtest garante resultado futuro. Não é recomendação de
> investimento.

---

## Por que esta estratégia

A versão anterior deste projeto tentava *scalping* com ~10 indicadores empilhados
(RSI, MACD, Bollinger, ATR, OBV, estocástico, padrões de candle e um sistema de
pontuação). O resultado prático foi que **o lucro era diluído pelas taxas**: mirar
movimentos de 0,1–0,3% pagando ~0,2% de ida e volta não fecha a conta.

A reconstrução inverte essa lógica:

| Scalping (antigo) | Trend following (atual) |
|---|---|
| Muitos trades por dia | **Poucos trades** — a taxa vira ruído |
| Alvo 0,1–0,3% | Alvo de vários por cento |
| Timeframe 1m–5m | **4h / 1d** |
| ~10 indicadores | **1 indicador** (EMA) |
| Sem backtest | **Backtest é o centro do projeto** |

Trend following **perde na maioria dos trades** (esperado: 30–40% de acerto). O
lucro vem de poucas operações grandes que pagam as várias perdas pequenas. Isso é
característica da estratégia, não defeito.

---

## Como funciona

**Entrada**: a EMA rápida cruza **acima** da EMA lenta.
**Saída**: a EMA rápida cruza **abaixo** da lenta, **ou** o stop-loss é atingido.

Nada além disso. A mesma lógica (`strategy/EmaCrossStrategy`) alimenta tanto o
backtest quanto a operação ao vivo — muda apenas a origem dos candles.

### Decisão só em candle fechado

O runner descarta o candle em formação e decide sobre o último candle **fechado**.
Isso evita *lookahead* (agir sobre dados que ainda não aconteceram) também no modo
ao vivo, não só no backtest.

---

## Backtest

É a peça mais importante: transforma "acho que funciona" em "os números dizem".

Acesse **`/backtest`**, escolha par, timeframe, períodos das EMAs, stop, número de
candles e a taxa. O motor:

1. baixa o histórico da Binance (paginado, até 2000 candles);
2. roda a estratégia com o `BarSeriesManager` do ta4j — que garante timing de
   entrada/saída livre de lookahead;
3. calcula o resultado em BRL com **taxa cobrada nas duas pontas**.

Métricas exibidas:

- **Retorno da estratégia** e capital final
- **Buy & hold** no mesmo período — o juiz final; se você não bate o "comprar e
  segurar", a estratégia não tem vantagem
- **Drawdown máximo** — quanta dor você teria aguentado
- **Nº de trades** — deve ser baixo (dezenas, não centenas)
- **Win rate** e **profit factor**
- **Curva de capital** e a lista de trades

---

## Modo de execução: paper trading

Esta versão **não envia ordens reais**. Toda execução é simulada
(`paper/PaperExecutor`) contra uma carteira BRL virtual, com a mesma taxa da
Binance descontada nas duas pontas — o P&L do paper reflete o mesmo atrito de
custo do mundo real.

A execução real na Binance foi deliberadamente adiada: primeiro valide a
estratégia no backtest e no paper.

---

## Portal

- **`/`** — dashboard: resumo (bots, rodando, carteira paper, P&L realizado e não
  realizado, taxas) e um card por bot
- **`/backtest`** — formulário e resultado
- **`/bots/create`** — criação/edição do bot

Tema neon/cyberpunk, minimalista.

---

## Stack

- Java 21 · Quarkus 3.24 · Qute (templates) · PostgreSQL + Hibernate Panache
- **ta4j 0.22.6** — indicadores e motor de backtest
  (⚠️ 0.22.7+ é compilado para Java 25 e **não** funciona neste build)
- Chart.js (CDN) para a curva de capital
- API pública da Binance (klines não exigem chave de API)

---

## Rodando

### Docker (recomendado — é assim que vai pro servidor)

```bash
docker compose up -d
```

Sobe a aplicação + PostgreSQL. Abra <http://localhost:8080>. Não é necessária
chave da Binance (só endpoints públicos).

O Postgres guarda os dados num volume nomeado, então `docker compose down`
preserva tudo. **`docker compose down -v` apaga** — é o único comando que
destrói o histórico.

### Local, sem Docker

Precisa de um PostgreSQL acessível. Por padrão o app procura
`jdbc:postgresql://localhost:5432/btrad001` (usuário/senha `btrad`), ou defina
`DB_URL`, `DB_USER` e `DB_PASSWORD`.

```bash
./mvnw quarkus:dev
```

### ⚠️ Antes de subir num servidor

**A Binance bloqueia IPs de datacenter em regiões restritas** (EUA, Malásia,
Ontário) com HTTP 451 — inclusive nos endpoints públicos. Teste na máquina
antes de instalar qualquer coisa:

```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://api.binance.com/api/v3/ticker/price?symbol=BTCBRL"
```

`200` = liberado. `451` = troque a região (São Paulo funciona).

O compose publica as portas em `127.0.0.1` de propósito: **a aplicação não tem
autenticação nenhuma**. Num servidor, acesse por túnel SSH
(`ssh -L 8080:localhost:8080 usuario@servidor`) em vez de expor a porta.

## Histórico de execuções

Cada compra e venda vira uma linha em `trade_record`, com preço, quantidade,
taxa, lucro e o **motivo da saída** (`EMA_CROSS` ou `STOP_LOSS`), além do
contexto da estratégia no momento (timeframe e períodos das EMAs). Isso é o que
permite analisar depois o que realmente aconteceu — o `BotStatus` guarda só
agregados.

| Endpoint | Uso |
|---|---|
| `/trades` | JSON. Aceita `?botId=`, `?symbol=`, `?limit=` |
| `/trades/csv` | Mesmos dados em CSV, para planilha ou análise |

Ou direto no banco:

```bash
docker compose exec db psql -U btrad -d btrad001 -c "SELECT symbol, reason, COUNT(*), ROUND(AVG(profitpct),2) FROM trade_record WHERE side='SELL' GROUP BY 1,2;"
```

## n8n (análise de notícias — futuro)

Já vem declarado no compose, mas não sobe por padrão:

```bash
docker compose --profile n8n up -d
```

Fica em <http://localhost:5678>. Dentro da rede do compose ele enxerga o
histórico em `http://app:8080/trades` e o banco em `db:5432` — sem precisar
expor porta nenhuma.

Vale lembrar o que discutimos: sinal de notícia/sentimento serve como **filtro
ou veto** sobre uma estratégia já validada, nunca como gerador de sinal, e
**nunca deve alimentar o backtest** — o modelo já viu o futuro daquele período,
o que invalida a simulação.

### Configuração (`application.yaml`)

```yaml
bot:
  paper:
    initial-balance: 4000.00   # saldo inicial simulado
    fee-rate: 0.001            # 0,1% por ponta
  strategy:
    defaults:
      timeframe: 4h
      ema-fast: 9
      ema-slow: 21
      stop-loss-percent: 5
      order-size-brl: 100
```

---

## Método recomendado

1. **Backtest primeiro.** Rode a estratégia em 500–2000 candles no 4h/1d.
2. **Compare com buy & hold.** Se não bater, não vale o risco nem o esforço.
3. **Desconfie de resultado bom demais.** Ajustar parâmetros até o número ficar
   bonito é *overfitting* — você decorou o passado, não descobriu uma vantagem.
   Valide em períodos e pares diferentes.
4. **Depois paper trading.** Deixe rodando e compare com o esperado.
5. Só então considere capital real.

## Fora de escopo (por enquanto)

- Execução real na Binance (a abstração está pronta para plugar)
- Filtro de notícias/sentimento via LLM — só faz sentido sobre uma base já
  validada, e **nunca** alimentando o backtest (o modelo foi treinado com o
  futuro daquele período, o que invalida a simulação)
- Otimização automática de parâmetros
