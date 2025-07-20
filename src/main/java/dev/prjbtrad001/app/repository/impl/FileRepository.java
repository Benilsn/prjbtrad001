package dev.prjbtrad001.app.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.domain.repository.BotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Named;
import lombok.extern.jbosslog.JBossLog;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Default
@JBossLog
@Named(value = "filedb")
@ApplicationScoped
public class FileRepository implements BotRepository<SimpleTradeBot> {

  private static final File botFile = Paths.get("src/main/resources/data/bots").toFile();

  private ObjectMapper mapper;

  public FileRepository(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void createBot(SimpleTradeBot tradeBot) {
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
  public List<SimpleTradeBot> getAllBots() {
    List<SimpleTradeBot> bots = new ArrayList<>();

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
          log.error("Skipping malformed line: " + line);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read bots file", e);
    }

    return bots;
  }

  @Override
  public Optional<SimpleTradeBot> getBotByType(BotType botType) {
    return
      getAllBots()
        .stream()
        .filter(b -> b.getParameters().getBotType().equals(botType))
        .findFirst();
  }

  @Override
  public Optional<SimpleTradeBot> getBotById(UUID botId) {
    return
      getAllBots()
        .stream()
        .filter(b -> b.getId().equals(botId))
        .findFirst();
  }

  @Override
  public void deleteBot(UUID botId) {
    List<SimpleTradeBot> bots = getAllBots();

    bots.removeIf(bot -> bot.getId().equals(botId));
    try (FileWriter fw = new FileWriter(botFile, false)) {
      for (SimpleTradeBot bot : bots) {
        String jsonLine = mapper.writeValueAsString(bot);
        fw.write(jsonLine + System.lineSeparator());
      }
    } catch (Exception e) {
      log.error("Error deleting bot: " + e.getMessage());
    }
  }


}
