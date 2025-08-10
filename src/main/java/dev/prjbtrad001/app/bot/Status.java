package dev.prjbtrad001.app.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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
  @Column(name = "totalPurchased", precision = 19, scale = 2)
  private BigDecimal totalPurchased;
  @Column(name = "averagePrice", precision = 19, scale = 2)
  private BigDecimal averagePrice;

  @Column(name = "quantity", precision = 19, scale = 8)
  private BigDecimal quantity;

  @Column(name = "profit", precision = 19, scale = 2)
  private BigDecimal profit;

  private LocalDateTime lastPurchaseTime;

  private BigDecimal trailingStopLevel;

  // Status
//  private BigDecimal lastPrice;
//  private BigDecimal lastRsi;
//  private BigDecimal lastSmaShort;
//  private BigDecimal lastSmaLong;
//  private BigDecimal actualSupport;
//  private BigDecimal actualResistance;
//  private BigDecimal lastVolume;
//

  public int isProfitPositive() {
    if (profit == null) {
      return 0;
    } else if (profit.compareTo(BigDecimal.ZERO) <= 0) {
      return -1;
    } else {
      return 1;
    }
  }

//  public String getFormattedTotalPurchase() {
//    if (totalPurchased == null) {
//      totalPurchased = BigDecimal.ZERO;
//    }
//    return FORMATTER2.format(totalPurchased.setScale(2, RoundingMode.UNNECESSARY));
//  }
//
//  public String getFormattedValueAtTheTimeOfLastPurchase() {
//    if (valueAtTheTimeOfLastPurchase == null) {
//      valueAtTheTimeOfLastPurchase = BigDecimal.ZERO;
//    }
//    return FORMATTER2.format(valueAtTheTimeOfLastPurchase.setScale(2, RoundingMode.UNNECESSARY));
//  }

  public String getFormattedQuantity() {
    if (profit == null) {
      profit = BigDecimal.ZERO;
    }
    return FORMATTER2.format(quantity.setScale(8, RoundingMode.UNNECESSARY));
  }
}
