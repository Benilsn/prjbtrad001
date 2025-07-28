package dev.prjbtrad001.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.CriptoCredentials;
import dev.prjbtrad001.app.dto.*;
import dev.prjbtrad001.app.utils.CriptoUtils;
import dev.prjbtrad001.infra.config.CredentialsConfig;
import dev.prjbtrad001.infra.exception.ErrorCode;
import dev.prjbtrad001.infra.exception.TradeException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.Optional;

import static dev.prjbtrad001.app.utils.CriptoUtils.generateSignature;
import static dev.prjbtrad001.app.utils.CriptoUtils.roundDownToStepSize;
import static dev.prjbtrad001.app.utils.LogUtils.log;
import static dev.prjbtrad001.infra.config.GenericConfig.MAPPER;


/**
 * Service class for interacting with Binance API.
 *
 * <p>This class provides utility methods to perform actions such as:
 * <ul>
 *   <li>Placing market buy orders</li>
 *   <li>Fetching account balances</li>
 *   <li>Retrieving Binance server time</li>
 *   <li>Fetching candlestick (kline) data</li>
 *   <li>Getting price information for one or multiple symbols</li>
 * </ul>
 * </p>
 *
 * <p>It handles request signing using API credentials, builds and sends HTTP requests,
 * and parses responses using Jackson's ObjectMapper.</p>
 *
 * <p>All interactions are logged, and most methods return Optional types or default
 * fallback values to ensure robustness in case of errors.</p>
 *
 * <p>Example usage:</p>
 *
 * <pre>{@code
 * BinanceService binanceService = new BinanceService();
 * Optional<AccountDto> account = binanceService.getCriptosBalance();
 * CriptoDto price = binanceService.getPrice("BTCBRL");
 * }</pre>
 *
 * @author [Your Name]
 * @see CriptoDto
 * @see AccountDto
 * @see TradeOrderDto
 * @see KlineDto
 */
@JBossLog
@ApplicationScoped
public class BinanceService {

  @Inject
  private final ObjectMapper objectMapper;

  @ConfigProperty(name = "bot.symbol.list")
  private String workingSymbols;

  private static final String BASE_URL = "https://api.binance.com/api/v3";

  private static final HttpClient httpClient = HttpClient.newHttpClient();

  public BinanceService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Retrieves the latest price for a specific symbol from Binance.
   *
   * <p>This method sends a GET request to Binance's <code>/ticker/price</code> endpoint
   * for a single trading pair (e.g., <code>BTCBRL</code>).</p>
   *
   * <p>If the request is successful, a {@link CriptoDto} is returned containing the symbol,
   * current price, and the local timestamp (formatted as <code>yyyy-MM-dd HH:mm:ss</code>) in the <code>lastUpdated</code> field.</p>
   *
   * <p>If the request fails or an exception occurs, a default {@link CriptoDto} is returned instead,
   * and the error is logged.</p>
   *
   * @param symbol the symbol of the trading pair (e.g., <code>BTCBRL</code>)
   * @return a {@link CriptoDto} containing the latest price and timestamp, or a default object on failure
   */
  public CriptoDto getPrice(String symbol) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/ticker/price?symbol=" + symbol))
        .GET()
        .build();

    CriptoDto cripto = null;

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        cripto = objectMapper.readValue(response.body(), new TypeReference<>() {
        });
        cripto.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      } else {
        log("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      log(e.getMessage());
      cripto = CriptoDto.defaultData();
    }

    return cripto;
  }

  /**
   * Retrieves the latest prices for a list of specified symbols from Binance.
   *
   * <p>This method sends a GET request to Binance's <code>/ticker/price</code> endpoint,
   * with a JSON-formatted list of trading pair symbols (e.g., <code>["BTCBRL","ETHBRL"]</code>).</p>
   *
   * <p>Each returned {@link CriptoDto} contains the symbol and its corresponding price.
   * The method also sets the current local timestamp (formatted as <code>yyyy-MM-dd HH:mm:ss</code>)
   * in the <code>lastUpdated</code> field of each DTO.</p>
   *
   * <p>If the request fails or an exception occurs, the error is logged and an empty list is returned.</p>
   *
   * @param symbolsJson a JSON array string representing the symbols (e.g., <code>["BTCBRL","ETHBRL"]</code>)
   * @return a list of {@link CriptoDto} objects with the latest prices and timestamps; or an empty list on failure
   */
  public List<CriptoDto> getPrices(String symbolsJson) {
    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/ticker/price?symbols=" + URLEncoder.encode(symbolsJson, StandardCharsets.UTF_8)))
        .GET()
        .build();

    List<CriptoDto> criptos = new ArrayList<>();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        criptos = objectMapper.readerForListOf(CriptoDto.class).readValue(response.body());
        criptos.forEach(c -> c.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
      } else {
        log("Error: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      log(e.getMessage());
    }

    return criptos;
  }

  /**
   * Retrieves candlestick (kline) data from Binance for a given trading pair.
   *
   * <p>This method sends a GET request to Binance's <code>/klines</code> endpoint,
   * fetching historical price data for the specified symbol, interval, and number of candles (limit).
   * The response is parsed into a list of {@link KlineDto} objects using a utility method.</p>
   *
   * <p>If the request fails or an exception occurs, the method logs the error
   * and returns an empty list.</p>
   *
   * @param symbol   the trading pair symbol (e.g., "BTCBRL")
   * @param interval the interval for each candlestick (e.g., "1m", "5m", "1h", "1d")
   * @param limit    the maximum number of candles to return (max allowed by Binance: 1000)
   * @return a list of {@link KlineDto} objects representing the candlestick data; empty if an error occurs
   */
  public static List<KlineDto> getCandles(String symbol, String interval, int limit) {

    HttpRequest request =
      HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit))
        .GET()
        .build();

    List<KlineDto> candles = new ArrayList<>();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        candles = CriptoUtils.parseKlines(MAPPER, response.body());
        log.debug("Getting candles for " + symbol + ", total: " + candles.size() + " candles");
      } else {
        log("Error getting candles: HTTP " + response.statusCode());
      }
    } catch (Exception e) {
      log(e.getMessage());
    }

    return candles;
  }

  /**
   * Retrieves the current server time from Binance.
   *
   * <p>This method sends a GET request to Binance's <code>/time</code> endpoint,
   * which returns the current server timestamp in milliseconds since the Unix epoch.
   * This is essential for synchronizing time-sensitive operations such as
   * authenticated API calls that require accurate timestamps.</p>
   *
   * <p>If the request succeeds (HTTP 200), it returns the server's timestamp as a {@code long}.
   * If the request fails or an exception occurs, it logs the issue and returns the current system time
   * via {@code System.currentTimeMillis()} as a fallback.</p>
   *
   * @return the Binance server time in milliseconds, or local system time if the request fails
   */
  public static long getBinanceServerTime() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/time"))
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = MAPPER.readTree(response.body());
        return json.get("serverTime").asLong();
      } else {
        log("Error getting serverTime: HTTP " + response.statusCode());
      }

    } catch (Exception e) {
      log("Error getting serverTime: " + e.getMessage());
    }

    return System.currentTimeMillis();
  }

  /**
   * Retrieves the user's cryptocurrency balances from Binance via the <code>/account</code> endpoint.
   *
   * <p>This method sends an authenticated GET request to Binance's <code>/account</code> API,
   * signed using the user's secret key, and retrieves all balances associated with the account.</p>
   *
   * <p>After parsing the response into an {@code AccountDto} object, it filters the balances
   * based on the configured <code>workingSymbols</code> (comma-separated string), keeping only the
   * balances relevant to the application’s trading context.</p>
   *
   * <p>If the request is successful (HTTP 200), the filtered balances are returned inside
   * an {@code Optional<AccountDto>}. Otherwise, an empty Optional is returned and the error is logged.</p>
   *
   * @return an {@code Optional<AccountDto>} containing filtered crypto balances,
   *         or {@code Optional.empty()} if the request fails or parsing fails
   */
  public Optional<AccountDto> getCriptosBalance() {
    String queryString = "timestamp=" + getBinanceServerTime();
    CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();

    try {
      String signature = generateSignature(queryString, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/account?" + queryString + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        AccountDto account = MAPPER.readValue(response.body(), AccountDto.class);
        account.filterBalances(workingSymbols.split(","));
        Optional<AccountDto> optionalAccount = Optional.of(account);
        log.debug("Account criptos balance: " + MAPPER.writeValueAsString(optionalAccount));
        return optionalAccount;
      } else {
        log("Error getting balance: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Error getting balance: " + e.getMessage());
    }

    return Optional.empty();
  }

  /**
   * Places a market buy order on Binance for the specified trading symbol using a fixed amount in BRL.
   *
   * <p>This method builds and sends a signed request to Binance's <code>/order</code> endpoint
   * using the <code>quoteOrderQty</code> parameter, which allows purchasing a crypto asset
   * with a specified amount of quote currency (e.g., buying BTC with 100 BRL).</p>
   *
   * <p>If the request succeeds (HTTP 200), the response is parsed into a {@code TradeOrderDto}
   * and returned wrapped in an {@code Optional}. If the request fails, it logs the error and returns {@code Optional.empty()}.</p>
   *
   * @param symbol  the trading pair symbol to buy (e.g., "BTCBRL")
   * @param quantity the amount in BRL (quote currency) to spend on the purchase (e.g., new BigDecimal("100"))
   * @return an {@code Optional<TradeOrderDto>} with Binance’s response details if the order succeeds,
   *         or {@code Optional.empty()} if the request fails or is rejected
   */
  public static Optional<TradeOrderDto> placeBuyOrder(String symbol, BigDecimal quantity) {
    long timestamp = getBinanceServerTime();
    String queryString = String.format("symbol=%s&side=BUY&type=MARKET&quoteOrderQty=%s&timestamp=%d", symbol, quantity.toPlainString(), timestamp);

    CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();

    try {
      String signature = generateSignature(queryString, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/order?" + queryString + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        TradeOrderDto tradeOrder = MAPPER.readValue(response.body(), TradeOrderDto.class);
        log.debug("Buying order response --> " + MAPPER.writeValueAsString(tradeOrder));
        return Optional.of(tradeOrder);
      } else {
        log("Purchase error: HTTP " + response.statusCode() + " - " + response.body());
      }
    } catch (Exception e) {
      log("Purchase error: " + e.getMessage());
    }

    return Optional.empty();
  }

  /**
   * Places a market sell order on Binance for the given trading symbol (e.g., "BTCBRL").
   *
   * <p>This method performs the following steps:</p>
   * <ul>
   *   <li>Retrieves the step size (LOT_SIZE) for the symbol to ensure the quantity being sold is valid.</li>
   *   <li>Fetches the user's free balance of the crypto asset involved in the trading pair (e.g., BTC for BTCBRL).</li>
   *   <li>Adjusts the crypto balance downward to conform to the symbol's required step size, if necessary.</li>
   *   <li>Constructs a signed query to send a market sell order to Binance using the <code>/order</code> endpoint.</li>
   * </ul>
   *
   * <p>If the request is successful (HTTP 200), it parses and returns the trade order response as a {@code TradeOrderDto}.
   * Otherwise, it logs the error and returns {@code Optional.empty()}.</p>
   *
   * @param symbol the trading pair symbol to sell (e.g., "BTCBRL")
   * @return an {@code Optional<TradeOrderDto>} containing the Binance response details, or {@code Optional.empty()} if the order failed
   * @throws TradeException if the account details or the asset balance for the symbol cannot be found
   */
  public Optional<TradeOrderDto> placeSellOrder(String symbol) {
    BigDecimal lotSizeToBeSold = getLotSize(symbol);
    BigDecimal criptoBalance =
      getCriptosBalance()
        .orElseThrow(() -> new TradeException(ErrorCode.ACCOUNT_DETAILS_NOT_FOUND.getMessage()))
        .balances()
        .stream()
        .filter(b -> symbol.replaceFirst("BRL$", "").equals(b.asset()))
        .findFirst()
        .orElseThrow(() -> new TradeException(ErrorCode.BALANCE_NOT_FOUND.getMessage()))
        .free();

    if (lotSizeToBeSold != null && criptoBalance.compareTo(lotSizeToBeSold) != 0){
      log.debug(symbol + " balance: " + criptoBalance);
      criptoBalance = roundDownToStepSize(criptoBalance, lotSizeToBeSold);
      log.debug("Adjusted crypto balance to match step size: " + criptoBalance);
    }

    long timestamp = getBinanceServerTime();

    String queryString = String.format("symbol=%s&side=SELL&type=MARKET&quantity=%s&timestamp=%d", symbol, criptoBalance.toPlainString(), timestamp);

    CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();

    try {
      String signature = generateSignature(queryString, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/order?" + queryString + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        TradeOrderDto tradeOrder = MAPPER.readValue(response.body(), TradeOrderDto.class);
        log.debug("Selling order response --> " + MAPPER.writeValueAsString(tradeOrder));
        return Optional.of(tradeOrder);
      } else {
        log("Selling error: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Selling error: " + e.getMessage());
    }

    return Optional.empty();
  }

  /**
   * Retrieves the minimum notional value (in BRL) required to buy a given symbol on Binance.
   * <p>
   * The "minNotional" value is part of the "NOTIONAL" filter, which defines the minimum
   * total value of a trade (price * quantity) allowed for the specified symbol.
   * </p>
   *
   * @param symbol the trading pair symbol (e.g., "BTCBRL")
   * @return the minimum notional value as a BigDecimal, or null if not found or request fails
   */
  public static BigDecimal getMinNotional(String symbol) {
    JsonNode filter = getFilter(symbol, FilterType.NOTIONAL);
    if (filter != null && filter.has("minNotional")) {
      return new BigDecimal(filter.get("minNotional").asText())
        .setScale(8, RoundingMode.HALF_UP);
    }
    return null;
  }

  /**
   * Retrieves the step size used to define the valid quantity increments for selling a symbol on Binance.
   * <p>
   * The "stepSize" value is part of the "LOT_SIZE" filter, which dictates the precision and allowed increments
   * when specifying the amount of the asset (e.g., BTC) to be sold. For example, if stepSize = 0.00001,
   * only quantities like 0.00001, 0.00002, etc., are valid.
   * </p>
   *
   * @param symbol the trading pair symbol (e.g., "BTCBRL")
   * @return the step size as a BigDecimal, or null if not found or request fails
   */
  public static BigDecimal getLotSize(String symbol) {
    JsonNode filter = getFilter(symbol, FilterType.LOT_SIZE);
    if (filter != null && filter.has("stepSize")) {
      return new BigDecimal(filter.get("stepSize").asText()).setScale(8, RoundingMode.HALF_UP);
    }
    return null;
  }

  /**
   * Fetches a specific filter type (such as LOT_SIZE, NOTIONAL, etc.) from Binance's exchange information
   * for the provided trading symbol.
   *
   * <p>This method sends a request to Binance's <code>/exchangeInfo</code> endpoint to retrieve
   * trading rules and filters associated with a symbol (e.g., BTCBRL). It then parses and returns
   * the JSON node corresponding to the requested filter type.</p>
   *
   * @param symbol     the trading pair symbol (e.g., "BTCBRL")
   * @param filterType the filter type to retrieve (e.g., FilterType.LOT_SIZE)
   * @return the corresponding filter as a JsonNode, or null if the filter is not found or the request fails
   */
  private static JsonNode getFilter(String symbol, FilterType filterType) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/exchangeInfo?symbol=" + symbol))
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = MAPPER.readTree(response.body());
        JsonNode filters = json.get("symbols").get(0).get("filters");

        for (JsonNode filter : filters) {
          if (filterType.name().equalsIgnoreCase(filter.get("filterType").asText())) {
            return filter;
          }
        }

        log("Filter not found: " + filterType + " for symbol: " + symbol);
      } else {
        log("Exchange info error: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Error getting filter: " + e.getMessage());
    }

    return null;
  }

  /**
   * Retrieves the BRL balance (free and locked) from the user's Binance account.
   *
   * <p>This method uses the Binance <code>/sapi/v1/capital/config/getall</code> endpoint
   * to fetch all balances for the authenticated user, then filters the result to extract
   * only the BRL (Brazilian Real) coin data.</p>
   *
   * <p>It requires valid API key and secret credentials, which must be retrieved through
   * the {@code CredentialsConfig.getCriptoCredentials()} method. The request is signed
   * using HMAC-SHA256 as required by Binance's API security rules.</p>
   *
   * @return an {@code Optional<BalanceDto>} containing the BRL balance (free and locked),
   *         or an empty Optional if the request fails or no BRL balance is found
   */
  public static Optional<BalanceDto> getBalance() {
    try {
      CriptoCredentials criptoCredentials = CredentialsConfig.getCriptoCredentials();
      long timestamp = getBinanceServerTime();
      String query = "recvWindow=5000&timestamp=" + timestamp;
      String signature = generateSignature(query, criptoCredentials.secretKey());

      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.binance.com/sapi/v1/capital/config/getall?" + query + "&signature=" + signature))
        .header("X-MBX-APIKEY", criptoCredentials.apiKey())
        .GET()
        .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        try {
          JsonNode array = MAPPER.readTree(response.body());
          for (JsonNode node : array) {

            if ("BRL".equalsIgnoreCase(node.get("coin").asText())) {
              BigDecimal free = new BigDecimal(node.get("free").asText());
              BigDecimal locked = new BigDecimal(node.get("locked").asText());
              Optional<BalanceDto> balance = Optional.of(new BalanceDto("BRL", free, locked));
              log.debug("BRL Balance: " + balance.get());
              return balance;
            }
          }
        } catch (Exception e) {
          log("Error converting BRL Balance: " + e.getMessage());
        }
      } else {
        log("Error Obtaining General Balance: HTTP " + response.statusCode() + " - " + response.body());
      }

    } catch (Exception e) {
      log("Error to consult general balance: " + e.getMessage());
    }
    return Optional.empty();
  }

}
