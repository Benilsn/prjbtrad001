package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.service.BotOrchestratorService;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/bots")
public class BotResource {

  @Inject
  BotOrchestratorService botOrchestratorService;

  @GET()
  @Path("/active")
  public TemplateInstance activebots() {
    return Templates.activeBots()
      .data("pageTitle", "Active Bots")
      .data("data", botOrchestratorService.getLogData());
//      .data("activeBots", botOrchestratorService.getActiveBots());
  }

  @GET()
  @Path("/create")
  public TemplateInstance createBot() {
    return Templates.createBot()
      .data("pageTitle", "Create Bot");
  }

}
