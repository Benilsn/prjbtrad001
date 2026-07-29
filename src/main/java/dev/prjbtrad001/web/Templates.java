package dev.prjbtrad001.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

/**
 * Type-safe references to the Qute templates under {@code src/main/resources/templates}.
 * Type-safe expression checking is relaxed so templates can call helper methods
 * and receive dynamically-keyed data.
 */
@CheckedTemplate(requireTypeSafeExpressions = false)
public class Templates {
  public static native TemplateInstance dashboard();

  public static native TemplateInstance botForm();

  public static native TemplateInstance backtest();
}
