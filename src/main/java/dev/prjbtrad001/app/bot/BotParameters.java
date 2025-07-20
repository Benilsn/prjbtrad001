package dev.prjbtrad001.app.bot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.infra.validation.ValidSymbol;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.FormParam;
import lombok.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class BotParameters{

  @JsonIgnore @Getter(AccessLevel.NONE) private DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("pt", "BR"));
  @JsonIgnore @Getter(AccessLevel.NONE) private DecimalFormat formatter = new DecimalFormat("#,###.00", symbols);

  @ValidSymbol
  @FormParam("symbol")
  private BotType botType = BotType.BTCUSDT;

  @Pattern(regexp = "\\d+[mhd]", message = "Interval must be like 1m, 5m, 1h")
  @FormParam("interval")
  private String interval;

  // Current state
  private boolean isLong;
  private double purchasePrice;

  @FormParam("stopLossPercent")
  private double stopLossPercent;

  @FormParam("takeProfitPercent")
  private double takeProfitPercent;

  // Technical parameters
  @Min(0) @Max(100)
  @FormParam("rsiPurchase")
  private double rsiPurchase;

  @Min(0) @Max(100)
  @FormParam("rsiSale")
  private double rsiSale;

  @DecimalMin(value = "0.0", inclusive = false, message = "Volume multiplier must be greater than 0")
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