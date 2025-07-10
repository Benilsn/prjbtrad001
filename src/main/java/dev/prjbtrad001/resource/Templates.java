package dev.prjbtrad001.resource;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate(requireTypeSafeExpressions = false)
public class Templates {

    public static native TemplateInstance home();
}