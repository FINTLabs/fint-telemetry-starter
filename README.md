# fint-telemetry-starter

Delt, teamuavhengig Spring Boot-starter for OpenTelemetry-tracing i Novari-applikasjoner.

Biblioteket oversetter plattformens miljøvariabel til riktig Spring Boot-property, setter
fornuftige standardverdier for annotasjonsbaserte spans og sampling, ekskluderer
actuator-endepunkter fra sporing, og advarer ved oppstart dersom sporing er aktiv uten at noe
eksport-endepunkt er tilgjengelig. Utover dette gjøres ingenting — all faktisk instrumentering
kommer fra Spring Boot og Micrometer selv.

## Innhold

- [Avhengighet](#avhengighet)
- [Kom i gang](#kom-i-gang)
- [Konfigurasjon](#konfigurasjon)
- [Egendefinerte spans](#egendefinerte-spans)
- [Attributter og kardinalitet](#attributter-og-kardinalitet)
- [Kontekst og tråder](#kontekst-og-tråder)
- [Logging](#logging)
- [Testing](#testing)
- [Feilsøking](#feilsøking)
- [Bygging lokalt](#bygging-lokalt)
- [Publisering](#publisering)

## Avhengighet

```kotlin
dependencies {
    implementation("no.novari:telemetry-starter:<versjon>")
}
```

Publiseres til `https://repo.fintlabs.no/releases`. Krever JDK 25 eller nyere, Spring Boot 3.5.

## Kom i gang

Legg til avhengigheten. Så snart plattformen setter miljøvariabelen
`OTEL_EXPORTER_OTLP_ENDPOINT`, er sporing aktivt uten videre konfigurasjon.

`@Observed`-annotasjonen fungerer, sampling er satt opp, og actuator-endepunkter er allerede
ekskludert. Se [Egendefinerte spans](#egendefinerte-spans) for hvordan du legger på ditt eget span.

**Sett `spring.application.name`.** Dette er en standard Spring Boot-property, ikke noe biblioteket
setter for deg — men Spring Boots egen OTel-instrumentering bruker den til å navngi tjenesten
(`service.name`) på hvert spenn. Mangler den, blir alle spans fra appen merket `unknown_service` i
sporingsverktøyet, uten varsel av noe slag.

Mangler miljøvariabelen, kjører applikasjonen helt normalt, men ingen spans eksporteres noe sted
— se [Feilsøking](#feilsøking).

## Konfigurasjon

| Egenskap | Standardverdi | Hva den gjør |
|---|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` (miljøvariabel) | — | Base-URL for sporingsplattformen. Oversettes til `management.otlp.tracing.endpoint` med `/v1/traces` lagt til |
| `management.otlp.tracing.endpoint` | (satt fra miljøvariabelen over, hvis den ikke er satt eksplisitt) | Full URL sporingsdata eksporteres til. En eksplisitt verdi her overstyrer alltid miljøvariabelen |
| `management.tracing.enabled` | `true` | Sett til `false` for å bevisst skru av sporing. Fjerner advarselen beskrevet i [Feilsøking](#feilsøking) |
| `management.tracing.sampling.probability` | `1.0` | Andel av egne rot-spor som samples. Et spor som allerede har en samplingsbeslutning fra oppstrøms (`traceparent`) arver alltid den, uansett denne verdien |
| `management.observations.annotations.enabled` | `true` | Slår på `@Observed`. Spring Boot har ingen standardverdi for denne — uten den gjør annotasjonen ingenting |
| `novari.telemetry.org-id` | — | Legges til som ressursattributtet `org-id` på alle spans, via `management.opentelemetry.resource-attributes.org-id`. Kan også settes via miljøvariabelen `NOVARI_TELEMETRY_ORG_ID` |
| `novari.telemetry.actuator-exclusion.enabled` | `true` | Ekskluderer actuator-endepunkter (stien fra `management.endpoints.web.base-path`, standard `/actuator`) fra sporing |
| `management.observations.enable.spring.security` | `false` | Skrur av Spring Securitys egne filter-kjede-spenn (`spring.security.*`). Sett til `true` for å beholde dem — se [Feilsøking](#feilsøking) for hvorfor de er av som standard |

Ingen av standardverdiene overstyrer en verdi du allerede har satt selv.

## Egendefinerte spans

### Med annotasjon

```kotlin
@Component
class InstanceProcessor {
    @Observed(name = "instance.process", contextualName = "instance.process")
    fun process(instanceId: String) {
        // ...
    }
}
```

To felt på `@Observed`, med to forskjellige jobber:

- `name` — navnet på selve observasjonen, brukes blant annet som metrikknavn
- `contextualName` — navnet spannet faktisk får i sporingsverktøyet

Setter du kun `name`, får spannet likevel et navn — men det blir da satt automatisk til
`KlasseNavn#metodeNavn` (normalisert), ikke verdien du satte i `name`. For eksempel gir

```kotlin
@Observed
fun process() { /* ... */ }
```

på en klasse som heter `InstanceProcessor`, et spann som heter `instance-processor#process` i
sporingsverktøyet. Vil du styre spannavnet selv, sett `contextualName` eksplisitt.

### Programmatisk

Bruk dette når du vil måle en avgrenset del av koden, eller legge på attributter som ikke er
kjent før metoden kjører:

```kotlin
class InstanceProcessor(
    private val observationRegistry: ObservationRegistry,
) {
    fun process(instanceId: String, instanceType: String) {
        Observation
            .createNotStarted("instance.process", observationRegistry)
            .lowCardinalityKeyValue("instance.type", instanceType)
            .highCardinalityKeyValue("instance.id", instanceId)
            .observe { doProcess(instanceId) }
    }
}
```

`observe { }` håndterer start, stopp og feilregistrering for deg, og sørger for at spannet blir
riktig plassert i sporet — bruk denne formen framfor å kalle `start()`/`stop()` manuelt.

## Attributter og kardinalitet

To typer attributter, med ulik konsekvens for observability-plattformen:

- **`lowCardinalityKeyValue`** — verdier fra et lite, avgrenset sett (status, type, kategori).
  Brukes også i metrikk-labels.
- **`highCardinalityKeyValue`** — verdier som er unike eller nær-unike (ID-er, tidsstempler,
  frie tekstfelt). Vises kun på spannet, aldri i metrikker.

Legger du en høy-kardinalitet-verdi (som en instans-ID) i `lowCardinalityKeyValue`, blir den
likevel tatt med i metrikk-labels — det gir én tidsserie per unike verdi, som i praksis betyr
uendelig mange tidsserier. Se eksempelet over: `instance.type` er lav kardinalitet,
`instance.id` er høy.

## Kontekst og tråder

Sporingskontekst er bundet til tråden som mottok den — ikke til requesten eller applikasjonen
som helhet. Et par konsekvenser av dette:

- Sender du arbeid videre til en `Executor`, `CompletableFuture` eller en egen tråd, følger ikke
  konteksten automatisk med. Skal det underliggende arbeidet inngå i samme spor, må konteksten
  overføres eksplisitt til den nye tråden.
- Bruk alltid den selvlukkende `observe { }`-formen (eller `try`/`finally` med `openScope()` og
  `stop()`) framfor å håndtere scope manuelt. Tråder gjenbrukes av rammeverket (Tomcats
  arbeidertråder, Kafka-lytterens tråder), så et scope som ikke lukkes kan bli liggende igjen og
  smitte over på neste, urelaterte stykke arbeid som kjøres på samme tråd.

## Logging

Trace-ID og span-ID er tilgjengelig i loggkonteksten så snart sporing er aktivt, uavhengig av
loggformat — dette skjer automatisk og krever ingen konfigurasjon fra biblioteket.

Vil du ha dem som egne, søkbare JSON-felt (anbefalt, framfor å hente dem ut med tekstsøk), tilbyr
biblioteket et inkluderbart Logback-fragment. Legg til encoderen selv:

```kotlin
runtimeOnly("net.logstash.logback:logstash-logback-encoder:9.0")
```

og inkluder fragmentet i din egen `logback-spring.xml`:

```xml
<configuration>
    <include resource="no/novari/telemetry/logback-json.xml"/>
    <root level="INFO">
        <appender-ref ref="TELEMETRY_JSON"/>
    </root>
</configuration>
```

Loggutdata får da `traceId` og `spanId` som egne, toppnivå JSON-felt (ikke nøstet under et
`mdc`-objekt), sammen med tidsstempel, nivå, logger, melding og eventuell stack trace.

Fragmentet er av som standard — biblioteket tvinger ikke et loggformat på noen. Foretrekker du
Boots standard tekstlogg, lar du rett og slett være å inkludere det.

## Testing

`testFixtures`-artefaktet gir deg det du trenger for å verifisere at et span faktisk ble
produsert, i din egen integrasjonstest:

```kotlin
testImplementation(testFixtures("no.novari:telemetry-starter:<versjon>"))
```

```kotlin
@Import(SpanCaptureConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureObservability
class InstanceProcessorTest {
    @Test
    fun `produces a span`(@Autowired exporter: InMemorySpanExporter, @Autowired processor: InstanceProcessor) {
        processor.process("123")

        val spans = exporter.awaitSpans(1)
        assertEquals(1, spans.size)
    }
}
```

`SpanCaptureConfiguration` registrerer en `InMemorySpanExporter` som fanger opp spans i minnet.
`awaitSpans(count, timeout)` venter til forventet antall spans er eksportert (spans eksporteres
asynkront), og returnerer dem.

**`@AutoConfigureObservability` er påkrevd.** Spring Boot setter `management.tracing.enabled=false`
i `@SpringBootTest`-kontekst med mindre testklassen annoteres med denne. Mangler den, blir ingen
spans produsert i det hele tatt — testen over feiler stille med `spans.size == 0`, og feilen kan
lett mistolkes som at propagering eller instrumentering ikke virker.

## Feilsøking

Fem ting som feiler stille, uten unntak eller kompileringsfeil:

**Tjenesten heter `unknown_service` i sporingsverktøyet.** `spring.application.name` er ikke satt.
Dette er en standard Spring Boot-property som biblioteket ikke setter for deg (i motsetning til
`novari.telemetry.org-id`, som blir en ressursattributt via biblioteket) — Spring Boots egen
OTel-instrumentering faller tilbake til literal-strengen `"unknown_service"` når den mangler. Spans
produseres og eksporteres helt normalt, bare uten et brukbart tjenestenavn.

**Manglende eksport-endepunkt.** Er verken `OTEL_EXPORTER_OTLP_ENDPOINT` eller
`management.otlp.tracing.endpoint` satt, genereres spans som normalt, men blir aldri eksportert
noe sted — de forsvinner sporløst. Biblioteket logger en WARN ved oppstart i dette tilfellet; se
etter en logglinje fra `TelemetryEnvironmentPostProcessor` som starter med
`fint-telemetry-starter: sporing er aktiv i classpath`. Er dette bevisst, sett
`management.tracing.enabled=false` for å fjerne advarselen.

**`@Observed` uten forventet spannavn.** Se [Egendefinerte spans](#egendefinerte-spans) — uten
en eksplisitt `contextualName` blir spannavnet satt automatisk til `klassenavn#metodenavn`
(normalisert til små bokstaver med bindestrek), ikke verdien i `name`.

**Mange tomme `security filterchain`-spor, ett per helsesjekk/scraping.** Spring Security lager
egne spenn (`spring.security.filterchains`, `spring.security.http.secured.requests` m.fl.) for
hver request som går gjennom sikkerhetsfilterkjeden — uavhengig av `http.server.requests` og
uten sti-informasjon i konteksten, så actuator-ekskluderingen over kan aldri filtrere dem bort.
Siden actuator-kallet selv ekskluderes, men ikke Securitys eget spenn, blir det stående igjen som
et eget, tomt rot-spor. Biblioteket skrur derfor av disse spennene som standard
(`management.observations.enable.spring.security=false`) — sett den til `true` selv om dere vil
beholde dem.

**Ingen spans i tester.** Se [Testing](#testing) — mangler `@AutoConfigureObservability` på
testklassen, produseres ingen spans, siden Spring Boot slår av sporing i testkontekst som
standard.

## Bygging lokalt

```bash
./gradlew check
```

Kjører kompilering, `ktlint` og hele testsuiten.

## Publisering

Publiseres til Reposilite (`https://repo.fintlabs.no/releases`) ved GitHub-release, via en
gjenbrukbar workflow fra `FINTLabs/fint-flyt-github-workflows`. Se `.github/workflows/publish.yaml`.
