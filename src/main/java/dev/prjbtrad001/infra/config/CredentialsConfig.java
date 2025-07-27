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

@Startup
@ApplicationScoped
public class CredentialsConfig {

  @ConfigProperty(name = "bot.credentials.path.api-key")
  private String API_KEY_PATH;

  @ConfigProperty(name = "bot.credentials.path.secret-key")
  private String SECRET_KEY_PATH;

  @Getter
  private static CriptoCredentials criptoCredentials;

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
