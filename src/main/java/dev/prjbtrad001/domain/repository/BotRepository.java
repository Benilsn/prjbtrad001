package dev.prjbtrad001.domain.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.core.TradeBot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@JBossLog
@ApplicationScoped
public class BotRepository {

  private static final File botFile = Paths.get("src/main/resources/bots").toFile();

  @Inject
  private ObjectMapper mapper;

  public void createBot(TradeBot tradeBot) {
    try {
      String jsonLine = mapper.writeValueAsString(tradeBot);

      try (FileWriter fw = new FileWriter(botFile, true)) {
        fw.write(jsonLine + System.lineSeparator());
      }
    } catch (Exception e) {
      log.error("Error creating bot: " + e.getMessage());
    }
  }

  public List<TradeBot> getAllBots() {
    List<TradeBot> bots = new ArrayList<>();

    if (!botFile.exists()) {
      return bots;
    }

    try (BufferedReader reader = new BufferedReader(new FileReader(botFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        try {
          SimpleTradeBot bot = mapper.readValue(line, SimpleTradeBot.class);
          bots.add(bot);
        } catch (Exception e) {
          // Log malformed JSON if needed
          System.err.println("Skipping malformed line: " + line);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read bots file", e);
    }

    return bots;
  }


}
