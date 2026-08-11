package no.novari.telemetry

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("novari.telemetry")
class TelemetryProperties {
    var orgId: String? = null

    val actuatorExclusion = ActuatorExclusion()

    class ActuatorExclusion {
        var enabled: Boolean = true
    }
}
