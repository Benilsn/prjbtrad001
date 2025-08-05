//package dev.prjbtrad001;
//
//import dev.prjbtrad001.app.bot.BotParameters;
//import dev.prjbtrad001.app.bot.SimpleTradeBot;
//import dev.prjbtrad001.app.bot.Status;
//import dev.prjbtrad001.app.bot.Wallet;
//import dev.prjbtrad001.app.dto.Kline;
//import dev.prjbtrad001.app.service.BinanceService;
//import dev.prjbtrad001.app.service.TradingService;
//import dev.prjbtrad001.domain.core.BotType;
//import io.quarkus.test.Mock;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.junit.jupiter.MockitoExtension;
//import java.io.InputStream;
//import java.math.BigDecimal;
//import java.util.List;
//
//@ExtendWith(MockitoExtensi.class)
//public class TradingSimulationTest {
//
//  @Mock
//  BinanceService binanceService;
//
//  @Test
//  public void testBuyScenario() throws Exception {
//    SimpleTradeBot bot = new SimpleTradeBot();
//    BotParameters parameters = getBotParameters();
//
//    bot.setParameters(parameters);
//    bot.setStatus(new Status());
//
//    InputStream mockFile = getClass().getClassLoader().getResourceAsStream("binance_mock_data.tsv");
//    List<Kline> mockKlines = MockBinanceDataLoader.loadFromTsv(mockFile);
//
//    try (MockedStatic<BinanceService> mockedBinance = Mockito.mockStatic(BinanceService.class)) {
//      mockedBinance.when(() -> BinanceService.getCandles(anyString(), anyString(), anyInt()))
//        .thenReturn(mockKlines);
//
//      try (MockedStatic<Wallet> mockedWallet = Mockito.mockStatic(Wallet.class)) {
//        mockedWallet.when(Wallet::get).thenReturn(new BigDecimal("1000"));
//
//        // Mocka também o bot.buy/sell se necessário
//        SimpleTradeBot spyBot = Mockito.spy(bot);
//        TradingService.analyzeMarket(spyBot);
//
//        verify(spyBot, atMostOnce()).buy(any());
//        // verify(spyBot, atMostOnce()).sell(any());
//      }
//    }
//  }
//
//  private static BotParameters getBotParameters() {
//    BotParameters parameters = new BotParameters();
//    parameters.setBotType(BotType.BTCUSDT);
//    parameters.setInterval("1m");
//    parameters.setWindowResistanceSupport(30);
//    parameters.setRsiPurchase(new BigDecimal("30"));
//    parameters.setRsiSale(new BigDecimal("70"));
//    parameters.setSmaShort(9);
//    parameters.setSmaLong(21);
//    parameters.setPurchaseAmount(new BigDecimal("200"));
//    parameters.setPurchaseStrategy(PurchaseStrategy.FIXED);
//    parameters.setVolumeMultiplier(new BigDecimal("1.0"));
//    parameters.setStopLossPercent(new BigDecimal("-2.0"));
//    parameters.setTakeProfitPercent(new BigDecimal("2.0"));
//    return parameters;
//  }
//}