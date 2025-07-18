package dev.prjbtrad001.domain.config;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotConfig {

  private long interval; // Ex: 1m, 5m

  // Estado atual
  private boolean isLong;         // Está com posição ativa?
  private double purchasePrice;       // Último preço de compra
  private double stopLossPercent;   // Ex: 0.005 = -0.5%
  private double takeProfitPercent; // Ex: 0.012 = +1.2%

  // Estratégia / parâmetros técnicos
  private double rsiPurchase;         // Ex: 30
  private double rsiSale;          // Ex: 70
  private double volumeMultiplier; // Ex: 1.2
  private int smaShort;             // Ex: 9
  private int smaLong;             // Ex: 21
  private int windowResistanceSupport; // Ex: 10 candles

  // Último status
  private double ultimoPreco;
  private double ultimoRSI;
  private double ultimaMediaVolume;
  private double suporteAtual;
  private double resistenciaAtual;

}