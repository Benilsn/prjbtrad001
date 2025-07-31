package dev.prjbtrad001.infra.exception;

public class BotOperationException extends RuntimeException {

  public BotOperationException(String message, Throwable throwable) {
    super(message, throwable);
  }

}
