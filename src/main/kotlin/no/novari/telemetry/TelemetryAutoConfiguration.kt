package no.novari.telemetry

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationPredicate
import io.micrometer.observation.ObservationRegistry
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.http.server.observation.ServerRequestObservationContext

@AutoConfiguration
@ConditionalOnClass(ObservationRegistry::class)
@EnableConfigurationProperties(TelemetryProperties::class)
class TelemetryAutoConfiguration {
    // management.observations.enable.* filters by observation name, not path, so it would
    // disable all HTTP tracing rather than just actuator's - hence a dedicated predicate.
    @Bean
    @ConditionalOnWebApplication(type = Type.SERVLET)
    @ConditionalOnClass(HttpServletRequest::class)
    @ConditionalOnBooleanProperty(name = ["novari.telemetry.actuator-exclusion.enabled"], matchIfMissing = true)
    @ConditionalOnMissingBean(name = ["actuatorObservationExclusionPredicate"])
    fun actuatorObservationExclusionPredicate(environment: Environment): ObservationPredicate {
        val basePath = environment.getProperty("management.endpoints.web.base-path", "/actuator")
        return ObservationPredicate { _, context -> !isActuatorRequest(context, basePath) }
    }

    private fun isActuatorRequest(
        context: Observation.Context,
        basePath: String,
    ): Boolean {
        val request = (context as? ServerRequestObservationContext)?.carrier
        // requestURI includes the servlet context path, while base-path is defined relative to
        // it - comparing against requestURI directly fails to exclude anything once
        // server.servlet.context-path is non-empty. servletPath is already context-relative.
        return request != null && request.servletPath.startsWith(basePath)
    }
}
