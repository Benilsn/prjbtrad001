package dev.prjbtrad001.domain.repository;

import dev.prjbtrad001.domain.core.BotType;
import dev.prjbtrad001.domain.core.TradeBot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRepository {

  void createBot(TradeBot tradeBot);
  List<TradeBot> getAllBots();
  Optional<TradeBot> getBotByType(BotType botType);
  void deleteBot(UUID botId);

//  void updateBot(BotType botType);

}
