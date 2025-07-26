package dev.prjbtrad001.infra.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.Wallet;
import dev.prjbtrad001.app.dto.Cripto;
import dev.prjbtrad001.app.service.BinanceService;
import dev.prjbtrad001.infra.templates.Templates;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.List;

import static dev.prjbtrad001.app.utils.FormatterUtils.FORMATTER2;

@Path("/")
@JBossLog
public class HomeResource {

  @Inject BinanceService binanceService;
  @Inject ObjectMapper mapper;

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
  public List<Cripto> refreshCriptoData(@QueryParam("symbols") String symbolsJson) {
    log.info("Refreshing data for symbols: " + symbolsJson);
    return binanceService.getPrices(symbolsJson);
  }

  @GET()
  @Path("/refresh-wallet")
  public String refreshWallet() {
    log.info("Refreshing wallet data...");
    return String.format("R$%s", FORMATTER2.format(Wallet.get()));
  }


}
