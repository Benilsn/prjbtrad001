package dev.prjbtrad001.app.utils;

import lombok.Getter;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class FormatterUtils {

  @Getter public static final DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("pt", "BR"));
  @Getter public static final DecimalFormat formatter = new DecimalFormat("#,###.00000", symbols);

  public static void setFormatter() {
    symbols.setDecimalSeparator(',');
    symbols.setGroupingSeparator('.');
    formatter.setParseBigDecimal(true);
    formatter.setRoundingMode(RoundingMode.UNNECESSARY);
  }


}
