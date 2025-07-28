package dev.prjbtrad001.infra.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
  INVALID_CREDENTIALS("Invalid credentials provided"),
  INSUFFICIENT_FUNDS("Insufficient funds for the trade"),
  TRADE_NOT_FOUND("Trade not found"),
  UNAUTHORIZED_ACCESS("Unauthorized access to the trade resource"),
  INVALID_TRADE_AMOUNT("Invalid trade amount specified"),
  RATE_LIMIT_EXCEEDED("Rate limit exceeded for API requests"),
  BALANCE_NOT_FOUND("Balance not found for the specified asset"),
  ACCOUNT_DETAILS_NOT_FOUND("Account details not found"),
  FAILED_TO_PLACE_SELL_ORDER("Failed to place the sell order"),
  FAILED_TO_PLACE_BUY_ORDER("Failed to place the buy order");

  private final String message;

  ErrorCode(String message) {
    this.message = message;
  }
}
