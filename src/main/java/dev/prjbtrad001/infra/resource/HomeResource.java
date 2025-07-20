package dev.prjbtrad001.infra.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.dto.Cripto;
import dev.prjbtrad001.app.service.BinanceService;
import dev.prjbtrad001.app.service.BotOrchestratorService;
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

@Path("/")
@JBossLog
public class HomeResource {

  @Inject BinanceService binanceService;
  @Inject BotOrchestratorService botOrchestratorService;
  @Inject ObjectMapper mapper;

  @ConfigProperty(name = "bot.symbol.list")
  private String workingSymbols;

  @GET
  public TemplateInstance homePage() {
    return Templates.home()
      .data("pageTitle", "btrad001")
      .data("workingSymbols", workingSymbols)
      .data("data", botOrchestratorService.getLogData());
  }

  @GET()
  @Path("/refresh-data")
  @Produces(MediaType.APPLICATION_JSON)
  public List<Cripto> refresh(@QueryParam("symbols") String symbolsJson) {
    log.info("Refreshing data for symbols: " + symbolsJson);
    return binanceService.getPrices(symbolsJson);
  }


}
