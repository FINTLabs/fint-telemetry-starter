package no.novari.telemetry

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import kotlin.test.assertEquals

/**
 * Every snippet here is transcribed verbatim into README.md. If a snippet in the README stops
 * matching a test here, the documentation is wrong - keep them in sync.
 */
class DocumentationExamplesTest {
    @Test
    fun `annotated span example works`() {
        val context =
            SpringApplication(AnnotatedExampleApplication::class.java).run(
                "--spring.main.web-application-type=none",
                "--management.tracing.sampling.probability=1.0",
            )
        val exporter = context.getBean<io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter>()

        context.getBean<AnnotatedExampleApplication.InstanceProcessor>().process("123")

        val spans = exporter.awaitSpans(1)
        context.close()

        assertEquals(1, spans.size)
        assertEquals("instance.process", spans.first().name)
    }

    @Test
    fun `annotated span without an explicit contextualName gets a class-and-method name`() {
        val context =
            SpringApplication(DefaultNameExampleApplication::class.java).run(
                "--spring.main.web-application-type=none",
                "--management.tracing.sampling.probability=1.0",
            )
        val exporter = context.getBean<io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter>()

        context.getBean<DefaultNameExampleApplication.InstanceProcessor>().process()

        val spans = exporter.awaitSpans(1)
        context.close()

        assertEquals(1, spans.size)
        assertEquals("instance-processor#process", spans.first().name)
    }

    @Test
    fun `programmatic span example works`() {
        val context =
            SpringApplication(ProgrammaticExampleApplication::class.java).run(
                "--spring.main.web-application-type=none",
                "--management.tracing.sampling.probability=1.0",
            )
        val exporter = context.getBean<io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter>()
        val registry = context.getBean<ObservationRegistry>()

        ProgrammaticInstanceProcessor(registry).process("123", "pdf")

        val spans = exporter.awaitSpans(1)
        context.close()

        assertEquals(1, spans.size)
        assertEquals("instance.process", spans.first().name)
    }

    @Import(SpanCaptureConfiguration::class)
    @SpringBootApplication
    class AnnotatedExampleApplication {
        @Component
        class InstanceProcessor {
            @Observed(name = "instance.process", contextualName = "instance.process")
            fun process(instanceId: String) {
            }
        }
    }

    @Import(SpanCaptureConfiguration::class)
    @SpringBootApplication
    class DefaultNameExampleApplication {
        @Component
        class InstanceProcessor {
            @Observed
            fun process() {
            }
        }
    }

    class ProgrammaticInstanceProcessor(
        private val observationRegistry: ObservationRegistry,
    ) {
        fun process(
            instanceId: String,
            instanceType: String,
        ) {
            Observation
                .createNotStarted("instance.process", observationRegistry)
                .lowCardinalityKeyValue("instance.type", instanceType)
                .highCardinalityKeyValue("instance.id", instanceId)
                .observe { doProcess(instanceId) }
        }

        private fun doProcess(instanceId: String) {
        }
    }

    @Import(SpanCaptureConfiguration::class)
    @SpringBootApplication
    class ProgrammaticExampleApplication
}
