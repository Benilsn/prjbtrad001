package dev.prjbtrad001.app.bot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.infra.validation.ValidSymbol;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class BotParameters {

  @Transient
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  private DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("pt", "BR"));

  @Transient
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  private DecimalFormat formatter = new DecimalFormat("#,###.00", symbols);

  @ValidSymbol(groups = Create.class, message = "Invalid bot type symbol")
  @NotNull(message = "Bot type cannot be null")
  @FormParam("symbol")
  private BotType botType;

  @NotBlank(message = "Interval cannot be blank")
  @Pattern(regexp = "^(1s|1m|3m|5m|15m|30m|1h|2h|4h|6h|8h|12h|1d|3d|1w|1M)$",
    message = "Invalid interval format. Must be one of: 1s, 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M")
  @FormParam("interval")
  @Column(name = "\"interval\"")
  private String interval;

  @NotNull(message = "Stop loss percentage cannot be null")
  @Min(value = 0, message = "Stop loss percentage must be greater than or equal to 0")
  @FormParam("stopLossPercent")
  private BigDecimal stopLossPercent;

  @NotNull(message = "Take profit percentage cannot be null")
  @Min(value = 0, message = "Take profit percentage must be greater than or equal to 0")
  @FormParam("takeProfitPercent")
  private BigDecimal takeProfitPercent;

  @NotNull(message = "RSI purchase value cannot be null")
  @Min(value = 0, message = "RSI purchase value must be greater than or equal to 0")
  @FormParam("rsiPurchase")
  private BigDecimal rsiPurchase;

  @NotNull(message = "RSI sale value cannot be null")
  @Min(value = 0, message = "RSI sale value must be greater than or equal to 0")
  @FormParam("rsiSale")
  private BigDecimal rsiSale;

  @NotNull(message = "Volume multiplier cannot be null")
  @Min(value = 0, message = "Volume multiplier must be greater than or equal to 0")
  @FormParam("volumeMultiplier")
  private BigDecimal volumeMultiplier;

  @NotNull(message = "SMA short period cannot be null")
  @Min(value = 0, message = "SMA short period must be greater than or equal to 0")
  @FormParam("smaShort")
  private int smaShort;

  @NotNull(message = "SMA long period cannot be null")
  @Min(value = 0, message = "SMA long period must be greater than or equal to 0")
  @FormParam("smaLong")
  private int smaLong;

  @NotNull(message = "Window resistance support cannot be null")
  @Min(value = 0, message = "Window resistance support must be greater than or equal to 0")
  @FormParam("windowResistanceSupport")
  private int windowResistanceSupport;

  @NotNull(message = "Purchase amount cannot be null")
  @Min(value = 0, message = "Purchase amount must be greater than or equal to 0")
  @FormParam("purchaseAmount")
  private BigDecimal purchaseAmount;

  @NotNull(message = "Purchase strategy cannot be null")
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


  public interface Create {
  }

  public interface Update {
  }
}