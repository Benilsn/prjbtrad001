package dev.prjbtrad001.app.core;

import dev.prjbtrad001.app.service.BinanceService;
import dev.prjbtrad001.app.service.MockService;
import dev.prjbtrad001.domain.core.TradingExecutor;
import dev.prjbtrad001.infra.config.TradingConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class TradingServiceProducer {

  @Inject
  TradingConfig tradingConfig;

  @Produces
  @ApplicationScoped
  public TradingExecutor produceTradingService(
    @Trading(Trading.Type.MOCK) MockService mockService,
    @Trading(Trading.Type.REAL) BinanceService binanceService) {
    return tradingConfig.isMockTrading() ? mockService : binanceService;
  }
}