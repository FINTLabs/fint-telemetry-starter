package no.novari.telemetry

import io.micrometer.observation.annotation.Observed
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves the FFS-2218 DoD: a minimal consumer that only adds the dependency - no configuration
 * or instrumentation code of its own - gets correctly configured, working tracing.
 */
class TelemetryStarterProdLikeIntegrationTest {
    private lateinit var mockOtlpCollector: MockWebServer

    @BeforeEach
    fun startCollector() {
        mockOtlpCollector = MockWebServer()
        mockOtlpCollector.enqueue(MockResponse().setResponseCode(200))
        mockOtlpCollector.start()
    }

    @AfterEach
    fun stopCollector() {
        mockOtlpCollector.shutdown()
    }

    @Test
    fun `consumer that only adds the dependency gets working tracing without any code`() {
        val context =
            SpringApplication(ConsumerApplication::class.java).run(
                "--spring.main.web-application-type=none",
                "--spring.application.name=telemetry-smoke-test",
                "--OTEL_EXPORTER_OTLP_ENDPOINT=${mockOtlpCollector.url("/")}",
                "--management.tracing.opentelemetry.export.schedule-delay=100ms",
            )

        context.getBean(ConsumerApplication.Greeter::class.java).greet()

        val recorded = mockOtlpCollector.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recorded, "expected a span to actually be exported to the collector")
        assertEquals("/v1/traces", recorded.path)

        context.close()
    }

    @SpringBootApplication
    class ConsumerApplication {
        @Component
        class Greeter {
            @Observed(name = "greeter.greet")
            fun greet(): String = "hello"
        }
    }
}
