package dev.prjbtrad001.app.bot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.infra.validation.ValidSymbol;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.FormParam;
import lombok.*;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Getter
@Setter
@Builder
@Embeddable
@AllArgsConstructor
public class BotParameters{

  @Transient
  @JsonIgnore @Getter(AccessLevel.NONE)
  private DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("pt", "BR"));

  @Transient
  @JsonIgnore @Getter(AccessLevel.NONE)
  private DecimalFormat formatter = new DecimalFormat("#,###.00", symbols);

  @ValidSymbol
  @FormParam("symbol")
  private BotType botType = BotType.BTCUSDT;

  @Pattern(regexp = "^(1s|1m|3m|5m|15m|30m|1h|2h|4h|6h|8h|12h|1d|3d|1w|1M)$")
  @FormParam("interval")
  @Column(name = "\"interval\"")
  private String interval;

  // Current state
  private boolean isLong;
  private double purchasePrice;

  @FormParam("stopLossPercent")
  private double stopLossPercent;

  @FormParam("takeProfitPercent")
  private double takeProfitPercent;

  @FormParam("rsiPurchase")
  private double rsiPurchase;

  @FormParam("rsiSale")
  private double rsiSale;

  @FormParam("volumeMultiplier")
  private double volumeMultiplier;

  @FormParam("smaShort")
  private int smaShort;

  @FormParam("smaLong")
  private int smaLong;

  @FormParam("windowResistanceSupport")
  private int windowResistanceSupport;

  // Status
  private double lastPrice;
  private double lastRsi;
  private double lastSmaShort;
  private double actualSupport;
  private double actualResistance;


  public BotParameters() {
    symbols.setDecimalSeparator(',');
    symbols.setGroupingSeparator('.');
    formatter.setParseBigDecimal(true);
  }

//  public String getFormattedPurchasePrice(){
//    return formatter.format(new BigDecimal(purchasePrice).setScale(2, RoundingMode.UNNECESSARY));
//  }
//
//  public String getFormattedLastPrice() {
//    return formatter.format(new BigDecimal(lastPrice).setScale(2, RoundingMode.UNNECESSARY));
//  }

}