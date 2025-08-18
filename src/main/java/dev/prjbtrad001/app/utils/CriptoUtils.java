package dev.prjbtrad001.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.bot.PurchaseStrategy;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.dto.KlineDto;
import dev.prjbtrad001.domain.core.BotType;
import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Utility class providing helper methods for cryptocurrency data processing.
 *
 * <p>This class contains static methods to parse JSON responses from Binance API,
 * manipulate numeric values such as rounding crypto balances according to step sizes,
 * and other common utility functions needed across the crypto trading application.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * List<KlineDto> klines = CriptoUtils.parseKlines(mapper, jsonResponse);
 * BigDecimal roundedBalance = CriptoUtils.roundDownToStepSize(balance, stepSize);
 * }</pre>
 *
 * @see KlineDto
 */
@UtilityClass
public class CriptoUtils {


  /**
   * Parses a JSON response from Binance's /klines endpoint into a list of {@link KlineDto} objects.
   *
   * <p>The JSON response is expected to be an array where each element represents a candlestick (kline)
   * with fields in a fixed order, as defined by Binance API:</p>
   * <ol>
   *   <li>Open time (long)</li>
   *   <li>Open price (String)</li>
   *   <li>High price (String)</li>
   *   <li>Low price (String)</li>
   *   <li>Close price (String)</li>
   *   <li>Volume (String)</li>
   *   <li>Close time (long)</li>
   *   <li>Quote asset volume (String)</li>
   *   <li>Number of trades (int)</li>
   *   <li>Taker buy base asset volume (String)</li>
   *   <li>Taker buy quote asset volume (String)</li>
   *   <li>Ignore (String)</li>
   * </ol>
   *
   * @param mapper       the Jackson {@link ObjectMapper} instance used to parse JSON
   * @param jsonResponse the raw JSON response string from Binance's /klines endpoint
   * @return a list of {@link KlineDto} representing parsed candlestick data
   * @throws Exception if parsing the JSON fails or the input format is unexpected
   */
  public static List<KlineDto> parseKlines(ObjectMapper mapper, String jsonResponse) throws Exception {
    JsonNode rootNode = mapper.readTree(jsonResponse);

    List<KlineDto> klines = new ArrayList<>();

    if (rootNode.isArray()) {
      for (JsonNode node : rootNode) {
        long openTime = node.get(0).asLong();
        String openPrice = node.get(1).asText();
        String highPrice = node.get(2).asText();
        String lowPrice = node.get(3).asText();
        String closePrice = node.get(4).asText();
        String volume = node.get(5).asText();
        long closeTime = node.get(6).asLong();
        String quoteAssetVolume = node.get(7).asText();
        int numberOfTrades = node.get(8).asInt();
        String takerBuyBaseAssetVolume = node.get(9).asText();
        String takerBuyQuoteAssetVolume = node.get(10).asText();
        String ignore = node.get(11).asText();

        KlineDto kline = new KlineDto(openTime, openPrice, highPrice, lowPrice, closePrice, volume,
          closeTime, quoteAssetVolume, numberOfTrades,
          takerBuyBaseAssetVolume, takerBuyQuoteAssetVolume, ignore);
        klines.add(kline);
      }
    }

    return klines;
  }

  /**
   * Generates an HMAC SHA-256 signature for the given data using the provided secret key.
   *
   * <p>This method is used to sign requests to Binance API, ensuring
   * authentication and integrity of the transmitted data.</p>
   *
   * @param data the message or query string to be signed
   * @param key  the secret key used for signing (usually the Binance API secret)
   * @return the generated signature as a hexadecimal string
   * @throws Exception if the HMAC SHA-256 algorithm is not available or initialization fails
   */
  public static String generateSignature(String data, String key) throws Exception {
    Mac sha256HMAC = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    sha256HMAC.init(secretKeySpec);
    byte[] hash = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }

  /**
   * Rounds down the given crypto balance to the nearest multiple of the specified step size.
   *
   * <p>This is useful for ensuring that trade quantities comply with Binance's
   * {@code LOT_SIZE.stepSize} constraint, which defines the minimum quantity increment allowed.</p>
   *
   * <p>Example:</p>
   * <pre>{@code
   * BigDecimal balance = new BigDecimal("0.00004995");
   * BigDecimal stepSize = new BigDecimal("0.00001000");
   * BigDecimal rounded = roundDownToStepSize(balance, stepSize);
   * // Result: 0.00004000
   * }</pre>
   *
   * @param cryptoBalance the available balance to round
   * @param stepSize      the step size defined by Binance for the asset
   * @return the balance rounded down to a valid quantity according to step size
   * @throws IllegalArgumentException if step size is zero or negative
   */
  public static BigDecimal roundDownToStepSize(BigDecimal cryptoBalance, BigDecimal stepSize) {
    if (stepSize.compareTo(BigDecimal.ZERO) <= 0)
      throw new IllegalArgumentException("Step size must be greater than zero.");

    return cryptoBalance
      .divide(stepSize, 0, RoundingMode.DOWN)
      .multiply(stepSize)
      .setScale(stepSize.scale(), RoundingMode.DOWN);
  }

  public static List<SimpleTradeBot> readFromCsv(Path filePath) throws IOException {
    List<SimpleTradeBot> bots = new ArrayList<>();

    List<String> lines = Files.readAllLines(filePath);

    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.isEmpty()) continue;

      String[] parts = line.split(";");
      if (parts.length < 12) {
        throw new IllegalArgumentException("Invalid CSV format at line " + (i + 1));
      }

      BotParameters parameters = new BotParameters();
      parameters.setBotType(BotType.valueOf(parts[0]));
      parameters.setInterval(parts[1]);
      parameters.setStopLossPercent(new BigDecimal(parts[2]));
      parameters.setTakeProfitPercent(new BigDecimal(parts[3]));
      parameters.setRsiPurchase(new BigDecimal(parts[4]));
      parameters.setRsiSale(new BigDecimal(parts[5]));
      parameters.setVolumeMultiplier(new BigDecimal(parts[6]));
      parameters.setSmaShort(Integer.parseInt(parts[7]));
      parameters.setSmaLong(Integer.parseInt(parts[8]));
      parameters.setCandlesAnalyzed(Integer.parseInt(parts[9]));
      parameters.setPurchaseStrategy(PurchaseStrategy.valueOf(parts[10])); // assumes enum
      parameters.setPurchaseAmount(new BigDecimal(parts[11]));

      bots.add(new SimpleTradeBot(parameters));
    }

    return bots;
  }

}
