package dev.prjbtrad001.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches candles and prices from Binance's public REST API.
 *
 * These endpoints are unauthenticated, so the bot needs no keys to run in
 * paper mode or to backtest.
 */
@JBossLog
@ApplicationScoped
public class BinanceDataClient implements MarketDataClient {

  private static final String BASE_URL = "https://api.binance.com/api/v3";
  private static final int MAX_PER_REQUEST = 1000;

  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public List<KlineDto> getCandles(String symbol, String interval, int limit) {
    int capped = Math.min(limit, MAX_PER_REQUEST);
    String url = BASE_URL + "/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + capped;
    return fetchKlines(url);
  }

  @Override
  public List<KlineDto> getCandlesRange(String symbol, String interval, int total) {
    List<KlineDto> all = new ArrayList<>();
    // Binance caps each request at 1000; walk backwards using endTime.
    Long endTime = null;
    int remaining = Math.max(total, 1);

    while (remaining > 0) {
      int batch = Math.min(remaining, MAX_PER_REQUEST);
      StringBuilder url = new StringBuilder(BASE_URL)
        .append("/klines?symbol=").append(symbol)
        .append("&interval=").append(interval)
        .append("&limit=").append(batch);
      if (endTime != null) url.append("&endTime=").append(endTime);

      List<KlineDto> page = fetchKlines(url.toString());
      if (page.isEmpty()) break;

      all.addAll(0, page);               // prepend: pages arrive newest-batch last
      remaining -= page.size();
      endTime = page.getFirst().openTime() - 1;   // next page ends just before this one

      if (page.size() < batch) break;    // no more history available
    }
    return all;
  }

  @Override
  public BigDecimal getPrice(String symbol) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/ticker/price?symbol=" + symbol))
        .timeout(Duration.ofSeconds(10))
        .GET().build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        JsonNode node = mapper.readTree(response.body());
        return new BigDecimal(node.get("price").asText());
      }
      log.warnf("Price fetch failed for %s: HTTP %d", symbol, response.statusCode());
    } catch (Exception e) {
      log.errorf("Price fetch error for %s: %s", symbol, e.getMessage());
    }
    return null;
  }

  private List<KlineDto> fetchKlines(String url) {
    List<KlineDto> candles = new ArrayList<>();
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .GET().build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.warnf("Klines fetch failed: HTTP %d - %s", response.statusCode(), response.body());
        return candles;
      }

      JsonNode array = mapper.readTree(response.body());
      for (JsonNode k : array) {
        candles.add(new KlineDto(
          k.get(0).asLong(),                       // openTime
          new BigDecimal(k.get(1).asText()),       // open
          new BigDecimal(k.get(2).asText()),       // high
          new BigDecimal(k.get(3).asText()),       // low
          new BigDecimal(k.get(4).asText()),       // close
          new BigDecimal(k.get(5).asText()),       // volume
          k.get(6).asLong()                        // closeTime
        ));
      }
    } catch (Exception e) {
      log.errorf("Klines fetch error: %s", e.getMessage());
    }
    return candles;
  }
}
