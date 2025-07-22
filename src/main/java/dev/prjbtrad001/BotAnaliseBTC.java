package dev.prjbtrad001;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import org.json.*;

public class BotAnaliseBTC {

  public static void main(String[] args) throws Exception {
    List<Double> closes = new ArrayList<>();
    List<Double> volumes = new ArrayList<>();

    // 1. Obter candles de 1m (últimos 50)
    JSONArray candles = getCandles("BTCUSDT", "1m", 50);

    for (int i = 0; i < candles.length(); i++) {
      JSONArray candle = candles.getJSONArray(i);
      double close = Double.parseDouble(candle.getString(4));
      double volume = Double.parseDouble(candle.getString(5));
      closes.add(close);
      volumes.add(volume);
    }

    // 2. Indicadores
    double rsi = calcularRSI(closes);
    double sma9 = calcularMedia(closes.subList(closes.size() - 9, closes.size()));
    double sma21 = calcularMedia(closes.subList(closes.size() - 21, closes.size()));
    double volumeAtual = volumes.get(volumes.size() - 1);
    double volumeMedio = calcularMedia(volumes);
    double suporte = Collections.min(closes.subList(closes.size() - 10, closes.size()));
    double resistencia = Collections.max(closes.subList(closes.size() - 10, closes.size()));
    double precoAtual = closes.get(closes.size() - 1);

    // 3. Decisão de compra
    System.out.println("RSI: " + rsi);
    System.out.println("SMA9: " + sma9 + ", SMA21: " + sma21);
    System.out.println("Volume atual: " + volumeAtual + ", Volume médio: " + volumeMedio);
    System.out.println("Suporte: " + suporte + ", Resistência: " + resistencia);
    System.out.println("Preço atual: " + precoAtual);

    if (rsi < 30 && precoAtual > suporte && sma9 > sma21 && volumeAtual > volumeMedio * 1.2) {
      System.out.println("🔵 Sinal de COMPRA detectado!");
    } else if (rsi > 70 || precoAtual >= resistencia) {
      System.out.println("🔴 Sinal de VENDA detectado!");
    } else {
      System.out.println("🟡 Nenhuma ação recomendada no momento.");
    }
  }

  // ========== Indicadores ==========
  public static double calcularRSI(List<Double> closes) {
    int periodo = 14;
    double ganho = 0, perda = 0;

    for (int i = 1; i <= periodo; i++) {
      double diff = closes.get(i) - closes.get(i - 1);
      if (diff > 0) ganho += diff;
      else perda += -diff;
    }

    double mediaGanho = ganho / periodo;
    double mediaPerda = perda / periodo;

    if (mediaPerda == 0) return 100;
    double rs = mediaGanho / mediaPerda;
    return 100 - (100 / (1 + rs));
  }

  public static double calcularMedia(List<Double> valores) {
    return valores.stream().mapToDouble(v -> v).average().orElse(0);
  }

  // ========== Binance API ==========
  public static JSONArray getCandles(String symbol, String interval, int limit) throws Exception {
    String url = "https://api.binance.com/api/v3/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit;
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setRequestMethod("GET");

    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
    StringBuilder resposta = new StringBuilder();
    String linha;
    while ((linha = in.readLine()) != null) resposta.append(linha);
    in.close();

    return new JSONArray(resposta.toString());
  }
}
