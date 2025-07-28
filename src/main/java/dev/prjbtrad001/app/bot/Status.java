package dev.prjbtrad001.app.bot;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static dev.prjbtrad001.app.utils.FormatterUtils.FORMATTER2;

@Getter
@Setter
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Status {

  // Current state
  private boolean isLong;
  private BigDecimal totalPurchased;
  private BigDecimal valueAtTheTimeOfLastPurchase;
  private BigDecimal quantity;
  private BigDecimal profit;
//  private String purchaseTime;

  // Status
//  private BigDecimal lastPrice;
//  private BigDecimal lastRsi;
//  private BigDecimal lastSmaShort;
//  private BigDecimal lastSmaLong;
//  private BigDecimal actualSupport;
//  private BigDecimal actualResistance;
//  private BigDecimal lastVolume;
//

  public String getFormattedTotalPurchase() {
    if (totalPurchased == null) {
      totalPurchased = BigDecimal.ZERO;
    }
    return FORMATTER2.format(totalPurchased.setScale(2, RoundingMode.UNNECESSARY));
  }

  public String getFormattedValueAtTheTimeOfLastPurchase() {
    if (valueAtTheTimeOfLastPurchase == null) {
      valueAtTheTimeOfLastPurchase = BigDecimal.ZERO;
    }
    return FORMATTER2.format(valueAtTheTimeOfLastPurchase.setScale(2, RoundingMode.UNNECESSARY));
  }

  public String getFormattedProfit() {
    if (profit == null) {
      profit = BigDecimal.ZERO;
    }
    return FORMATTER2.format(profit.setScale(2, RoundingMode.UNNECESSARY));
  }
}
