package dev.prjbtrad001.strategy;

/**
 * The decision produced for a single closed candle.
 */
public enum Signal {
  /** Open a long position. */
  ENTER,
  /** Close the open position. */
  EXIT,
  /** Do nothing. */
  HOLD
}
