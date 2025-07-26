package dev.prjbtrad001.app.bot;

import jakarta.persistence.Embeddable;
import lombok.*;

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
  private double purchasePrice;
  private Instant purchaseTime;
  private double quantity;

  // Status
  private double lastPrice;
  private double lastRsi;
  private double lastSmaShort;
  private double lastSmaLong;
  private double actualSupport;
  private double actualResistance;
  private double lastVolume;
  
}
