package dev.prjbtrad001.infra.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = SymbolValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSymbol {

  String message() default "Símbolo inválido. Exemplo válido: BTCUSDT";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}