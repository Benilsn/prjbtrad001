package dev.prjbtrad001.app.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Getter
@Setter
@NoArgsConstructor
public class Cripto {

  private String symbol;

  @JsonDeserialize(using = BigDecimal4ScaleDeserializer.class)
  private String price;

  private String last24hourPrice;

  private String lastUpdated;

  @Getter(AccessLevel.NONE)
  private static final DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
  @Getter(AccessLevel.NONE)
  private static final DecimalFormat formatter = new DecimalFormat("#,###.00000", symbols);

  public Cripto(String symbol, String price, String last24hourPrice, String lastUpdated) {
    this.symbol = symbol;
    this.price = price;
    this.last24hourPrice = last24hourPrice;
    this.lastUpdated = lastUpdated;
  }

  public static Cripto defaultData() {
    return new Cripto(
      "DEFAULT",
      BigDecimal.ZERO.toPlainString(),
      "0.0",
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    );
  }

  private static class BigDecimal4ScaleDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser p, DeserializationContext deserializationContext) throws IOException {
      symbols.setDecimalSeparator(',');
      symbols.setGroupingSeparator('.');
      formatter.setParseBigDecimal(true);
      return formatter.format(new BigDecimal(p.getText()).setScale(5, RoundingMode.UNNECESSARY));
    }
  }
}
