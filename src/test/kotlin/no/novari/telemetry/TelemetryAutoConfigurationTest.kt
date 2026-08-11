package no.novari.telemetry

import io.micrometer.observation.ObservationPredicate
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryAutoConfigurationTest {
    private val contextRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TelemetryAutoConfiguration::class.java))
            .withBean(ObservationRegistry::class.java, { ObservationRegistry.create() })

    private fun contextFor(path: String) =
        ServerRequestObservationContext(
            MockHttpServletRequest("GET", path),
            MockHttpServletResponse(),
        )

    @Test
    fun `excludes requests under the actuator base path`() {
        contextRunner.run { context ->
            val predicate = context.getBean<ObservationPredicate>()
            assertFalse(predicate.test("http.server.requests", contextFor("/actuator/health")))
        }
    }

    @Test
    fun `allows ordinary application paths through`() {
        contextRunner.run { context ->
            val predicate = context.getBean<ObservationPredicate>()
            assertTrue(predicate.test("http.server.requests", contextFor("/api/instanser")))
        }
    }

    @Test
    fun `respects a custom management endpoints web base-path`() {
        contextRunner
            .withPropertyValues("management.endpoints.web.base-path=/mgmt")
            .run { context ->
                val predicate = context.getBean<ObservationPredicate>()
                assertFalse(predicate.test("http.server.requests", contextFor("/mgmt/health")))
                assertTrue(predicate.test("http.server.requests", contextFor("/actuator/health")))
            }
    }

    @Test
    fun `can be disabled via novari telemetry actuator-exclusion enabled`() {
        contextRunner
            .withPropertyValues("novari.telemetry.actuator-exclusion.enabled=false")
            .run { context ->
                assertFalse(context.containsBean("actuatorObservationExclusionPredicate"))
            }
    }
}
