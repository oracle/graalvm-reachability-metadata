/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Locale

/**
 * Exercises Kotlin companion-object JSON creators for warehouse location codes.
 * A shipment service accepts compact location strings through a validating domain factory
 * and emits canonical codes for the warehouse picking system.
 */
public class CompanionFactoryShipmentTest {

    public data class WarehouseLocation private constructor(val aisle: String, val bin: Int) {
        public companion object {
            @JvmStatic
            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            public fun fromCode(code: String): WarehouseLocation {
                val parts: List<String> = code.trim().split('-')
                require(parts.size == 2) { "A location must contain an aisle and bin" }
                val aisle: String = parts[0].uppercase(Locale.ROOT)
                val bin: Int = parts[1].toInt()
                require(aisle.matches(Regex("[A-Z]+"))) { "An aisle must contain letters" }
                require(bin in 1..9999) { "A bin must be between 1 and 9999" }
                return WarehouseLocation(aisle, bin)
            }
        }

        @JsonValue
        public fun toCode(): String = "$aisle-${bin.toString().padStart(4, '0')}"
    }

    public data class Shipment(
        val shipmentId: String,
        val origin: WarehouseLocation,
        val destination: WarehouseLocation,
    )

    @Test
    @Timeout(60)
    public fun acceptsLocationCodesAndPublishesCanonicalPickingInstructions(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val requestJson: String = """
            {
              "shipmentId": "shipment-204",
              "origin": "a-7",
              "destination": "bc-42"
            }
        """.trimIndent()

        val shipment: Shipment = mapper.readValue(requestJson)

        assertEquals("shipment-204", shipment.shipmentId)
        assertEquals(WarehouseLocation.fromCode("A-0007"), shipment.origin)
        assertEquals("A", shipment.origin.aisle)
        assertEquals(7, shipment.origin.bin)
        assertEquals("BC", shipment.destination.aisle)
        assertEquals(42, shipment.destination.bin)
        val pickingRoute: String = "${shipment.origin.toCode()} -> ${shipment.destination.toCode()}"
        assertEquals("A-0007 -> BC-0042", pickingRoute)

        val instructionJson: String = mapper.writeValueAsString(shipment)
        val instruction: JsonNode = mapper.readTree(instructionJson)
        assertEquals("shipment-204", instruction["shipmentId"].asText())
        assertEquals(mapper.readTree("\"A-0007\""), instruction["origin"])
        assertEquals(mapper.readTree("\"BC-0042\""), instruction["destination"])
        val restored: Shipment = mapper.readValue(instructionJson)
        assertEquals(shipment, restored)
    }
}
