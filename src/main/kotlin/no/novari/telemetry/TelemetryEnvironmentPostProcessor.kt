package no.novari.telemetry

import org.apache.commons.logging.Log
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.boot.logging.DeferredLogFactory
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.getProperty

class TelemetryEnvironmentPostProcessor(
    logFactory: DeferredLogFactory,
) : EnvironmentPostProcessor,
    Ordered {
    private val log: Log = logFactory.getLog(TelemetryEnvironmentPostProcessor::class.java)

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val otelEndpointEnv = environment.getProperty(OTEL_EXPORTER_OTLP_ENDPOINT_ENV)
        val existingTracingEndpoint = environment.getProperty(MGMT_OTLP_TRACING_ENDPOINT)

        val defaults = linkedMapOf<String, Any>()

        if (existingTracingEndpoint == null && !otelEndpointEnv.isNullOrBlank()) {
            defaults[MGMT_OTLP_TRACING_ENDPOINT] = toTracingEndpoint(otelEndpointEnv)
        }
        if (environment.getProperty(MGMT_OBSERVATIONS_ANNOTATIONS_ENABLED) == null) {
            defaults[MGMT_OBSERVATIONS_ANNOTATIONS_ENABLED] = "true"
        }
        if (environment.getProperty(MGMT_TRACING_SAMPLING_PROBABILITY) == null) {
            defaults[MGMT_TRACING_SAMPLING_PROBABILITY] = "1.0"
        }
        // Spring Securitys egen ObservationFilterChainDecorator lager spenn (spring.security.*)
        // som ikke er nøstet under http.server.requests og ikke bærer HttpServletRequest i sin
        // egen Observation.Context - vår actuator-ekskludering kan derfor aldri filtrere dem bort
        // per sti. De blir stående som egne, tomme rot-spor for hver helsesjekk/scraping. Av som
        // standard; sett denne til true selv for å beholde dem.
        if (environment.getProperty(MGMT_OBSERVATIONS_ENABLE_SPRING_SECURITY) == null) {
            defaults[MGMT_OBSERVATIONS_ENABLE_SPRING_SECURITY] = "false"
        }

        val orgId = environment.getProperty(NOVARI_TELEMETRY_ORG_ID)
        if (!orgId.isNullOrBlank() && environment.getProperty(MGMT_OTEL_RESOURCE_ATTR_ORG_ID) == null) {
            defaults[MGMT_OTEL_RESOURCE_ATTR_ORG_ID] = orgId
        }

        if (defaults.isNotEmpty()) {
            environment.propertySources.addLast(MapPropertySource(PROPERTY_SOURCE_NAME, defaults))
        }

        warnIfTracingIsDormant(environment, existingTracingEndpoint ?: otelEndpointEnv)
    }

    // Unlike the OTel convention, where OTEL_EXPORTER_OTLP_ENDPOINT is a base URL that gets
    // /v1/traces appended automatically, Boot uses management.otlp.tracing.endpoint verbatim.
    private fun toTracingEndpoint(baseUrl: String): String = baseUrl.trimEnd('/') + "/v1/traces"

    private fun warnIfTracingIsDormant(
        environment: ConfigurableEnvironment,
        effectiveEndpoint: String?,
    ) {
        val tracingExplicitlyDisabled = !environment.getProperty<Boolean>(MGMT_TRACING_ENABLED, true)
        if (tracingExplicitlyDisabled || !effectiveEndpoint.isNullOrBlank()) {
            return
        }
        log.warn(
            "fint-telemetry-starter: sporing er aktiv i classpath (micrometer-tracing-bridge-otel), " +
                "men verken miljøvariabelen $OTEL_EXPORTER_OTLP_ENDPOINT_ENV eller propertyen " +
                "$MGMT_OTLP_TRACING_ENDPOINT er satt. Spans blir generert, men IKKE eksportert noe " +
                "sted - de forsvinner sporløst. Sett $OTEL_EXPORTER_OTLP_ENDPOINT_ENV (settes normalt " +
                "av plattformen) eller $MGMT_OTLP_TRACING_ENDPOINT eksplisitt for å aktivere eksport, " +
                "eller sett $MGMT_TRACING_ENABLED=false hvis sporing bevisst skal være av.",
        )
    }

    override fun getOrder(): Int = ConfigDataEnvironmentPostProcessor.ORDER + 1

    private companion object {
        const val OTEL_EXPORTER_OTLP_ENDPOINT_ENV = "OTEL_EXPORTER_OTLP_ENDPOINT"
        const val MGMT_OTLP_TRACING_ENDPOINT = "management.otlp.tracing.endpoint"
        const val MGMT_OBSERVATIONS_ANNOTATIONS_ENABLED = "management.observations.annotations.enabled"
        const val MGMT_TRACING_SAMPLING_PROBABILITY = "management.tracing.sampling.probability"
        const val MGMT_OBSERVATIONS_ENABLE_SPRING_SECURITY = "management.observations.enable.spring.security"
        const val MGMT_TRACING_ENABLED = "management.tracing.enabled"
        const val NOVARI_TELEMETRY_ORG_ID = "novari.telemetry.org-id"
        const val MGMT_OTEL_RESOURCE_ATTR_ORG_ID = "management.opentelemetry.resource-attributes.org-id"
        const val PROPERTY_SOURCE_NAME = "fintTelemetryDefaults"
    }
}
