package dev.prjbtrad001.domain.repository;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.core.BotType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRepository extends PanacheRepositoryBase<SimpleTradeBot, UUID> {

  void createBot(SimpleTradeBot tradeBot);
  List<SimpleTradeBot> getAllBots();
  Optional<SimpleTradeBot> getBotByType(BotType botType);
  Optional<SimpleTradeBot> getBotById(UUID botId);
  void deleteBot(UUID botId);

//  void updateBot(BotType botTypeName);

}
