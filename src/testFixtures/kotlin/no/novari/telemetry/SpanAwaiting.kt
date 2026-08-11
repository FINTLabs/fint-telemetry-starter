package no.novari.telemetry

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import java.time.Duration

fun InMemorySpanExporter.awaitSpans(
    count: Int,
    timeout: Duration = Duration.ofSeconds(5),
): List<SpanData> {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (finishedSpanItems.size < count && System.nanoTime() < deadline) {
        Thread.sleep(50)
    }
    return finishedSpanItems.toList()
}
