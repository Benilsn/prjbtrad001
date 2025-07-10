package dev.prjbtrad001.resource;

import dev.prjbtrad001.service.BinanceService;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/")
public class HomeResource {

    @Inject
    BinanceService binanceService;

    @GET
    public TemplateInstance homePage() {
        return Templates.home()
                .data("pageTitle", "btrad001")
                .data("btcData", binanceService.bitcoinData())
                .data("etcData", binanceService.bitcoinData())
                .data("data", binanceService.getLogData());
    }
}
