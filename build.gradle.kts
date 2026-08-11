import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.springframework.boot.gradle.plugin.SpringBootPlugin

object Versions {
    const val KOTLIN = "2.4.0"
}

plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("java-test-fixtures")
    id("com.github.ben-manes.versions") version "0.54.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
}

group = "no.novari"
version = findProperty("version") ?: "1.0-SNAPSHOT"

extra["kotlin.version"] = Versions.KOTLIN

ktlint {
    version.set("1.8.0")
}

private val fintLabsRepo = uri("https://repo.fintlabs.no/releases")

repositories {
    mavenLocal()
    maven {
        url = fintLabsRepo
    }
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Eksponeres videre til konsumenten - løsningsanalyse §3.
    api("io.micrometer:micrometer-tracing-bridge-otel")
    api("io.opentelemetry:opentelemetry-exporter-otlp")
    api("org.springframework.boot:spring-boot-starter-aop")
    api("org.springframework.boot:spring-boot-starter-actuator")

    // Kun for typene brukt i actuator-ekskluderings-ObservationPredicate
    // (ServerRequestObservationContext / HttpServletRequest). Ikke tvunget på
    // ikke-web-konsumenter, siden biblioteket er teamuavhengig.
    compileOnly("org.springframework:spring-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // Kun for logback-json.xml. Konsumenten legger selv til runtimeOnly hvis
    // fragmentet tas i bruk - biblioteket skal ikke tvinge et loggformat på noen.
    compileOnly("net.logstash.logback:logstash-logback-encoder:9.0")

    testFixturesApi("io.opentelemetry:opentelemetry-sdk-trace")
    testFixturesApi("io.opentelemetry:opentelemetry-sdk-testing")
    testFixturesApi("org.springframework.boot:spring-boot-test")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
    testImplementation("net.logstash.logback:logstash-logback-encoder:9.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}

tasks.named<Jar>("jar") {
    enabled = true
}

java {
    withSourcesJar()
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

publishing {
    repositories {
        maven {
            url = fintLabsRepo
            credentials {
                username = System.getenv("REPOSILITE_USERNAME")
                password = System.getenv("REPOSILITE_PASSWORD")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return !isStable
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}
