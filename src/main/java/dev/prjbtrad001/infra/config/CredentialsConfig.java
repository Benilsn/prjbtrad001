package dev.prjbtrad001.infra.config;

import dev.prjbtrad001.app.bot.CriptoCredentials;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Configuration class responsible for loading and providing Binance API credentials application-wide.
 *
 * <p>Annotated with {@code @Startup} to ensure this bean is eagerly initialized when the application starts,
 * and {@code @ApplicationScoped} to provide a singleton instance for the entire application lifecycle.</p>
 *
 * <p>This class typically loads API credentials from secure storage or configuration files
 * and makes them available for injection or static access throughout the application.</p>
 */
@Startup
@ApplicationScoped
public class CredentialsConfig {

  @ConfigProperty(name = "bot.credentials.path.api-key")
  private String API_KEY_PATH;

  @ConfigProperty(name = "bot.credentials.path.secret-key")
  private String SECRET_KEY_PATH;

  @Getter
  private static CriptoCredentials criptoCredentials;

  /**
   * Loads the Binance API credentials from predefined file paths after the bean's construction.
   *
   * <p>This method is annotated with {@code @PostConstruct} to automatically execute
   * after dependency injection is done. It reads the API key and secret key from
   * specified files, trims whitespace, and initializes the {@code criptoCredentials} object.</p>
   *
   * <p>If reading the files fails, it logs the error message to standard error
   * and assigns default credentials to {@code criptoCredentials}.</p>
   */
  @PostConstruct
  private void loadCredentials() {
    try {
      String apiKey = Files.readString(Paths.get(API_KEY_PATH)).trim();
      String secretKey = Files.readString(Paths.get(SECRET_KEY_PATH)).trim();
      criptoCredentials = CriptoCredentials.of(apiKey, secretKey);
    } catch (IOException e) {
      System.err.println("Error reading credentials: " + e.getMessage());
      criptoCredentials = CriptoCredentials.DEFAULT;
    }
  }

}
