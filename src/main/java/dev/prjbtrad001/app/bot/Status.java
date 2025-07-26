package dev.prjbtrad001.app.bot;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Status {

  // Current state
  private boolean isLong;
  private BigDecimal purchasePrice;
  private Instant purchaseTime;
  private BigDecimal quantity;

  // Status
  private BigDecimal lastPrice;
  private BigDecimal lastRsi;
  private BigDecimal lastSmaShort;
  private BigDecimal lastSmaLong;
  private BigDecimal actualSupport;
  private BigDecimal actualResistance;
  private BigDecimal lastVolume;
  
}
