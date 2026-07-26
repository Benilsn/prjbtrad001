package dev.prjbtrad001.web;

import io.quarkus.qute.TemplateExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatting helpers usable directly in templates, e.g. {@code {value.brl}} or
 * {@code {pctValue.pct}}.
 */
@TemplateExtension
public class TemplateExtensions {

  private static final DecimalFormatSymbols BR = new DecimalFormatSymbols(Locale.of("pt", "BR"));
  private static final ThreadLocal<DecimalFormat> MONEY =
    ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00", BR));

  /** BigDecimal → "1.234,56" (BRL grouping), null-safe. */
  static String brl(BigDecimal value) {
    if (value == null) return "0,00";
    return MONEY.get().format(value);
  }

  /** double → "12.34" with two decimals. */
  static String pct(double value) {
    return String.format(Locale.US, "%.2f", value);
  }

  /** BigDecimal → plain 2-decimal string (for stop/order fields). */
  static String num2(BigDecimal value) {
    if (value == null) return "0";
    return value.stripTrailingZeros().scale() <= 0
      ? value.setScale(0, RoundingMode.HALF_UP).toPlainString()
      : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}
