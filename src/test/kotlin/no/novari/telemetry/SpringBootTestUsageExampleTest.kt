package no.novari.telemetry

import io.micrometer.observation.annotation.Observed
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import kotlin.test.assertEquals

/**
 * Verifies the exact @SpringBootTest-style snippet documented in README.md's Testing section,
 * including @AutoConfigureObservability - without it, tracing stays disabled in tests and this
 * would silently produce zero spans.
 */
@Import(SpanCaptureConfiguration::class)
@SpringBootTest(
    classes = [SpringBootTestUsageExampleTest.ExampleApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["management.tracing.sampling.probability=1.0"],
)
@AutoConfigureObservability
class SpringBootTestUsageExampleTest {
    @Test
    fun `produces a span`(
        @Autowired exporter: InMemorySpanExporter,
        @Autowired processor: ExampleApplication.InstanceProcessor,
    ) {
        processor.process("123")

        val spans = exporter.awaitSpans(1)
        assertEquals(1, spans.size)
    }

    @SpringBootApplication
    class ExampleApplication {
        @Component
        class InstanceProcessor {
            @Observed(name = "instance.process", contextualName = "instance.process")
            fun process(instanceId: String) {
            }
        }
    }
}
