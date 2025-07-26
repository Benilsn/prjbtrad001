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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Getter
@Setter
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
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


  @FormParam("stopLossPercent")
  private BigDecimal stopLossPercent;

  @FormParam("takeProfitPercent")
  private BigDecimal takeProfitPercent;

  @FormParam("rsiPurchase")
  private BigDecimal rsiPurchase;

  @FormParam("rsiSale")
  private BigDecimal rsiSale;

  @FormParam("volumeMultiplier")
  private BigDecimal volumeMultiplier;

  @FormParam("smaShort")
  private int smaShort;

  @FormParam("smaLong")
  private int smaLong;

  @FormParam("windowResistanceSupport")
  private int windowResistanceSupport;

  @FormParam("purchaseAmount")
  private BigDecimal purchaseAmount;

  @FormParam("purchaseStrategy")
  private PurchaseStrategy purchaseStrategy;

//  public BotParameters() {
//    symbols.setDecimalSeparator(',');
//    symbols.setGroupingSeparator('.');
//    formatter.setParseBigDecimal(true);
//  }

//  public String getFormattedPurchasePrice(){
//    return formatter.format(new BigDecimal(purchasePrice).setScale(2, RoundingMode.UNNECESSARY));
//  }
//
//  public String getFormattedLastPrice() {
//    return formatter.format(new BigDecimal(lastPrice).setScale(2, RoundingMode.UNNECESSARY));
//  }

}