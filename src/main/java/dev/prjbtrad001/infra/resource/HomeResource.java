package dev.prjbtrad001.infra.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.dto.BalanceDto;
import dev.prjbtrad001.app.dto.CriptoDto;
import dev.prjbtrad001.app.service.BinanceService;
import dev.prjbtrad001.app.utils.LogUtils;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;

import static dev.prjbtrad001.app.utils.FormatterUtils.FORMATTER2;

@Path("/")
@JBossLog
public class HomeResource {

  @Inject
  BinanceService binanceService;
  @Inject
  ObjectMapper mapper;

  @ConfigProperty(name = "bot.symbol.list")
  private String workingSymbols;

  @GET
  public TemplateInstance homePage() {
    return Templates.home()
      .data("pageTitle", "btrad001")
      .data("workingSymbols", workingSymbols)
      .data("criptos", workingSymbols.split(","));
  }

  @GET()
  @Path("/refresh-data")
  @Produces(MediaType.APPLICATION_JSON)
  public List<CriptoDto> refreshCriptoData(@QueryParam("symbols") String symbolsJson) {
    log.info("Refreshing data for symbols: " + symbolsJson);
    return binanceService.getPrices(symbolsJson);
  }

  @GET()
  @Path("/refresh-wallet")
  public String refreshWallet() {
    log.info("Refreshing wallet data...");
    return String.format("R$%s", FORMATTER2.format(binanceService.getBalance().orElse(new BalanceDto(null, BigDecimal.ZERO, null)).balance()));
  }

  @GET
  @Path("/trade-log")
  public TemplateInstance tradeLog() {
    return Templates.tradeLog()
      .data("pageTitle", "Trade Log");
  }

  @GET()
  @Path("/refresh-log-data")
  @Produces(MediaType.APPLICATION_JSON)
  public Queue<String> refreshLogData() {
    return LogUtils.LOG_DATA;
  }


}
