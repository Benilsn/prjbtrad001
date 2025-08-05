package dev.prjbtrad001;

import dev.prjbtrad001.app.dto.KlineDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MockBinanceDataLoader {

  public static List<KlineDto> loadFromTsv(InputStream inputStream) throws IOException {
    List<KlineDto> klines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      boolean headerSkipped = false;

      while ((line = reader.readLine()) != null) {
        if (!headerSkipped) {
          headerSkipped = true;
          continue;
        }

        String[] parts = line.split("\t");
        if (parts.length < 3) continue;

        String openTime = parts[0];
        String closePrice = parts[1];
        String volume = parts[2];

        KlineDto kline = new KlineDto();
        kline.setOpenTime(Long.parseLong(openTime));
        kline.setClosePrice(closePrice);
        kline.setVolume(volume);

        klines.add(kline);
      }
    }
    return klines;
  }
}