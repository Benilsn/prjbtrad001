package dev.prjbtrad001.app.bot;

public class Wallet {

  private static volatile double BALANCE = 1000.0;

  public static synchronized double get() {
    return BALANCE;
  }

  public static synchronized void deposit(double amount) {
    BALANCE += amount;
  }

  public static synchronized void withdraw(double amount) {
    BALANCE -= amount;
  }

}