package dev.prjbtrad001.infra.validation;

import dev.prjbtrad001.app.service.BotOrchestratorService;
import dev.prjbtrad001.domain.core.BotType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SymbolValidator implements ConstraintValidator<ValidSymbol, BotType> {

  @Inject BotOrchestratorService botOrchestratorService;

  @ConfigProperty (name = "bot.symbol.limit")
  int limitForSymbol;

  @Override
  public boolean isValid(BotType botType, ConstraintValidatorContext context) {

    var bots =
      botOrchestratorService
        .getAllBots()
        .stream()
        .filter(bot -> bot.getParameters().getBotType().equals(botType))
        .toList();

    context.buildConstraintViolationWithTemplate(
      "You can only have " + limitForSymbol + " bots of type " + botType + ".")
      .addConstraintViolation()
      .disableDefaultConstraintViolation();

    return bots.size() < limitForSymbol;
  }
}