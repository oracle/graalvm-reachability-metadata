/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises hex-string transport of `Cbor` documents through `encodeToHexString` and
 * `decodeFromHexString` for models built from `@JvmInline` value classes.
 *
 * An airline prints each boarding pass as a hex token in a barcode and in a check-in URL.
 * The pass is modelled with value-class wrappers for flight numbers, seats and fares, so
 * the token must carry only the wrapped values, and the gate scanner must decode the
 * printed token, accept upper-case tokens typed in by staff, and reject damaged ones.
 */
@OptIn(ExperimentalSerializationApi::class)
public class BoardingPassHexTokenCborTest {

    @Serializable
    @JvmInline
    public value class FlightNumber(val code: String)

    @Serializable
    @JvmInline
    public value class Seat(val label: String)

    @Serializable
    @JvmInline
    public value class FareCents(val amount: UInt)

    @Serializable
    @JvmInline
    public value class PassengerId(val value: Long)

    @Serializable
    public enum class Cabin { ECONOMY, BUSINESS }

    @Serializable
    public data class Gate(val number: Int)

    @Serializable
    public data class BoardingPass(
        val passenger: PassengerId,
        val flight: FlightNumber,
        val seat: Seat?,
        val cabin: Cabin,
        val fare: FareCents,
        val connections: List<FlightNumber> = emptyList(),
        val baggageBySeat: Map<Seat, Int> = emptyMap(),
    )

    /** The scanner's view of a pass: only the fields the gate needs. */
    @Serializable
    public data class GateCheck(val flight: FlightNumber, val seat: Seat?)

    private val issuer: Cbor = Cbor { useDefiniteLengthEncoding = true }

    private val scanner: Cbor = Cbor { ignoreUnknownKeys = true }

    @Test
    fun printsAndScansBoardingPassToken(): Unit {
        val pass: BoardingPass = BoardingPass(
            passenger = PassengerId(9_000_000_123L),
            flight = FlightNumber("JU540"),
            seat = Seat("14C"),
            cabin = Cabin.BUSINESS,
            fare = FareCents(4_000_000_000u),
            connections = listOf(FlightNumber("JU112"), FlightNumber("LH1411")),
            baggageBySeat = mapOf(Seat("14C") to 2),
        )

        val token: String = issuer.encodeToHexString(pass)
        assertTrue(token.all { c: Char -> c in '0'..'9' || c in 'a'..'f' }, token)
        assertEquals(0, token.length % 2)

        val reissued: BoardingPass = issuer.decodeFromHexString<BoardingPass>(token)
        assertEquals(pass, reissued)
        assertEquals(4_000_000_000u, reissued.fare.amount)
        assertEquals("LH1411", reissued.connections.last().code)
        assertEquals(2, reissued.baggageBySeat.getValue(Seat("14C")))

        val check: GateCheck = scanner.decodeFromHexString<GateCheck>(token)
        assertEquals(GateCheck(FlightNumber("JU540"), Seat("14C")), check)
    }

    @Test
    fun valueClassesEncodeAsTheirWrappedValue(): Unit {
        val token: String = issuer.encodeToHexString(GateCheck(FlightNumber("A1"), Seat("2B")))

        // {"flight": "A1", "seat": "2B"}: the wrappers add no extra CBOR structure.
        assertEquals("a266666c696768746241316473656174623242", token)

        // A standby pass has no seat; the absent wrapper is written as an empty CBOR map.
        val standby: String = issuer.encodeToHexString(GateCheck(FlightNumber("A1"), null))
        assertEquals("a266666c696768746241316473656174a0", standby)
        assertNull(issuer.decodeFromHexString<GateCheck>(standby).seat)
    }

    @Test
    fun acceptsUpperCaseTokensKeyedInByStaff(): Unit {
        val gate: Gate = Gate(23)
        val token: String = issuer.encodeToHexString(gate)
        assertEquals("a1666e756d62657217", token)

        val typed: String = token.uppercase()
        assertEquals(gate, issuer.decodeFromHexString<Gate>(typed))
        assertEquals(gate, Cbor.decodeFromHexString<Gate>("BF666E756D62657217FF"))
    }

    @Test
    fun rejectsDamagedTokens(): Unit {
        val token: String = issuer.encodeToHexString(Gate(23))

        assertThrows(IllegalArgumentException::class.java) {
            issuer.decodeFromHexString<Gate>(token.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            issuer.decodeFromHexString<Gate>(token.replace('6', 'g'))
        }
        // A valid token for another document type is refused rather than misread.
        val passToken: String = issuer.encodeToHexString(GateCheck(FlightNumber("A1"), Seat("2B")))
        assertThrows(SerializationException::class.java) {
            issuer.decodeFromHexString<Gate>(passToken)
        }
    }
}
