package dev.prjbtrad001.app.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.jbosslog.JBossLog;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@JBossLog
@UtilityClass
public class LogUtils {

  public static final List<String> LOG_DATA = new ArrayList<>();

  public static void log(String message) {
    String timestamp = "[" +java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] - ";
    log.info(message);
    LOG_DATA.add(timestamp.concat(message));
  }

}
