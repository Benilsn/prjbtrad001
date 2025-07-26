package dev.prjbtrad001.domain.repository;

import dev.prjbtrad001.domain.core.BotType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRepository<T> extends PanacheRepositoryBase<T, UUID> {

  void createBot(T tradeBot);
  List<T> getAllBots();
  Optional<T> getBotByType(BotType botType);
  Optional<T> getBotById(UUID botId);
  void deleteBot(UUID botId);

//  void updateBot(BotType botTypeName);

}
