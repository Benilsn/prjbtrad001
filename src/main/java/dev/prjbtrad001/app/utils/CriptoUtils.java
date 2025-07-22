package dev.prjbtrad001.app.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.dto.Kline;
import lombok.experimental.UtilityClass;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class CriptoUtils {

  public static List<Kline> parseKlines(ObjectMapper mapper, String jsonResponse) throws Exception {
    JsonNode rootNode = mapper.readTree(jsonResponse);

    List<Kline> klines = new ArrayList<>();

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

        Kline kline = new Kline(openTime, openPrice, highPrice, lowPrice, closePrice, volume,
          closeTime, quoteAssetVolume, numberOfTrades,
          takerBuyBaseAssetVolume, takerBuyQuoteAssetVolume, ignore);
        klines.add(kline);
      }
    }

    return klines;
  }


}
