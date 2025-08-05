package dev.prjbtrad001.app.dto;

public enum FilterType {
  PRICE_FILTER,
  LOT_SIZE,
  ICEBERG_PARTS,
  MARKET_LOT_SIZE,
  TRAILING_DELTA,
  PERCENT_PRICE_BY_SIDE,
  NOTIONAL,
  MAX_NUM_ORDERS,
  MAX_NUM_ALGO_ORDERS;

  public static FilterType fromString(String value) {
    for (FilterType type : values()) {
      if (type.name().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown filterType: " + value);
  }
}