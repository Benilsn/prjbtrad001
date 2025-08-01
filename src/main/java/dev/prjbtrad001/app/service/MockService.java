package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.core.Trading;
import dev.prjbtrad001.app.dto.*;
import dev.prjbtrad001.domain.core.TradingExecutor;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Trading(Trading.Type.MOCK)
public class MockService implements TradingExecutor {

  @Override
  public CriptoDto getPrice(String symbol) {
    return null;
  }

  @Override
  public Optional<BalanceDto> getBalance() {
    return Optional.empty();
  }

  @Override
  public Optional<AccountDto> getAllCriptoBalances() {
    return Optional.empty();
  }

  @Override
  public List<CriptoDto> getPrices(String symbolsJson) {
    return List.of();
  }

  @Override
  public AccountDto.Balance getCriptoBalance(String symbol) {
    return null;
  }

  @Override
  public Optional<TradeOrderDto> placeSellOrder(String symbol) {
    return Optional.empty();
  }

  @Override
  public List<KlineDto> getCandles(String symbol, String interval, int limit) {
    return List.of();
  }

  @Override
  public Optional<TradeOrderDto> placeBuyOrder(String symbol, BigDecimal quantity) {
    return Optional.empty();
  }
}
