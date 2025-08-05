package dev.prjbtrad001.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;

public class GenericConfig {

  public static final ObjectMapper MAPPER = new ObjectMapper();

  @Produces
  @ApplicationScoped
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

}
