package com.solace.samples.features.opentelemetry.manualinstrumentation;

import com.solace.opentelemetry.javaagent.jms.SolaceJmsContextPropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.util.concurrent.TimeUnit;

public class TracingUtil {

  private TracingUtil() {
  }

  public static void initManualTracing(String serviceName) {
    //Create Resource
    Resource resource = Resource.getDefault().merge(Resource.create(
        Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName)));

    //Create Span Exporter
    OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
        //.setEndpoint("http://localhost:55680")
        .build();

    //Create SdkTracerProvider
    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(100, TimeUnit.MILLISECONDS).build())
        .setResource(resource)
        .build();

    //This Instance can be used to get tracer if it is not configured as global
    OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        //.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .setPropagators(ContextPropagators.create(new SolaceJmsContextPropagator()))
        .buildAndRegisterGlobal();
  }
}