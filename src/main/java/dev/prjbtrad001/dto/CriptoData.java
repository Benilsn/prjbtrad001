package dev.prjbtrad001.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class CriptoData {

  private BigDecimal currentPrice;
  private BigDecimal last24hourPrice;
  private LocalDateTime lastUpdated;

}
