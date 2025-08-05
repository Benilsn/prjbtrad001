package dev.prjbtrad001.infra.exception;

import dev.prjbtrad001.infra.resource.Templates;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

  @Override
  public Response toResponse(ConstraintViolationException e) {
    List<String> errors = e.getConstraintViolations().stream()
      .map(ConstraintViolation::getMessage)
      .toList();

    return Response.ok(
      Templates.createBot()
        .data("errors", errors)
        .data("workingSymbols", workingSymbols.split(","))
        .data("pageTitle", "Create Bot")
    ).build();
  }

  @Inject
  @ConfigProperty(name = "bot.symbol.list")
  private String workingSymbols;
}