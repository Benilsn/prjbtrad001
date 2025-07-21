package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.service.BinanceService;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.util.List;

@Path("/cryptos")
public class CryptoResource {

    @Inject
    BinanceService binanceService;

    @Inject
    @Location("cryptoList.html")
    Template cryptoListTemplate;

    @GET
    @Path("/list")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance renderCryptoList() throws IOException, InterruptedException {
        List<String> symbols = binanceService.getAllSymbols();
        return cryptoListTemplate
                .data("symbols", symbols)
                .data("pageTitle", "Lista de Criptomoedas");
    }
}
