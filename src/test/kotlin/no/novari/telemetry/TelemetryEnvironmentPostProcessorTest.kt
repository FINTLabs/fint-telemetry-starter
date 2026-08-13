package no.novari.telemetry

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import org.springframework.boot.logging.DeferredLogFactory
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryEnvironmentPostProcessorTest {
    private val logFactory = DeferredLogFactory { supplier -> supplier.get() }
    private val postProcessor = TelemetryEnvironmentPostProcessor(logFactory)
    private var attachedAppender: ListAppender<ILoggingEvent>? = null

    @AfterEach
    fun detachAppender() {
        attachedAppender?.let { appender ->
            (LoggerFactory.getLogger(TelemetryEnvironmentPostProcessor::class.java) as Logger)
                .detachAppender(appender)
        }
    }

    private fun attachListAppender(): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(TelemetryEnvironmentPostProcessor::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        attachedAppender = appender
        return appender
    }

    private fun environmentWith(vararg entries: Pair<String, String>): StandardEnvironment {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(MapPropertySource("test", mapOf(*entries)))
        return environment
    }

    @Test
    fun `translates OTEL_EXPORTER_OTLP_ENDPOINT to management otlp tracing endpoint with v1-traces appended`() {
        val environment = environmentWith("OTEL_EXPORTER_OTLP_ENDPOINT" to "http://collector:4318")
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals(
            "http://collector:4318/v1/traces",
            environment.getProperty("management.otlp.tracing.endpoint"),
        )
    }

    @Test
    fun `trims trailing slash before appending v1-traces`() {
        val environment = environmentWith("OTEL_EXPORTER_OTLP_ENDPOINT" to "http://collector:4318/")
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals(
            "http://collector:4318/v1/traces",
            environment.getProperty("management.otlp.tracing.endpoint"),
        )
    }

    @Test
    fun `does not override an explicitly set management otlp tracing endpoint`() {
        val environment =
            environmentWith(
                "OTEL_EXPORTER_OTLP_ENDPOINT" to "http://platform-collector:4318",
                "management.otlp.tracing.endpoint" to "http://custom:4318",
            )
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals("http://custom:4318", environment.getProperty("management.otlp.tracing.endpoint"))
    }

    @Test
    fun `sets default annotations-enabled and sampling-probability when not already set`() {
        val environment = StandardEnvironment()
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals("true", environment.getProperty("management.observations.annotations.enabled"))
        assertEquals("1.0", environment.getProperty("management.tracing.sampling.probability"))
    }

    @Test
    fun `does not override an explicitly set sampling-probability`() {
        val environment = environmentWith("management.tracing.sampling.probability" to "0.25")
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals("0.25", environment.getProperty("management.tracing.sampling.probability"))
    }

    @Test
    fun `disables spring security observations by default`() {
        val environment = StandardEnvironment()
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals("false", environment.getProperty("management.observations.enable.spring.security"))
    }

    @Test
    fun `does not override an explicitly enabled spring security observations setting`() {
        val environment = environmentWith("management.observations.enable.spring.security" to "true")
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals("true", environment.getProperty("management.observations.enable.spring.security"))
    }

    @Test
    fun `translates novari telemetry org-id to opentelemetry resource attribute`() {
        val environment = environmentWith("novari.telemetry.org-id" to "afk.no")
        postProcessor.postProcessEnvironment(environment, mock())
        assertEquals("afk.no", environment.getProperty("management.opentelemetry.resource-attributes.org-id"))
    }

    @Test
    fun `does not set org-id resource attribute when org-id is not configured`() {
        val environment = StandardEnvironment()
        postProcessor.postProcessEnvironment(environment, mock())
        assertNull(environment.getProperty("management.opentelemetry.resource-attributes.org-id"))
    }

    @Test
    fun `logs a warning when the endpoint is missing and tracing is not explicitly disabled`() {
        val appender = attachListAppender()
        postProcessor.postProcessEnvironment(StandardEnvironment(), mock())
        assertTrue(
            appender.list.any { event ->
                event.level == Level.WARN && event.formattedMessage.contains("forsvinner sporløst")
            },
            "expected a warning about dormant tracing, got: ${appender.list.map { it.formattedMessage }}",
        )
    }

    @Test
    fun `does not log a warning when the endpoint is set`() {
        val appender = attachListAppender()
        postProcessor.postProcessEnvironment(
            environmentWith("OTEL_EXPORTER_OTLP_ENDPOINT" to "http://collector:4318"),
            mock(),
        )
        assertTrue(appender.list.none { it.level == Level.WARN })
    }

    @Test
    fun `does not log a warning when management tracing enabled is set to false`() {
        val appender = attachListAppender()
        postProcessor.postProcessEnvironment(
            environmentWith("management.tracing.enabled" to "false"),
            mock(),
        )
        assertTrue(appender.list.none { it.level == Level.WARN })
    }
}
