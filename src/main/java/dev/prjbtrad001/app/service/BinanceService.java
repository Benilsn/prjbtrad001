package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.dto.CriptoData;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;

@ApplicationScoped
public class BinanceService {

  @Inject
  ObjectMapper objectMapper;
  Random rdn = new Random();
  private static final String BASE_URL = "https://api.binance.com/api/v3/ticker/price?symbol=";
  private static final HttpClient httpClient = HttpClient.newHttpClient();

  public CriptoData getPrice(String symbol) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + symbol))
        .GET()
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = objectMapper.readTree(response.body());
        return
          new CriptoData(
            BigDecimal.valueOf(json.get("price").asDouble()),
            BigDecimal.ZERO,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss")));
      } else {
        System.err.println("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }

    return CriptoData.defaultData();
  }

  public CriptoData bitcoinData() {
    return new CriptoData(
      BigDecimal.valueOf(rdn.nextInt(110000, 130000)).setScale(2, RoundingMode.CEILING),
      BigDecimal.valueOf(rdn.nextInt(110000, 130000)).setScale(2, RoundingMode.CEILING),
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    );
  }

  public CriptoData ethData() {
    return new CriptoData(
      BigDecimal.valueOf(rdn.nextInt(2700, 2900)).setScale(2, RoundingMode.CEILING),
      BigDecimal.valueOf(rdn.nextInt(2300, 2500)).setScale(2, RoundingMode.CEILING),
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    );
  }


  public List<String> getLogData() {

    List<String> listOfData = new ArrayList<>();
    List<String> coins = List.of("BTC", "ETH", "XRP", "LTC", "BCH");
    List<String> operation = List.of("Buy", "Sell");

    for (int i = 0; i < rdn.nextInt(10, 99); i++) {
      listOfData.add(
        String.format("[%02d:%02d] %s %s at $%d",
          rdn.nextInt(0, 24), // Hour
          rdn.nextInt(0, 60), // Minute
          operation.get(rdn.nextInt(operation.size())), // Buy/Sell
          coins.get(rdn.nextInt(coins.size())), // Coin type
          rdn.nextInt(1000, 100000) // Price
        ));
    }
    return listOfData;
  }

}
