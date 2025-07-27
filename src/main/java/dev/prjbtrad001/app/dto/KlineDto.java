package dev.prjbtrad001.app.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineDto {

  private long openTime;
  private String openPrice;
  private String highPrice;
  private String lowPrice;
  private String closePrice;
  private String volume;
  private long closeTime;
  private String quoteAssetVolume;
  private int numberOfTrades;
  private String takerBuyBaseAssetVolume;
  private String takerBuyQuoteAssetVolume;
  private String ignore;

}