package dev.prjbtrad001.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.dto.KlineDto;
import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@UtilityClass
public class CriptoUtils {


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

  public static String generateSignature(String data, String key) throws Exception {
    Mac sha256HMAC = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    sha256HMAC.init(secretKeySpec);
    byte[] hash = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }


}
