package dev.prjbtrad001.app.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Status {

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

  public int isProfitPositive() {
    if (profit == null) {
      return 0;
    } else if (profit.compareTo(BigDecimal.ZERO) <= 0) {
      return -1;
    } else {
      return 1;
    }
  }

}
