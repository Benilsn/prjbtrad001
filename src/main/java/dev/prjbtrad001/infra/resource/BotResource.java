package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.service.BotOrchestratorService;
import dev.prjbtrad001.domain.core.TradeBot;
import dev.prjbtrad001.app.bot.BotParameters;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import jakarta.validation.Validator;

import java.util.List;
import java.util.Set;

@Path("/bots")
public class BotResource {

  @Inject
  Validator validator;
  @Inject
  BotOrchestratorService botOrchestratorService;

  @GET()
  @Path("/running")
  public TemplateInstance activebots(@QueryParam("message") String message) {
    return Templates.activeBots()
      .data("pageTitle", "Active Bots")
      .data("message", message)
      .data("activeBots", botOrchestratorService.getAllBots());
  }

  @GET()
  @Path("/trade-log")
  public TemplateInstance tradeLog() {
    return Templates.tradeLog()
      .data("pageTitle", "Trade Log")
      .data("data", botOrchestratorService.getLogData());
  }

  @GET()
  @Path("/create")
  public TemplateInstance createBot() {
    return Templates.createBot()
      .data("pageTitle", "Create Bot");
  }

  @POST
  @Path("/save")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Object saveBot(
    @BeanParam BotParameters parameters) {
    Set<ConstraintViolation<BotParameters>> violations = validator.validate(parameters);

    if (!violations.isEmpty()) {
      List<String> errors = violations.stream()
        .map(ConstraintViolation::getMessage)
        .toList();

      return
        createBot()
          .data("errors", errors)
          .data("parameters", parameters);
    }

    botOrchestratorService
      .createBot(new SimpleTradeBot(parameters));

    return
      Response
        .seeOther(UriBuilder.fromPath("/bots/running")
          .queryParam("message", "Bot created successfully!")
          .build())
        .build();
  }

}
