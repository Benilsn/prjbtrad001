package dev.prjbtrad001.infra.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TradingConfig {

  @ConfigProperty(name = "bot.mock-trading", defaultValue = "true")
  private boolean mockTrading;

  public boolean isMockTrading() {
    return mockTrading;
  }

  public void setMockTrading(boolean mockTrading) {
    this.mockTrading = mockTrading;
  }

}
