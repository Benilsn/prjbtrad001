package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.dto.Cripto;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@ApplicationScoped
public class BinanceService {

  @Inject
  ObjectMapper objectMapper;
  private final Random rdn = new Random();
  private static final String BASE_URL = "https://api.binance.com/api/v3/ticker/price?symbol=";
  private static final HttpClient httpClient = HttpClient.newHttpClient();

  public Cripto getPrice(String symbol) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + symbol))
        .GET()
        .build();

    Cripto cripto = Cripto.defaultData();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        cripto = objectMapper.convertValue(response.body(), Cripto.class);
      } else {
        log.info("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }

    return cripto;
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
