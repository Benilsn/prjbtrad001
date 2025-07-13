package dev.prjbtrad001.domain.config;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class BotConfig {
  public double stopLoss;
  public double buyThreshold;
  public double sellThreshold;
  public int pollingInterval;
}