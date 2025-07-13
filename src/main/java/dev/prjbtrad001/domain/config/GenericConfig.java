package dev.prjbtrad001.domain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;

public class GenericConfig {

  @Produces
  @ApplicationScoped
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

}
