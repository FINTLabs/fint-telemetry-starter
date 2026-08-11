package no.novari.telemetry

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals

/**
 * Proves that SpanCaptureConfiguration and awaitSpans, as published in testFixtures, are enough
 * for a consuming application to write an integration test that verifies a span was produced.
 */
class SpanCaptureFixturesTest {
    @Test
    fun `records a span produced via an active observation`() {
        val context =
            SpringApplication(ConsumerApplication::class.java).run(
                "--spring.main.web-application-type=none",
                "--management.tracing.sampling.probability=1.0",
            )

        val registry = context.getBean(ObservationRegistry::class.java)
        val exporter = context.getBean(InMemorySpanExporter::class.java)

        val observation = Observation.createNotStarted("demo-span", registry).start()
        observation.openScope().use { }
        observation.stop()

        val spans = exporter.awaitSpans(1)

        context.close()

        assertEquals(1, spans.size)
        assertEquals("demo-span", spans.first().name)
    }

    @Import(SpanCaptureConfiguration::class)
    @SpringBootApplication
    class ConsumerApplication
}
