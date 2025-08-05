# PrjBTrad001 - Bot de Trading de Criptomoedas

## Objetivo
O PrjBTrad001 é um bot de trading automatizado desenvolvido para operar em mercados de criptomoedas. O objetivo principal é executar estratégias de trading baseadas em análise técnica, com foco em maximizar lucros e minimizar riscos em diferentes condições de mercado.

## Como o bot funciona

### Análise de Mercado
O bot analisa continuamente o mercado usando uma combinação de indicadores técnicos:
- **RSI (Índice de Força Relativa)**: Identifica condições de sobrecompra e sobrevenda
- **Médias Móveis**: SMA9, SMA21, EMA8, EMA21, EMA50, EMA100 para confirmar tendências
- **Bandas de Bollinger**: Identifica extremos de preço e volatilidade
- **Análise de Volume**: Detecta aumento de interesse no mercado
- **Suporte e Resistência**: Identifica níveis de preço significativos

### Estratégias de Compra
O bot implementa as seguintes condições para executar ordens de compra:
- RSI abaixo do valor configurado (condição de sobrevenda)
- Preço tocando suporte ou banda inferior de Bollinger
- Volume acima da média em momentos críticos
- Momentum positivo em condições favoráveis

### Estratégias de Venda
As decisões de venda são baseadas em:
- **Take Profit**: Venda quando o lucro atinge o percentual configurado
- **Stop Loss**: Limita perdas ao percentual configurado
- **Timeout de Posição**: Fecha posições após um período específico
- **Análise Técnica**: RSI em sobrecompra, preço tocando resistência, etc.

### Adaptação a Tendências de Mercado
O bot identifica tendências de baixa (downtrend) e ajusta seu comportamento:
- **Em Downtrend**:
    - Usa take profit e stop loss reduzidos (metade do normal)
    - Executa compras menores (50% do valor padrão)
    - Fecha posições mais rapidamente (1/3 do timeout normal)
    - Exige condições especiais para compra (RSI extremo, suporte forte, pico de volume)

## Configurações do Bot
O bot é altamente configurável através de parâmetros:
- **RSI de Compra e Venda**: Níveis para detectar sobrevenda e sobrecompra
- **Take Profit e Stop Loss**: Percentuais para saída automática
- **Multiplicador de Volume**: Fator para identificar picos de volume
- **Estratégia de Compra**: Valor fixo ou percentual do saldo
- **Valor de Compra**: Montante ou percentual para cada operação

## Gerenciamento de Risco
- **Stop Loss Dinâmico**: Protege lucros quando a posição já está lucrativa
- **Análise de Volatilidade**: Ajusta tamanho da posição com base na volatilidade
- **Intervalos Mínimos**: Previne operações excessivas em curto período

## Tecnologias Utilizadas
- Java
- Maven
- API de Exchange (Binance)
- Persistência de dados
- Sistema de logging para análise de desempenho

## Status do Projeto
Este projeto está em desenvolvimento ativo, com melhorias contínuas sendo implementadas para otimizar a estratégia de trading e o gerenciamento de risco.

---

**Nota**: Trading de criptomoedas envolve riscos significativos. Este bot é uma ferramenta experimental e seus resultados podem variar de acordo com as condições de mercado.