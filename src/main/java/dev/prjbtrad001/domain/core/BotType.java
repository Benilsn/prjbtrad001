package dev.prjbtrad001.domain.core;

public enum BotType {
  BTCBRL,
  ETHBRL,
  SOLBRL,
  BNBBRL,
  XRPBRL,
  DOGEBRL,
  MATICBRL,
  LTCBRL,
  LINKBRL,
  ENABRL,
  AVAXBRL,
  ADABRL;

  public boolean isValid(String symbol) {
    for (BotType type : BotType.values()) {
      if (type.name().equalsIgnoreCase(symbol)) {
        return true;
      }
    }
    return false;
  }

}