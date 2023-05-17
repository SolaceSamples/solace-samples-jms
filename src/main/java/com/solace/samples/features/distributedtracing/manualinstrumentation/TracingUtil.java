/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.solace.samples.features.distributedtracing.manualinstrumentation;

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