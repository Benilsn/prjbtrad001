package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

/**
 * Swing-trade tuned constants.
 *
 * Philosophy:
 *  - Minimum fee round-trip on Binance: ~0.20% (0.1% maker + 0.1% taker)
 *  - Target minimum R:R = 2:1, so minimum move target is ~1%+ to be worth it
 *  - Stop losses wider than scalping (1.5–3x ATR) to avoid noise
 */
public class TradingConstants {

  // ── RSI zones (swing context) ─────────────────────────────────
  /** Buy window: pulled back but not in free-fall */
  public static final BigDecimal RSI_SWING_BUY_MIN  = BigDecimal.valueOf(30);
  public static final BigDecimal RSI_SWING_BUY_MAX  = BigDecimal.valueOf(55);
  /** Sell / overbought zone */
  public static final BigDecimal RSI_OVERBOUGHT     = BigDecimal.valueOf(68);
  public static final BigDecimal RSI_OVERSOLD        = BigDecimal.valueOf(32);

  // ── Profit / fee gates ────────────────────────────────────────
  /** Minimum estimated profit before entry — covers 0.20% round-trip + slippage margin */
  public static final BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("0.60");

  /** Minimum price change (%) to consider a MACD histogram flip meaningful */
  public static final BigDecimal MACD_HISTOGRAM_MIN  = new BigDecimal("0.0001");

  // ── ATR stop multipliers ──────────────────────────────────────
  /** Stop loss placed N × ATR below entry */
  public static final double ATR_STOP_MULTIPLIER        = 1.5;
  /** Trailing stop activates after this profit % */
  public static final BigDecimal TRAILING_ACTIVATION    = new BigDecimal("1.0");
  /** Trail keeps this fraction of the peak profit */
  public static final BigDecimal TRAILING_KEEP_FRACTION = new BigDecimal("0.65");

  // ── Volume filter ─────────────────────────────────────────────
  /** Entry only when volume ≥ this multiple of average */
  public static final BigDecimal VOLUME_ENTRY_MULTIPLIER = new BigDecimal("0.9");

  // ── Minimum candle history required ──────────────────────────
  public static final int MIN_CANDLES_FOR_EMA200 = 210;
}
