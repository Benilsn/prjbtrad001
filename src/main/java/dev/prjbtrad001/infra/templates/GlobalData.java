package dev.prjbtrad001.infra.templates;

import dev.prjbtrad001.app.bot.Wallet;
import io.quarkus.qute.TemplateGlobal;

import static dev.prjbtrad001.app.utils.FormatterUtils.FORMATTER2;

@TemplateGlobal
public class GlobalData {

  public static String walletBalance() {
    return FORMATTER2.format(Wallet.get());
  }

}