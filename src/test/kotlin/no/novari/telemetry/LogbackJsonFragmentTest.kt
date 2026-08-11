package no.novari.telemetry

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

/**
 * Proves that an application including no/novari/telemetry/logback-json.xml gets trace-ID and
 * span-ID as JSON fields in its log output. Uses an isolated LoggerContext rather than Boot's
 * own logging system, so the narrow System.out swap around a single log statement cannot
 * interfere with other tests in this module.
 */
class LogbackJsonFragmentTest {
    @Test
    fun `application including the fragment gets trace-id and span-id as JSON fields`() {
        val context =
            SpringApplication(MinimalApp::class.java).run(
                "--spring.main.web-application-type=none",
                "--management.tracing.sampling.probability=1.0",
            )

        val observationRegistry = context.getBean(ObservationRegistry::class.java)
        val observation = Observation.createNotStarted("fragment-test-span", observationRegistry).start()

        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        val isolatedContext = LoggerContext()
        try {
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))

            // A freshly constructed LoggerContext has no MDCAdapter of its own - MDC state lives
            // in the single adapter bound to the real, Boot-bootstrapped context, so the isolated
            // context must reuse that same instance to see anything Micrometer puts into MDC.
            val realContext = LoggerFactory.getILoggerFactory() as LoggerContext
            isolatedContext.setMDCAdapter(realContext.mdcAdapter)

            // ConsoleAppender resolves System.out when it starts (during doConfigure), not per
            // write - the swap above must happen before configuring, not after.
            val configurator = JoranConfigurator()
            configurator.context = isolatedContext
            javaClass.getResourceAsStream("/logback-json-fragment-test.xml").use { input ->
                configurator.doConfigure(input)
            }

            observation.openScope().use {
                isolatedContext.getLogger("fragment-test").info("hello from fragment test")
            }
        } finally {
            System.setOut(originalOut)
            observation.stop()
            isolatedContext.stop()
            context.close()
        }

        val json = ObjectMapper().readTree(captured.toString(Charsets.UTF_8).trim())

        assertEquals("hello from fragment test", json["message"].asText())
        assertEquals(true, json.has("traceId"))
        assertEquals(true, json.has("spanId"))
    }

    @SpringBootApplication
    class MinimalApp
}
