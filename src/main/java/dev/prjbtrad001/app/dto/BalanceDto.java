package dev.prjbtrad001.app.dto;

import java.math.BigDecimal;

public record BalanceDto(
  String coin,
  BigDecimal balance,
  BigDecimal locked
) {}
