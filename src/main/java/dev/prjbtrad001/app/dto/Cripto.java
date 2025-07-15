package dev.prjbtrad001.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cripto {

  private String symbol;
  private double price;
  private BigDecimal currentPrice;
  private BigDecimal last24hourPrice;
  private String lastUpdated;

  public static Cripto defaultData() {
    return new Cripto(
      "DEFAULT",
      0.0,
      BigDecimal.ZERO,
      BigDecimal.ZERO,
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    );
  }

}
