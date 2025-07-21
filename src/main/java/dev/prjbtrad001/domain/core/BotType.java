package dev.prjbtrad001.domain.core;

public enum BotType {
  BTCUSDT,
  ETHUSDT,
  XRPUSDT;

  public boolean isValid(String symbol) {
    for (BotType type : BotType.values()) {
      if (type.name().equalsIgnoreCase(symbol)) {
        return true;
      }
    }
    return false;
  }

}