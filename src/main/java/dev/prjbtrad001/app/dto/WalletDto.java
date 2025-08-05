package dev.prjbtrad001.app.dto;

import java.math.BigDecimal;

public record WalletDto(
  String coin,
  BigDecimal free,
  BigDecimal locked
) {}
