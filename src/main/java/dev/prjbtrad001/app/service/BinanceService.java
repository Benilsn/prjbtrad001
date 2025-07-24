package dev.prjbtrad001.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.dto.Cripto;
import dev.prjbtrad001.app.dto.Kline;
import dev.prjbtrad001.app.utils.CriptoUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
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
import java.util.List;

import static dev.prjbtrad001.infra.config.GenericConfig.MAPPER;

@JBossLog
@ApplicationScoped
public class BinanceService {

  @Inject
  ObjectMapper objectMapper;

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

  public static List<Kline> getCandles(String symbol, String interval, int limit) {

    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit))
        .GET()
        .build();

    List<Kline> candles = new ArrayList<>();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        candles = CriptoUtils.parseKlines(MAPPER, response.body());
      } else {
        log.error("Error: HTTP " + response.statusCode());
      }
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    return candles;
  }


}
