package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.core.Trading;
import dev.prjbtrad001.app.dto.*;
import dev.prjbtrad001.app.utils.CriptoUtils;
import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.domain.core.TradingExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static dev.prjbtrad001.app.utils.LogUtils.log;
import static dev.prjbtrad001.infra.config.GenericConfig.MAPPER;

@JBossLog
@ApplicationScoped
@Trading(Trading.Type.MOCK)
public class MockService implements TradingExecutor {

  private static final String BASE_URL = "https://api.binance.com/api/v3";
  private final MockWallet wallet = new MockWallet();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public CriptoDto getPrice(String symbol) {
    try {
      List<KlineDto> lastCandle = getCandles(symbol, "1m", 1);
      if (!lastCandle.isEmpty()) {
        BigDecimal price = new BigDecimal(lastCandle.getFirst().getClosePrice());
        return new CriptoDto(symbol, price.toPlainString(), null, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      }
    } catch (Exception e) {
      log.errorf("Error getting mock price for %s: %s", symbol, e.getMessage());
    }
    return null;
  }

  @Override
  public Optional<BalanceDto> getBalance() {
    BigDecimal balance = wallet.getBalance("BRL");
    return Optional.of(new BalanceDto("BRL", balance, BigDecimal.ZERO));
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
    String asset = symbol.replaceFirst("BRL$", "");
    return new AccountDto.Balance(asset, wallet.getBalance(asset), BigDecimal.ZERO);
  }

  @Override
  public List<KlineDto> getCandles(String symbol, String interval, int limit) {
    List<KlineDto> candles;

    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit))
        .GET()
        .build();

    candles = new ArrayList<>();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        candles = CriptoUtils.parseKlines(MAPPER, response.body());
        log.debugf("Getting candles for %s, total: %d candles", symbol, candles.size());
      } else {
        log("Error getting candles: HTTP " + response.statusCode());
      }
    } catch (Exception e) {
      log(e.getMessage());
    }

    return candles;
  }


  @Override
  public Optional<TradeOrderDto> placeBuyOrder(String symbol, BigDecimal purchaseAmountInReais) {
    try {
      BigDecimal price = new BigDecimal(getPrice(symbol).getPrice()).setScale(8, RoundingMode.HALF_UP);
      BigDecimal brlBalance = wallet.getBalance("BRL");
      String asset = symbol.replaceFirst("BRL$", "");

      BigDecimal minScalpValue = BigDecimal.valueOf(10);
      if (purchaseAmountInReais.compareTo(minScalpValue) < 0) {
        log("[" + symbol + "] ⚠️ Valor muito pequeno para scalping eficiente");
      }

      if (brlBalance.compareTo(purchaseAmountInReais) >= 0) {
        wallet.updateBalance("BRL", purchaseAmountInReais.negate());

        BigDecimal grossQuantity = purchaseAmountInReais.divide(price, 8, RoundingMode.HALF_UP);

        BigDecimal feeRate = BigDecimal.valueOf(0.001);
        if (symbol.equalsIgnoreCase(BotType.BNBBRL.toString())) {
          feeRate = BigDecimal.valueOf(0.00075);
        }

        BigDecimal tradingFee = grossQuantity.multiply(feeRate);
        log("[" + symbol + "] - " + "💰 Trading fee: " + tradingFee.setScale(2, RoundingMode.HALF_UP) + " " + asset, true);

        BigDecimal feeImpactPercent =
          tradingFee.multiply(price)
            .divide(purchaseAmountInReais, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        log("[" + symbol + "] - 📊 Impacto da taxa: " + feeImpactPercent + "% do valor total");

        BigDecimal netQuantity = grossQuantity.subtract(tradingFee);

        wallet.updateBalance(asset, netQuantity);

        return Optional.of(createMockOrder(symbol, netQuantity, price, purchaseAmountInReais));
      }
      log.warn("Insufficient mock balance for buy order");
    } catch (Exception e) {
      log.errorf("Error placing mock buy order: %s", e.getMessage());
    }
    return Optional.empty();
  }

  @Override
  public Optional<TradeOrderDto> placeSellOrder(String symbol) {
    try {
      String asset = symbol.replaceFirst("BRL$", "");
      BigDecimal quantity = wallet.getBalance(asset);

      if (quantity.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal price = new BigDecimal(getPrice(symbol).getPrice()).setScale(8, RoundingMode.HALF_UP);
        BigDecimal totalInReais = price.multiply(quantity);

        BigDecimal tradingFee = totalInReais.multiply(BigDecimal.valueOf(0.001));
        log("[" + symbol + "] - " + "💰 Trading fee: R$" + tradingFee.setScale(2, RoundingMode.HALF_UP), true);
        BigDecimal totalAfterFee = totalInReais.subtract(tradingFee);

        wallet.updateBalance(asset, quantity.negate());
        wallet.updateBalance("BRL", totalAfterFee);

        log.infof("Mock sell: %s quantity=%s price=%s total=%s",
          symbol, quantity, price, totalInReais);

        return Optional.of(createMockOrder(symbol, quantity, price, totalInReais));
      }
      log.warn("Insufficient asset balance for sell order");
    } catch (Exception e) {
      log.errorf("Error placing mock sell order: %s", e.getMessage());
    }
    return Optional.empty();
  }

  private TradeOrderDto createMockOrder(String symbol, BigDecimal quantity, BigDecimal price, BigDecimal total) {
    List<TradeOrderDto.Fill> fills = List.of(new TradeOrderDto.Fill(
      price, quantity, BigDecimal.ZERO, "BRL"
    ));

    return new TradeOrderDto(
      System.currentTimeMillis(),
      symbol,
      quantity,
      total,
      "FILLED",
      System.currentTimeMillis(),
      fills
    );
  }


  @Getter
  private static class MockWallet {
    private static final BigDecimal INITIAL_BALANCE = BigDecimal.valueOf(1500.00);
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();

    public MockWallet() {
      balances.put("BRL", INITIAL_BALANCE);
    }

    public synchronized BigDecimal getBalance(String symbol) {
      return balances.getOrDefault(symbol, BigDecimal.ZERO);
    }

    public synchronized void updateBalance(String symbol, BigDecimal amount) {
      balances.merge(symbol, amount.setScale(8, RoundingMode.HALF_UP), BigDecimal::add);
    }
  }
}
