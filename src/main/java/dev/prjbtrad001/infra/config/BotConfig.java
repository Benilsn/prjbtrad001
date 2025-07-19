package dev.prjbtrad001.infra.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class BotConfig {

  @JsonIgnore
  @Getter(AccessLevel.NONE)
  private DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("pt", "BR"));
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  private DecimalFormat formatter = new DecimalFormat("#,###.00", symbols);

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
  private double lastPrice;
  private double lastRsi;
  private double lastSmaShort;
  private double actualSupport;
  private double actualResistance;


  public BotConfig() {
    symbols.setDecimalSeparator(',');
    symbols.setGroupingSeparator('.');
    formatter.setParseBigDecimal(true);
  }

  public String getFormattedPurchasePrice(){
    return formatter.format(new BigDecimal(purchasePrice).setScale(2, RoundingMode.UNNECESSARY));
  }

  public String getFormattedLastPrice() {
    return formatter.format(new BigDecimal(lastPrice).setScale(2, RoundingMode.UNNECESSARY));
  }

}