package dev.prjbtrad001.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.CriptoCredentials;
import dev.prjbtrad001.app.dto.AccountDto;
import dev.prjbtrad001.app.dto.BalanceDto;
import dev.prjbtrad001.app.dto.CriptoDto;
import dev.prjbtrad001.app.dto.KlineDto;
import dev.prjbtrad001.app.utils.CriptoUtils;
import dev.prjbtrad001.infra.config.CredentialsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.prjbtrad001.app.utils.CriptoUtils.generateSignature;
import static dev.prjbtrad001.app.utils.LogUtils.log;
import static dev.prjbtrad001.infra.config.GenericConfig.MAPPER;

@ApplicationScoped
public class BinanceService {

  @Inject
  private final ObjectMapper objectMapper;

  @ConfigProperty(name = "bot.symbol.list")
  private String workingSymbols;

  private static final String BASE_URL = "https://api.binance.com/api/v3";

  private static final HttpClient httpClient = HttpClient.newHttpClient();

  public BinanceService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public CriptoDto getPrice(String symbol) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/ticker/price?symbol=" + symbol))
        .GET()
        .build();

    CriptoDto cripto = null;

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        cripto = objectMapper.readValue(response.body(), new TypeReference<>() {
        });
        cripto.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      } else {
        log("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      log(e.getMessage());
      cripto = CriptoDto.defaultData();
    }

    return cripto;
  }

  public List<CriptoDto> getPrices(String symbolsJson) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/ticker/price?symbols=" + URLEncoder.encode(symbolsJson, StandardCharsets.UTF_8)))
        .GET()
        .build();

    List<CriptoDto> criptos = new ArrayList<>();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        criptos = objectMapper.readerForListOf(CriptoDto.class).readValue(response.body());
        criptos.forEach(c -> c.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
      } else {
        log("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      log(e.getMessage());
    }

    return criptos;
  }

  public static List<KlineDto> getCandles(String symbol, String interval, int limit) {

    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit))
        .GET()
        .build();

    List<KlineDto> candles = new ArrayList<>();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        candles = CriptoUtils.parseKlines(MAPPER, response.body());
      } else {
        log("Error getting candles: HTTP " + response.statusCode());
      }
    } catch (Exception e) {
      log(e.getMessage());
    }

    return candles;
  }

  public static long getBinanceServerTime() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/time"))
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = MAPPER.readTree(response.body());
        return json.get("serverTime").asLong();
      } else {
        log("Error getting serverTime: HTTP " + response.statusCode());
      }

    } catch (Exception e) {
      log("Error getting serverTime: " + e.getMessage());
    }

    return System.currentTimeMillis();
  }

  public Optional<AccountDto> getCriptosBalance() {
    String queryString = "timestamp=" + getBinanceServerTime();
    CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();

    try {
      String signature = generateSignature(queryString, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/account?" + queryString + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        AccountDto account = MAPPER.readValue(response.body(), AccountDto.class);
        account.filterBalances(workingSymbols.split(","));
        return Optional.of(account);
      } else {
        log("Error getting balance: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Error getting balance: " + e.getMessage());
    }

    return Optional.empty();
  }

  public static String placeBuyOrder(String symbol, BigDecimal quantity) {
    long timestamp = getBinanceServerTime();
    String queryString = String.format(
      "symbol=%s&side=BUY&type=MARKET&quoteOrderQty=%s&timestamp=%d", symbol, quantity, timestamp
    );

    CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();

    try {
      String signature = generateSignature(queryString, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/order?" + queryString + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        return response.body();
      } else {
        log("Purchase error: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Purchase error: " + e.getMessage());
    }

    return null;
  }

  public static String placeSellOrder(String symbol, String quantity) {
    long timestamp = getBinanceServerTime();
    String queryString = String.format(
      "symbol=%s&side=SELL&type=MARKET&quantity=%s&timestamp=%d", symbol, quantity, timestamp
    );

    CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();

    try {
      String signature = generateSignature(queryString, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/order?" + queryString + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        return response.body();
      } else {
        log("Selling error: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Selling error: " + e.getMessage());
    }

    return null;
  }

  public static BigDecimal getMinNotional(String symbol) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/exchangeInfo?symbol=" + symbol))
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = MAPPER.readTree(response.body());
        JsonNode filters = json
          .get("symbols").get(0)
          .get("filters");

        for (JsonNode filter : filters) {
          if ("NOTIONAL".equals(filter.get("filterType").asText())) {
            return new BigDecimal(filter.get("minNotional").asText()).setScale(8, RoundingMode.HALF_UP);
          }
        }

        log("MIN_NOTIONAL filter not found for symbol: " + symbol);
      } else {
        log("Exchange info error: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Error getting minNotional: " + e.getMessage());
    }

    return null;
  }


  public static Optional<BalanceDto> getBalance() {
    try {
      CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();
      long timestamp = getBinanceServerTime();
      String query = "recvWindow=5000&timestamp=" + timestamp;
      String signature = generateSignature(query, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.binance.com/sapi/v1/capital/config/getall?" + query + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        try {
          JsonNode array = MAPPER.readTree(response.body());
          for (JsonNode node : array) {

            if ("BRL".equalsIgnoreCase(node.get("coin").asText())) {
              BigDecimal free = new BigDecimal(node.get("free").asText());
              BigDecimal locked = new BigDecimal(node.get("locked").asText());
              return Optional.of(new BalanceDto("BRL", free, locked));
            }
          }
        } catch (Exception e) {
          log("Error converting BRL Balance: " + e.getMessage());
        }
      } else {
        log("Error Obtaining General Balance: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Error to consult general balance: " + e.getMessage());
    }
    return Optional.empty();
  }


}
