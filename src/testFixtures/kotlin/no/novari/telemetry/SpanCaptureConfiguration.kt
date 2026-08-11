package no.novari.telemetry

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class SpanCaptureConfiguration {
    @Bean
    fun inMemorySpanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Bean
    fun inMemorySpanProcessor(exporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(exporter)
}
