package dev.prjbtrad001.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.dto.Cripto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JBossLog
@ApplicationScoped
public class BinanceService {

  @Inject
  ObjectMapper objectMapper;

  @ConfigProperty(name = "binance.api.base-url", defaultValue = "https://api.binance.com/api/v3")
  private static final String BASE_URL = "https://api.binance.com/api/v3";
  private static final HttpClient httpClient = HttpClient.newHttpClient();

  public Cripto getPrice(String symbol) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/ticker/price?symbol=" + symbol))
        .GET()
        .build();

    Cripto cripto = null;

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        cripto = objectMapper.readValue(response.body(), new TypeReference<>() {
        });
        cripto.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      } else {
        log.debug("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      log.error(e.getMessage());
      cripto = Cripto.defaultData();
    }

    return cripto;
  }

  public List<Cripto> getPrices(String symbolsJson) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/ticker/price?symbols=" + URLEncoder.encode(symbolsJson, StandardCharsets.UTF_8)))
        .GET()
        .build();

    List<Cripto> criptos = new ArrayList<>();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        criptos = objectMapper.readerForListOf(Cripto.class).readValue(response.body());
        criptos.forEach(c -> c.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
      } else {
        log.error("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      log.error(e.getMessage());
    }

    return criptos;
  }

  public List<String> getAllSymbols() throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/exchangeInfo"))
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode symbols = root.path("symbols");

      List<String> symbolList = new ArrayList<>();
      for (JsonNode s : symbols) {
        String symbol = s.path("symbol").asText();
        // Você pode filtrar só pares USDT, por exemplo:
        if (symbol.endsWith("USDT")) {
          symbolList.add(symbol);
        }
      }
      return symbolList;
    } else {
      log.error("Error fetching exchangeInfo from Binance: " + response.statusCode());
      return Collections.emptyList();
    }
  }
}
