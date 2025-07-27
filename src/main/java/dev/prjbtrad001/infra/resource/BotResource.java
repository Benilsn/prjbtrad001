package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.service.BotOrchestratorService;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.prjbtrad001.app.utils.FormatterUtils.getLastFiveCharacters;

@Path("/bots")
public class BotResource {

  @Inject
  Validator validator;
  @Inject
  BotOrchestratorService botOrchestratorService;

  @ConfigProperty(name = "bot.symbol.list")
  private String workingSymbols;

  @GET
  public TemplateInstance allBots(@QueryParam("message") String message) {
    return Templates.allBots()
      .data("pageTitle", "All Bots")
      .data("message", message)
      .data("allBots", botOrchestratorService.getAllBots());
  }

  @GET
  @Path("/create")
  public TemplateInstance createBot() {
    return Templates.createBot()
      .data("workingSymbols", workingSymbols.split(","))
      .data("pageTitle", "Create Bot");
  }

  @POST
  @Path("/save")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Object saveBot(
    @BeanParam BotParameters parameters,
    @FormParam("botId") UUID botId) {
    Set<ConstraintViolation<BotParameters>> violations;

    if (botId != null) {
      violations = validator.validate(parameters, BotParameters.Update.class);
    } else {
      violations = validator.validate(parameters, BotParameters.Create.class);
    }


    if (!violations.isEmpty()) {
      List<String> errors = violations.stream()
        .map(ConstraintViolation::getMessage)
        .toList();

      return
        createBot()
          .data("errors", errors)
          .data("parameters", parameters);
    }

    String msg = "Bot created successfully!";
    if (botId != null) {
      botOrchestratorService.updateBot(parameters, botId);
      msg = "Bot updated successfully!";
    } else {
      botOrchestratorService.createBot(new SimpleTradeBot(parameters));
    }

    return
      Response
        .seeOther(UriBuilder.fromPath("/bots")
          .queryParam("message", msg)
          .build())
        .build();
  }

  @POST
  @Path("/delete")
  public Response deleteBot(@FormParam("botId") UUID botId) {
    botOrchestratorService.deleteBot(botId);
    return
      Response
        .seeOther(UriBuilder.fromPath("/bots")
          .queryParam("message", "Bot deleted successfully!")
          .build())
        .build();
  }

  @GET
  @Path("/edit/{botId}")
  public TemplateInstance editBot(@PathParam("botId") UUID botId) {
    SimpleTradeBot bot = botOrchestratorService.getBotById(botId);
    return Templates.createBot()
      .data("workingSymbols", workingSymbols.split(","))
      .data("pageTitle", "Create Bot")
      .data("botId", botId)
      .data("bot", bot);
  }

  @GET
  @Path("/start/{botId}")
  public Response startBot(@PathParam("botId") UUID botId) {
    botOrchestratorService.startBot(botId);
    return
      Response
        .seeOther(UriBuilder.fromPath("/bots")
          .queryParam("message", "Bot  " + getLastFiveCharacters(botId.toString()) + "  STARTED!")
          .build())
        .build();
  }

  @GET
  @Path("/stop/{botId}")
  public Response stopBot(@PathParam("botId") UUID botId) {
    botOrchestratorService.stopBot(botId);
    return
      Response
        .seeOther(UriBuilder.fromPath("/bots")
          .queryParam("message", "Bot  " + getLastFiveCharacters(botId.toString()) + "  \nSTOPPED!")
          .build())
        .build();
  }


}
