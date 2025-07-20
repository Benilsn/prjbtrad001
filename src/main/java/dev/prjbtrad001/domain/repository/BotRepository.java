package dev.prjbtrad001.domain.repository;

import dev.prjbtrad001.domain.core.TradeBot;
import java.util.List;

public interface BotRepository {

  void createBot(TradeBot tradeBot);
  List<TradeBot> getAllBots();

//  TradeBot getBotByType(TradeBot.BotType botType);
//  void updateBot(TradeBot.BotType botType);
//  void deleteBot(TradeBot.BotType botType);

}
