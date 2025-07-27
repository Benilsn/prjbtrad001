package dev.prjbtrad001.app.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import dev.prjbtrad001.app.utils.FormatterUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static dev.prjbtrad001.app.utils.FormatterUtils.FORMATTER1;


@Getter
@Setter
@NoArgsConstructor
public class CriptoDto {

  private String symbol;

  @JsonDeserialize(using = BigDecimal4ScaleDeserializer.class)
  private String price;

  private String last24hourPrice;

  private String lastUpdated;

  public CriptoDto(String symbol, String price, String last24hourPrice, String lastUpdated) {
    this.symbol = symbol;
    this.price = price;
    this.last24hourPrice = last24hourPrice;
    this.lastUpdated = lastUpdated;
  }

  public static CriptoDto defaultData() {
    return new CriptoDto(
      "DEFAULT",
      BigDecimal.ZERO.toPlainString(),
      "0.0",
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    );
  }

  private static class BigDecimal4ScaleDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser p, DeserializationContext deserializationContext) throws IOException {
      FormatterUtils.setFormatter();
      return FORMATTER1.format(new BigDecimal(p.getText()).setScale(5, RoundingMode.UNNECESSARY));
    }
  }
}
