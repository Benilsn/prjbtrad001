package dev.prjbtrad001.web;

import dev.prjbtrad001.domain.bot.TradeRecord;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to the execution history.
 *
 * Exists so trades can be analysed (or pulled by n8n) without handing out
 * database credentials.
 */
@Path("/trades")
public class TradeResource {

  private static final int MAX_LIMIT = 5000;

  @GET
  @Transactional
  @Produces(MediaType.APPLICATION_JSON)
  public List<TradeRecord> list(@QueryParam("botId") UUID botId,
                                @QueryParam("symbol") String symbol,
                                @QueryParam("limit") Integer limit) {
    int max = limit == null ? 500 : Math.min(Math.max(limit, 1), MAX_LIMIT);
    Sort newest = Sort.by("executedAt").descending();

    var query = botId != null
      ? TradeRecord.<TradeRecord>find("botId", newest, botId)
      : symbol != null
      ? TradeRecord.<TradeRecord>find("symbol", newest, Enum.valueOf(dev.prjbtrad001.domain.bot.BotType.class, symbol))
      : TradeRecord.<TradeRecord>findAll(newest);

    return query.page(0, max).list();
  }

  /** Same data as CSV — convenient for spreadsheets and quick analysis. */
  @GET
  @Path("/csv")
  @Transactional
  @Produces("text/csv")
  public Response csv(@QueryParam("botId") UUID botId,
                      @QueryParam("symbol") String symbol,
                      @QueryParam("limit") Integer limit) {
    StringBuilder sb = new StringBuilder(
      "executedAt,symbol,side,reason,timeframe,emaFast,emaSlow,price,quantity,notionalBrl,feeBrl,profitBrl,profitPct,botId\n");

    for (TradeRecord t : list(botId, symbol, limit)) {
      sb.append(t.getExecutedAt()).append(',')
        .append(t.getSymbol()).append(',')
        .append(t.getSide()).append(',')
        .append(t.getReason()).append(',')
        .append(t.getTimeframe()).append(',')
        .append(t.getEmaFast()).append(',')
        .append(t.getEmaSlow()).append(',')
        .append(nz(t.getPrice())).append(',')
        .append(nz(t.getQuantity())).append(',')
        .append(nz(t.getNotionalBrl())).append(',')
        .append(nz(t.getFeeBrl())).append(',')
        .append(nz(t.getProfitBrl())).append(',')
        .append(nz(t.getProfitPct())).append(',')
        .append(t.getBotId()).append('\n');
    }

    return Response.ok(sb.toString())
      .header("Content-Disposition", "attachment; filename=\"trades.csv\"")
      .build();
  }

  private static String nz(Object v) {
    return v == null ? "" : v.toString();
  }
}
