package dev.prjbtrad001.app.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.jbosslog.JBossLog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@JBossLog
@UtilityClass
public class LogUtils {

  public final Queue<String> LOG_DATA = new ConcurrentLinkedQueue<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final String logFilePath = "C:\\Users\\benil\\Desktop\\trade-log\\" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".log";
  private final File file = new File(logFilePath);

  public static void log(String message) {
    log.info(message);

    String timestamp = "[" +java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")) + "] - ";
    String finalMessage = timestamp.concat(message);

    LOG_DATA.add(finalMessage);
    executor.submit(() -> writeToFile(finalMessage));
  }

  public static void log(String message, boolean includeTimestamp) {
    log.info(message);

    String timestamp = "";
    if (includeTimestamp) {
      timestamp = "[" +java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")) + "] - ";
    }
    String finalMessage = timestamp.concat(message);

    LOG_DATA.add(finalMessage);
    executor.submit(() -> writeToFile(finalMessage));
  }

  private void writeToFile(String message) {
    if (file.getParentFile() != null) {
      file.getParentFile().mkdirs();
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
      writer.write(message);
      writer.newLine();
    } catch (IOException e) {
      log.error("Failed to write log to file: " + e.getMessage());
    }
  }

  public static void shutdown() {
    LOG_DATA.clear();
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
    }
  }

}
