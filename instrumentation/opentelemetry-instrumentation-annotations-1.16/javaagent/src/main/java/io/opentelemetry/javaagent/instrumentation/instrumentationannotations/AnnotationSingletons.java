/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import static java.util.logging.Level.FINE;

import application.io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.MethodSpanAttributesExtractor;
import io.opentelemetry.instrumentation.api.annotation.support.SpanAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.util.SpanNames;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class AnnotationSingletons {

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-1.16";

  private static final Logger logger = Logger.getLogger(AnnotationSingletons.class.getName());
  private static final Instrumenter<Method, Object> INSTRUMENTER = createInstrumenter();
  private static final Instrumenter<MethodRequest, Object> INSTRUMENTER_WITH_ATTRIBUTES =
      createInstrumenterWithAttributes();
  private static final SpanAttributesExtractor ATTRIBUTES = createAttributesExtractor();

  // The reason for using reflection here is that it needs to be compatible with the old version of
  // @WithSpan annotation that does not include the withParent option to avoid failing the muzzle
  // check.
  private static MethodHandle withParentMethodHandle = null;

  static {
    try {
      withParentMethodHandle =
          MethodHandles.publicLookup()
              .findVirtual(WithSpan.class, "withParent", MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException ignore) {
      // ignore
    }
  }

  public static Instrumenter<Method, Object> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<MethodRequest, Object> instrumenterWithAttributes() {
    return INSTRUMENTER_WITH_ATTRIBUTES;
  }

  public static SpanAttributesExtractor attributes() {
    return ATTRIBUTES;
  }

  private static Instrumenter<Method, Object> createInstrumenter() {
    return Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            AnnotationSingletons::spanNameFromMethod)
        .addAttributesExtractor(CodeAttributesExtractor.create(MethodCodeAttributesGetter.INSTANCE))
        .addContextCustomizer(AnnotationSingletons::parentContextFromMethod)
        .buildInstrumenter(AnnotationSingletons::spanKindFromMethod);
  }

  private static Instrumenter<MethodRequest, Object> createInstrumenterWithAttributes() {
    return Instrumenter.builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            AnnotationSingletons::spanNameFromMethodRequest)
        .addAttributesExtractor(
            CodeAttributesExtractor.create(MethodRequestCodeAttributesGetter.INSTANCE))
        .addAttributesExtractor(
            MethodSpanAttributesExtractor.create(
                MethodRequest::method,
                WithSpanParameterAttributeNamesExtractor.INSTANCE,
                MethodRequest::args))
        .addContextCustomizer(AnnotationSingletons::parentContextFromMethodRequest)
        .buildInstrumenter(AnnotationSingletons::spanKindFromMethodRequest);
  }

  private static SpanAttributesExtractor createAttributesExtractor() {
    return SpanAttributesExtractor.create(WithSpanParameterAttributeNamesExtractor.INSTANCE);
  }

  private static SpanKind spanKindFromMethodRequest(MethodRequest request) {
    return spanKindFromMethod(request.method());
  }

  private static SpanKind spanKindFromMethod(Method method) {
    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);
    if (annotation == null) {
      return SpanKind.INTERNAL;
    }
    return toAgentOrNull(annotation.kind());
  }

  private static SpanKind toAgentOrNull(
      application.io.opentelemetry.api.trace.SpanKind applicationSpanKind) {
    try {
      return SpanKind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "unexpected span kind: {0}", applicationSpanKind.name());
      return SpanKind.INTERNAL;
    }
  }

  private static String spanNameFromMethodRequest(MethodRequest request) {
    return spanNameFromMethod(request.method());
  }

  private static String spanNameFromMethod(Method method) {
    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);
    String spanName = annotation.value();
    if (spanName.isEmpty()) {
      spanName = SpanNames.fromMethod(method);
    }
    return spanName;
  }

  private static Context parentContextFromMethodRequest(
      Context context, MethodRequest request, Attributes attributes) {
    return parentContextFromMethod(context, request.method(), attributes);
  }

  private static Context parentContextFromMethod(
      Context context, Method method, Attributes attributes) {
    if (withParentMethodHandle == null) {
      return context;
    }

    WithSpan annotation = method.getDeclaredAnnotation(WithSpan.class);

    boolean withParent = true;
    try {
      withParent = (boolean) withParentMethodHandle.invoke(annotation);
    } catch (Throwable ignore) {
      // ignore
    }

    return withParent ? context : Context.root();
  }

  private AnnotationSingletons() {}
}
