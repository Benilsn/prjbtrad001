package dev.prjbtrad001.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(
  int makerCommission,
  int takerCommission,
  int buyerCommission,
  int sellerCommission,
  CommissionRates commissionRates,
  boolean canTrade,
  boolean canWithdraw,
  boolean canDeposit,
  long updateTime,
  String accountType,
  List<Balance> balances
) {

  public void filterBalances(String... assets) {
    Set<String> allowedAssets = Arrays.stream(assets)
      .map(asset -> asset.replaceFirst("BRL$", ""))
      .collect(Collectors.toSet());

    balances.removeIf(balance -> !allowedAssets.contains(balance.asset()));
  }

  public record CommissionRates(
    String maker,
    String taker,
    String buyer,
    String seller
  ) {
  }

  public record Balance(
    String asset,
    BigDecimal free,
    BigDecimal locked
  ) {
  }
}
