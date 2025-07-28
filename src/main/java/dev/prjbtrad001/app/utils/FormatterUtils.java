package dev.prjbtrad001.app.utils;

import lombok.Getter;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatterUtils {

  @Getter public static final DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
  @Getter public static final DecimalFormat FORMATTER1 = new DecimalFormat("#,###.00000", symbols);
  @Getter public static final DecimalFormat FORMATTER2 = new DecimalFormat("#,###.00", symbols);

  public static void setFormatter() {
    symbols.setDecimalSeparator(',');
    symbols.setGroupingSeparator('.');
    FORMATTER1.setParseBigDecimal(true);
    FORMATTER1.setRoundingMode(RoundingMode.UNNECESSARY);
  }

  public static String getLastFiveCharacters(String str) {
    Matcher matcher = Pattern.compile("(.{5})$").matcher(str);
    String lastFive = "";

    if (matcher.find()) {
      lastFive = matcher.group(1);
    } else {
      System.out.println("No match found");
    }
    return "...".concat(lastFive);
  }


}
