# PrjBTrad001 - Bot de Trading de Criptomoedas

## Objetivo
O PrjBTrad001 é um bot de trading automatizado desenvolvido para operar em mercados de criptomoedas. O objetivo principal é executar estratégias de trading baseadas em análise técnica, com foco em maximizar lucros e minimizar riscos em diferentes condições de mercado.

## Como o bot funciona
Este bot de trading implementa uma estratégia técnica avançada que utiliza múltiplos indicadores para identificar oportunidades de compra e venda em mercados de criptomoedas, adaptando-se dinamicamente às condições de mercado.

### 1. Estratégia de Compra

O bot avalia as seguintes condições para gerar sinais de compra:

### Condições Primárias:
- **RSI em sobrevenda**: Compra quando o RSI está abaixo do valor configurado (geralmente 30)
- **Proximidade ao suporte**: Identifica níveis de suporte e compra quando o preço está próximo
- **Volume adequado**: Verifica se o volume está acima de 80% da média
- **Momentum positivo**: Analisa se há impulso de alta no movimento do preço

### Análise Especial para Tendências de Baixa:
Em mercados de baixa, o bot usa uma estratégia modificada que requer pelo menos 3 dos seguintes sinais:
- RSI em condição de sobrevenda extrema
- Proximidade ao suporte técnico
- Volume adequado (>80% da média)
- Indicação de reversão (momentum positivo)

### Sistema de Pontuação para Compra:
- RSI em sobrevenda: 1.3 pontos
- Tendência de alta: 1.0 ponto
- Volume adequado: 0.6 ponto
- Preço próximo ao suporte: 0.8 ponto
- Momentum positivo: 0.5 ponto
- Baixa volatilidade: 0.3 ponto

Uma compra é executada quando:
- A pontuação total é maior que o limiar definido
- Existem pelo menos 3 sinais positivos
- O preço está próximo a um nível de suporte ou banda inferior de Bollinger

## 2. Estratégia de Venda

O bot utiliza uma abordagem multi-camada para vendas:

### Gatilhos de Saída Automática:
- **Take Profit**: Vende quando o lucro atinge o percentual configurado
- **Stop Loss**: Limita perdas quando o preço cai abaixo do percentual definido
- **Trailing Stop**: Ajusta dinamicamente o nível de saída para proteger lucros
- **Timeout**: Fecha posições abertas há muito tempo com lucro mínimo

### Sinais Técnicos de Venda:
- **RSI em sobrecompra**: Vende quando o RSI está acima do valor configurado
- **Reversão de tendência**: Detecta quando EMAs e SMAs indicam mudança para baixa
- **Contato com resistência**: Identifica quando o preço atinge níveis de resistência
- **Topo das Bandas de Bollinger**: Vende quando o preço toca a banda superior

### Saídas de Emergência:
- **Queda abrupta**: Detecta quedas rápidas de preço com alta volatilidade
- **Reversão de RSI**: Identifica quando o RSI reverte de valores extremos

## 3. Estratégia para Tendências de Baixa

O bot identifica tendências de baixa quando pelo menos 2 destas condições são verdadeiras:
- EMA de 8 períodos abaixo da EMA de 21 períodos
- Inclinação de preço negativa
- Preço atual abaixo da linha média das Bandas de Bollinger

Em tendências de baixa, o bot:
- Reduz o tamanho das posições (30-60% do valor normal)
- Aplica take profits mais agressivos
- Utiliza stop losses mais próximos
- Exige sinais mais fortes para entrar em novas posições

## 4. Análise de Indicadores Técnicos

### Indicadores Primários:
- **RSI (14)**: Identifica condições de sobrecompra/sobrevenda
- **EMAs**: 8, 21, 50 e 100 períodos para análise de tendências
- **SMAs**: 9 e 21 períodos para confirmação de tendências
- **Bandas de Bollinger**: Identifica volatilidade e extremos de preço

### Indicadores Secundários:
- **Momentum**: Calcula a força da tendência atual
- **Volatilidade**: Mede a intensidade das oscilações de preço
- **Níveis de Suporte/Resistência**: Identificados a partir dos mínimos/máximos recentes
- **Inclinação de Preço**: Determina a direção e força da tendência atual

### Cálculo de Intensidade de Sinal:
O bot calcula a força do sinal de compra baseado em:
- Valor extremo de RSI
- Proximidade ao suporte técnico
- Posição nas Bandas de Bollinger
- Direção do momentum

Esta análise resulta em um multiplicador que ajusta o volume de compra entre 30% e 150% do valor base.

## 5. Gestão de Risco Adaptativa

- **Tamanho de posição dinâmico**: Ajusta o valor de compra baseado na força do sinal
- **Take Profit variável**: Adapta-se à volatilidade do mercado
- **Stop Loss ajustável**: Mais próximo em mercados voláteis
- **Trailing Stop**: Protege lucros em operações bem-sucedidas
- **Timeout de posição**: Evita capital preso em posições estagnadas

Esta estratégia combina análise técnica tradicional com controle de risco dinâmico para operar em diversos cenários de mercado.
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
- **Intervalos Mínimos**: Previne operações excessivas em curto periodo

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