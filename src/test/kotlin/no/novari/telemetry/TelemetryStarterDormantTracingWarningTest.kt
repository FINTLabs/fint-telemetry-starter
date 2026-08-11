package no.novari.telemetry

import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertTrue

/**
 * Proves that the deferred log message from TelemetryEnvironmentPostProcessor survives the full
 * Boot startup flow and reaches the fully configured logging system - not just that the post
 * processor calls log.warn(...) when invoked directly (covered by
 * TelemetryEnvironmentPostProcessorTest, which uses a non-deferred DeferredLogFactory).
 *
 * Captures System.out directly rather than pre-attaching a Logback ListAppender: Boot's own
 * LoggingSystem.initialize() resets the Logback context during startup and tears down any
 * appender attached beforehand, so a pre-attached ListAppender would miss the message even
 * though it was actually logged. System.out is what the user would actually see.
 */
class TelemetryStarterDormantTracingWarningTest {
    @Test
    fun `warns at startup when the OTLP endpoint is completely missing`() {
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))

        val context =
            try {
                SpringApplication(MinimalConsumerApplication::class.java).run(
                    "--spring.main.web-application-type=none",
                    "--spring.application.name=dormant-tracing-test",
                )
            } finally {
                System.setOut(originalOut)
            }

        context.use {
            val output = captured.toString(Charsets.UTF_8)
            assertTrue(
                output.contains("forsvinner sporløst"),
                "expected a warning about dormant tracing at startup without an OTLP endpoint",
            )
        }
    }

    @SpringBootApplication
    class MinimalConsumerApplication
}
