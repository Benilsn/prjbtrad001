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
  public static final String LINE_SEPARATOR = "--------------------------------------------------------------------------------------------------";
  private final String defaultLogFilePath = "C:\\Users\\benil\\Desktop\\trade-log\\" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".log";
  private final File defautlFile = new File(defaultLogFilePath);

  public static void log(String message) {
    log.info(message);

    String timestamp = "[" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")) + "] - ";
    String finalMessage = timestamp.concat(message);

    LOG_DATA.add(finalMessage);
    executor.submit(() -> writeToFile(finalMessage, defautlFile));
  }

  public static void log(Queue<String> message) {
    log.info(message);

    message.forEach(msg -> {
      LOG_DATA.add(msg);
      executor.submit(() -> writeToFile(msg, defautlFile));
    });
  }

  public static void log(String message, boolean includeTimestamp) {
    log.info(message);

    String timestamp = "";
    if (includeTimestamp) {
      timestamp = "[" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")) + "] - ";
    }
    String finalMessage = timestamp.concat(message);

    LOG_DATA.add(finalMessage);
    executor.submit(() -> writeToFile(finalMessage, defautlFile));
  }

  public static void logCripto(Queue<String> message, String cripto) {
    log.info(message);
    message.add(LINE_SEPARATOR);
    final String criptoLogFilePath = "C:\\Users\\benil\\Desktop\\trade-log\\" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + "-" + cripto + ".log";
    final File cryptoFile = new File(criptoLogFilePath);

    message.forEach(msg -> {
      LOG_DATA.add(msg);
      executor.submit(() -> writeToFile(msg, cryptoFile));
    });
  }

  private void writeToFile(String data, File fileToWrite) {
    if (fileToWrite.getParentFile() != null) {
      fileToWrite.getParentFile().mkdirs();
    }

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToWrite, true))) {
      writer.write(data);
      writer.newLine();
    } catch (IOException e) {
      log.error("Failed to write log to file: " + e.getMessage());
    }
  }

  public static String buildLog(String message, boolean includeTimestamp) {
    String timestamp = "";
    if (includeTimestamp) {
      timestamp = "[" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")) + "] - ";
    }
    return timestamp.concat(message);
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
