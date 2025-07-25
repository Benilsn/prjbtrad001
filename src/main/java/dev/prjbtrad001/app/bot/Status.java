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
  private boolean isLong;         // true if the bot is currently holding a position
  private double purchasePrice;   // entry price
  private Instant purchaseTime;   // (ADD) timestamp of the buy
  private double quantity;        // (ADD) quantity of asset bought

  // Status
  private double lastPrice;
  private double lastRsi;
  private double lastSmaShort;
  private double lastSmaLong;       // (ADD) SMA21 if you're using it
  private double actualSupport;
  private double actualResistance;
  private double lastVolume;
  
}
