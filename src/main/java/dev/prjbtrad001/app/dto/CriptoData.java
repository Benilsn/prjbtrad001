package dev.prjbtrad001.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@AllArgsConstructor
public class CriptoData {

  private BigDecimal currentPrice;
  private BigDecimal last24hourPrice;
  private String lastUpdated;

  public static CriptoData defaultData() {
    return new CriptoData(
      BigDecimal.ZERO,
      BigDecimal.ZERO,
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    );
  }

}
