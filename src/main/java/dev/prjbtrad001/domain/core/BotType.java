package dev.prjbtrad001.domain.core;

public enum BotType {
  BTCUSDT,
  ETHUSDT,
  XRPUSDT,
  BNBUSDT;

  public boolean isValid(String symbol) {
    if (symbol == null) {
      return false;
    }
    try {
      BotType.valueOf(symbol.toUpperCase());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
