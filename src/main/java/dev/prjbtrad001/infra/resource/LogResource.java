package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.utils.LogUtils;
import dev.prjbtrad001.infra.templates.Templates;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Queue;

@Path("/")
@ApplicationScoped
public class LogResource {

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
