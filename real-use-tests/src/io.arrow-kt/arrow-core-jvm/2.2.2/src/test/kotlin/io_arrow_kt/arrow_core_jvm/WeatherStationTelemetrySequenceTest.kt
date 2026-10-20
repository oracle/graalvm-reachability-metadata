/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.Ior
import arrow.core.NonEmptyList
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.align
import arrow.core.filterOption
import arrow.core.interleave
import arrow.core.leftPadZip
import arrow.core.mapOrAccumulate
import arrow.core.nonEmptyListOf
import arrow.core.padZip
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.rightPadZip
import arrow.core.salign
import arrow.core.separateEither
import arrow.core.split
import arrow.core.tail
import arrow.core.unalign
import arrow.core.unzip
import arrow.core.zip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Lazy `Sequence` combinators (`split`, `tail`, `align`, `salign`, `unalign`, `padZip`,
 * `leftPadZip`, `rightPadZip`, `interleave`, multi-way `zip`, `unzip`, `filterOption`,
 * `separateEither` and `mapOrAccumulate` on `Sequence`).
 *
 * A weather station gateway ingests telemetry as lazy frame streams. Some feeds never end
 * (polling schedules, the primary radio link), so the gateway relies on Arrow's `Sequence`
 * extensions pulling only as many frames as the downstream stage asks for. The header frame
 * is split off the stream, redundant radio links are aligned slot by slot, sensor channels are
 * zipped into samples, gaps are filled from the forecast, and bad frames are either separated
 * out or accumulated into one validation report.
 */
class WeatherStationTelemetrySequenceTest {

    data class Reading(val slot: Int, val celsius: Double)

    data class Sample(val slot: Int, val celsius: Double, val humidity: Int, val pressure: Int)

    sealed interface Rejection {
        data class Malformed(val raw: String) : Rejection

        data class OutOfRange(val slot: Int, val celsius: Double) : Rejection
    }

    private fun parse(raw: String): Either<Rejection, Reading> = either {
        val parts: List<String> = raw.split(':')
        ensure(parts.size == 2) { Rejection.Malformed(raw) }
        val slot: Int = ensureNotNull(parts[0].toIntOrNull()) { Rejection.Malformed(raw) }
        val celsius: Double = ensureNotNull(parts[1].toDoubleOrNull()) { Rejection.Malformed(raw) }
        ensure(celsius in -60.0..60.0) { Rejection.OutOfRange(slot, celsius) }
        Reading(slot, celsius)
    }

    private fun slots(): Sequence<Int> = generateSequence(1) { it + 1 }

    @Test
    fun headerFrameIsSplitOffAndBodyFramesAreSeparated() {
        val frames: Sequence<String> = sequenceOf(
            "STATION:ws-17",
            "1:12.5",
            "2:abc",
            "3:13.0",
            "4:99.0",
            "5:14.25",
        )

        val (body: Sequence<String>, header: String) = requireNotNull(frames.split())
        assertEquals("STATION:ws-17", header)
        assertEquals("2:abc", body.tail().first())

        val (rejected: List<Rejection>, accepted: List<Reading>) =
            body.map(::parse).separateEither()
        assertEquals(
            listOf(Rejection.Malformed("2:abc"), Rejection.OutOfRange(4, 99.0)),
            rejected,
        )
        assertEquals(
            listOf(Reading(1, 12.5), Reading(3, 13.0), Reading(5, 14.25)),
            accepted,
        )

        assertNull(emptySequence<String>().split())
    }

    @Test
    fun redundantRadioLinksAreAlignedWithoutDrainingTheEndlessPrimary() {
        var pulledFromPrimary: Int = 0
        val primary: Sequence<Reading> = slots()
            .map { slot: Int -> Reading(slot, 10.0 + slot) }
            .onEach { pulledFromPrimary++ }
        val backup: Sequence<Reading> = sequenceOf(Reading(1, 11.5), Reading(2, 11.0))

        val resolved: List<String> = primary
            .align(backup) { ior: Ior<Reading, Reading> ->
                ior.fold(
                    { p: Reading -> "${p.slot}:primary:${p.celsius}" },
                    { b: Reading -> "${b.slot}:backup:${b.celsius}" },
                    { p: Reading, b: Reading -> "${p.slot}:both:${(p.celsius + b.celsius) / 2}" },
                )
            }
            .take(4)
            .toList()

        assertEquals(
            listOf("1:both:11.25", "2:both:11.5", "3:primary:13.0", "4:primary:14.0"),
            resolved,
        )
        assertEquals(4, pulledFromPrimary)

        val lateBackup: Sequence<Reading> =
            sequenceOf(Reading(1, 11.5), Reading(2, 11.0), Reading(3, 12.5), Reading(4, 13.5))
        val (primaryOnly: Sequence<Reading>, backupOnly: Sequence<Reading>) =
            primary.take(2).align(lateBackup).unalign()
        assertEquals(listOf(1, 2), primaryOnly.map(Reading::slot).toList())
        assertEquals(listOf(1, 2, 3, 4), backupOnly.map(Reading::slot).toList())

        val morningRain: Sequence<Int> = sequenceOf(3, 0, 2)
        val eveningRain: Sequence<Int> = sequenceOf(1, 4, 0, 5)
        assertEquals(listOf(4, 4, 2, 5), morningRain.salign(eveningRain, Int::plus).toList())
    }

    @Test
    fun sensorChannelsAreZippedIntoSamplesAndUnzippedForCharts() {
        val temperatures: Sequence<Double> = sequenceOf(12.5, 13.0, 13.75, 14.0)
        val humidity: Sequence<Int> = sequenceOf(81, 79, 77)
        val pressure: Sequence<Int> = sequenceOf(1012, 1011, 1011, 1010)

        val samples: List<Sample> = slots()
            .zip(temperatures, humidity, pressure) { slot: Int, c: Double, h: Int, p: Int ->
                Sample(slot, c, h, p)
            }
            .toList()
        assertEquals(
            listOf(
                Sample(1, 12.5, 81, 1012),
                Sample(2, 13.0, 79, 1011),
                Sample(3, 13.75, 77, 1011),
            ),
            samples,
        )

        val (axis: Sequence<Int>, series: Sequence<Double>) =
            samples.asSequence().unzip { sample: Sample -> sample.slot to sample.celsius }
        assertEquals(listOf(1, 2, 3), axis.toList())
        assertEquals(listOf(12.5, 13.0, 13.75), series.toList())
    }

    @Test
    fun missingObservationsAreFilledFromTheForecast() {
        val forecast: Sequence<Double> = sequenceOf(12.0, 13.0, 14.0, 15.0, 16.0)
        val observed: Sequence<Double> = sequenceOf(12.5, 12.5, 14.5)

        val published: List<Double> = observed
            .leftPadZip(forecast) { obs: Double?, fc: Double -> obs ?: fc }
            .toList()
        assertEquals(listOf(12.5, 12.5, 14.5, 15.0, 16.0), published)

        val deviations: List<Double> = forecast
            .rightPadZip(observed) { fc: Double, obs: Double? -> (obs ?: fc) - fc }
            .toList()
        assertEquals(listOf(0.5, -0.5, 0.5, 0.0, 0.0), deviations)

        val coverage: List<Pair<Double?, Double?>> = observed.padZip(forecast.take(2)).toList()
        assertEquals(listOf(12.5 to 12.0, 12.5 to 13.0, 14.5 to null), coverage)
    }

    @Test
    fun endlessPollingScheduleAndBatteryFramesStayLazy() {
        val north: Sequence<String> = slots().map { "north-$it" }
        val south: Sequence<String> = slots().map { "south-$it" }
        assertEquals(
            listOf("north-1", "south-1", "north-2", "south-2", "north-3"),
            north.interleave(south).take(5).toList(),
        )

        val batteryStatus: Sequence<Option<Int>> = slots().map { frame: Int ->
            if (frame % 3 == 0) Some(100 - frame) else None
        }
        assertEquals(listOf(97, 94, 91), batteryStatus.filterOption().take(3).toList())
    }

    @Test
    fun calibrationBatchReportsEveryBadFrameAtOnce() {
        val badBatch: Sequence<String> = sequenceOf("1:12.5", "2:x", "3:70.0", "4:13.0")
        val badReport: Either<NonEmptyList<Rejection>, List<Reading>> =
            badBatch.mapOrAccumulate { raw: String -> parse(raw).bind() }
        assertEquals(
            Either.Left(
                nonEmptyListOf(Rejection.Malformed("2:x"), Rejection.OutOfRange(3, 70.0)),
            ),
            badReport,
        )

        val summary: Either<String, List<Double>> = badBatch.mapOrAccumulate(
            { first: String, second: String -> "$first; $second" },
        ) { raw: String ->
            parse(raw).mapLeft { rejection: Rejection -> rejection.toString() }.bind().celsius
        }
        assertEquals(
            Either.Left("Malformed(raw=2:x); OutOfRange(slot=3, celsius=70.0)"),
            summary,
        )

        val goodBatch: Sequence<String> = sequenceOf("1:12.5", "2:13.0")
        val goodReport: Either<NonEmptyList<Rejection>, List<Reading>> =
            goodBatch.mapOrAccumulate { raw: String -> parse(raw).bind() }
        assertEquals(Either.Right(listOf(Reading(1, 12.5), Reading(2, 13.0))), goodReport)
    }
}
