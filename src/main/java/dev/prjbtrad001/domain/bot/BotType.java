package dev.prjbtrad001.domain.bot;

/**
 * Supported Binance spot trading pairs (quote currency: BRL).
 */
public enum BotType {
  BTCBRL,
  ETHBRL,
  SOLBRL,
  BNBBRL,
  XRPBRL,
  DOGEBRL,
  LTCBRL,
  ADABRL,
  LINKBRL,
  AVAXBRL;

  public static boolean isValid(String symbol) {
    for (BotType type : values()) {
      if (type.name().equalsIgnoreCase(symbol)) return true;
    }
    return false;
  }
}
