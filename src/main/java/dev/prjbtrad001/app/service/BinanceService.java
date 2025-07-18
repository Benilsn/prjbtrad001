package dev.prjbtrad001.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.dto.Cripto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@JBossLog
@ApplicationScoped
public class BinanceService {

  @Inject
  ObjectMapper objectMapper;
  private static final String BASE_URL = "https://api.binance.com/api/v3/ticker/price?symbol=";
  private static final HttpClient httpClient = HttpClient.newHttpClient();

  public Cripto getPrice(String symbol) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + symbol))
        .GET()
        .build();

    Cripto cripto = null;

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        cripto = objectMapper.readValue(response.body(), new TypeReference<>() {});
        cripto.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      } else {
        log.info("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      cripto = Cripto.defaultData();
    }

    return cripto;
  }



}
