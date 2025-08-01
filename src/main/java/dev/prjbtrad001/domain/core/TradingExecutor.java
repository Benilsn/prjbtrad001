package dev.prjbtrad001.domain.core;

import dev.prjbtrad001.app.dto.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TradingExecutor {

  CriptoDto getPrice(String symbol);
  Optional<BalanceDto> getBalance();
  Optional<AccountDto> getAllCriptoBalances();
  List<CriptoDto> getPrices(String symbolsJson);
  AccountDto.Balance getCriptoBalance(String symbol);
  Optional<TradeOrderDto> placeSellOrder(String symbol);
  List<KlineDto> getCandles(String symbol, String interval, int limit);
  Optional<TradeOrderDto> placeBuyOrder(String symbol, BigDecimal quantity);

}