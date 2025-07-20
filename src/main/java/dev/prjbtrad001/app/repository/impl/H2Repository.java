package dev.prjbtrad001.app.repository.impl;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.domain.repository.BotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Default
@Named(value = "h2db")
@ApplicationScoped
public class H2Repository implements BotRepository<SimpleTradeBot> {

  @Override
  @Transactional
  public void createBot(SimpleTradeBot tradeBot) {
    this.persist(tradeBot);
  }

  @Override
  @Transactional
  public List<SimpleTradeBot> getAllBots() {
    return this.listAll();
  }

  @Override
  @Transactional
  public Optional<SimpleTradeBot> getBotByType(BotType botType) {
    return Optional.empty();
  }

  @Override
  @Transactional
  public Optional<SimpleTradeBot> getBotById(UUID botId) {
    return Optional.ofNullable(this.findById(botId));
  }

  @Override
  @Transactional
  public void deleteBot(UUID botId) {
    this.deleteById(botId);
  }
}
