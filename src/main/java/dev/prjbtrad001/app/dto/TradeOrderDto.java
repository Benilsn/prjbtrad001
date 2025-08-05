package dev.prjbtrad001.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeOrderDto(

  @JsonProperty("orderId")
  long id, // ID único da ordem atribuído pela Binance

  @JsonProperty("symbol")
  String symbol, // Par de negociação (ex: BTCBRL)

  @JsonProperty("executedQty")
  BigDecimal quantity, // Quantidade total de cripto comprada (ex: BTC)

  @JsonProperty("cummulativeQuoteQty")
  BigDecimal totalSpentBRL, // Valor total gasto na ordem em BRL

  @JsonProperty("status")
  String status, // Status da ordem (ex: FILLED, CANCELED, etc.)

  @JsonProperty("transactTime")
  long timestamp, // Timestamp da execução da ordem (em milissegundos)

  @JsonProperty("fills")
  List<Fill> trades // Lista de execuções da ordem (podem ser várias para MARKET)
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Fill(

    @JsonProperty("price")
    BigDecimal price, // Preço por unidade de cripto nesse trecho da execução

    @JsonProperty("qty")
    BigDecimal quantity, // Quantidade de cripto comprada nessa parte da ordem

    @JsonProperty("commission")
    BigDecimal fee, // Taxa cobrada pela Binance

    @JsonProperty("commissionAsset")
    String feeAsset // Ativo no qual a taxa foi cobrada (ex: BTC, BNB)
  ) {}
}
