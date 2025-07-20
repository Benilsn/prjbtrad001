package dev.prjbtrad001.app.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.domain.core.TradeBot;
import dev.prjbtrad001.domain.repository.BotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@JBossLog
@ApplicationScoped
public class FileRepository implements BotRepository {

  private static final File botFile = Paths.get("src/main/resources/bots").toFile();

  private ObjectMapper mapper;

  public FileRepository(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
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

  @Override
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
          log.debug("Skipping malformed line: " + line);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read bots file", e);
    }

    return bots;
  }

  @Override
  public Optional<TradeBot> getBotByType(BotType botType) {
    return
      getAllBots()
        .stream()
        .filter(b -> b.getParameters().getBotType().equals(botType))
        .findFirst();
  }

  @Override
  public void deleteBot(UUID botId) {
    List<TradeBot> bots = getAllBots();

    bots.removeIf(bot -> ((SimpleTradeBot) bot).getId().equals(botId));
    try (FileWriter fw = new FileWriter(botFile, false)) {
      for (TradeBot bot : bots) {
        String jsonLine = mapper.writeValueAsString(bot);
        fw.write(jsonLine + System.lineSeparator());
      }
    } catch (Exception e) {
      log.error("Error deleting bot: " + e.getMessage());
    }
  }


}
