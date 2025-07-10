package dev.prjbtrad001.service;

import dev.prjbtrad001.dto.CriptoData;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@ApplicationScoped
public class BinanceService {

  Random rdn = new Random();

  public CriptoData bitcoinData() {
    return new CriptoData(
      BigDecimal.valueOf(rdn.nextInt(110000, 130000)).setScale(2, RoundingMode.CEILING),
      BigDecimal.valueOf(rdn.nextInt(110000, 130000)).setScale(2, RoundingMode.CEILING),
      LocalDateTime.now()
    );
  }

  public CriptoData ethData() {
    return new CriptoData(
      BigDecimal.valueOf(rdn.nextInt(2700, 2900)).setScale(2, RoundingMode.CEILING),
      BigDecimal.valueOf(rdn.nextInt(2300, 2500)).setScale(2, RoundingMode.CEILING),
      LocalDateTime.now()
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
