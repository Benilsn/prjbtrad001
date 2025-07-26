package dev.prjbtrad001.app.bot;

import java.math.BigDecimal;

public class Wallet {

  private static volatile BigDecimal BALANCE = new BigDecimal("1000.0");

  public static synchronized BigDecimal get() {
    return BALANCE;
  }

  public static synchronized void deposit(BigDecimal amount) {
    BALANCE = BALANCE.add(amount);
  }

  public static synchronized void withdraw(BigDecimal amount) {
    BALANCE = BALANCE.subtract(amount);
  }

}