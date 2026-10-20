/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Generation of `.proto` schema text from `@Serializable` class descriptors with `ProtoBufSchemaGenerator`.
 *
 * An inventory service publishes the protobuf contract of its warehouse messages so that teams
 * in other languages can generate their own stubs. It derives the `.proto` file from its Kotlin
 * model, then checks that the published contract names the declared field numbers, integer
 * encodings, packed lists, maps, enums and optional fields the way `protoc` expects them.
 */
@OptIn(ExperimentalSerializationApi::class)
class InventorySchemaGenerationTest {

    @Serializable
    @SerialName("StockState")
    enum class StockState {
        @ProtoNumber(0)
        UNKNOWN,

        @ProtoNumber(1)
        IN_STOCK,

        @ProtoNumber(2)
        BACKORDERED,
    }

    @Serializable
    @SerialName("Location")
    data class Location(
        @ProtoNumber(1) val aisle: String,
        @ProtoNumber(2) val shelf: Int,
    )

    @Serializable
    @SerialName("StockItem")
    data class StockItem(
        @ProtoNumber(1) val sku: String,
        @ProtoNumber(2) @ProtoType(ProtoIntegerType.SIGNED) val delta: Int,
        @ProtoNumber(3) @ProtoType(ProtoIntegerType.FIXED) val checksum: Long,
        @ProtoNumber(4) @ProtoPacked val recentCounts: List<Int>,
        @ProtoNumber(5) val bins: Map<String, Location>,
        @ProtoNumber(6) val state: StockState,
        @ProtoNumber(7) val note: String? = null,
        @ProtoNumber(8) val location: Location,
    )

    @Serializable
    @SerialName("Warehouse")
    data class Warehouse(
        @ProtoNumber(1) val code: String,
        @ProtoNumber(2) val items: List<StockItem>,
    )

    private val descriptors: List<SerialDescriptor> =
        listOf(Warehouse.serializer().descriptor)

    private fun publishContract(): String =
        ProtoBufSchemaGenerator.generateSchemaText(
            descriptors,
            "com.example.inventory",
            mapOf("java_package" to "com.example.inventory.proto"),
        )

    /** Returns the body of the named message or enum, one trimmed line per field. */
    private fun block(schema: String, keyword: String, name: String): List<String> {
        val header = "$keyword $name {"
        val start: Int = schema.indexOf(header)
        assertTrue(start >= 0, "missing `$header` in:\n$schema")
        val end: Int = schema.indexOf("}", start)
        return schema.substring(start + header.length, end)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
    }

    @Test
    fun publishesHeaderPackageAndOptions() {
        val schema: String = publishContract()

        assertTrue(schema.contains("syntax = \"proto2\";"), schema)
        assertTrue(schema.contains("package com.example.inventory;"), schema)
        assertTrue(schema.contains("option java_package = \"com.example.inventory.proto\";"), schema)
        assertEquals(schema, publishContract())
    }

    @Test
    fun declaresEveryReachableMessageAndEnumOnce() {
        val schema: String = publishContract()

        for (header in listOf(
            "message Warehouse {",
            "message StockItem {",
            "message Location {",
            "enum StockState {",
        )) {
            assertEquals(1, schema.split(header).size - 1, "`$header` in:\n$schema")
        }
    }

    @Test
    fun mapsFieldEncodingsToProtoTypes() {
        val item: List<String> = block(publishContract(), "message", "StockItem")

        assertEquals(
            listOf(
                "required string sku = 1;",
                "required sint32 delta = 2;",
                "required fixed64 checksum = 3;",
                "repeated int32 recentCounts = 4 [packed=true];",
                "map<string, Location> bins = 5;",
                "required StockState state = 6;",
                "optional string note = 7;",
                "required Location location = 8;",
            ),
            item,
        )
    }

    @Test
    fun keepsEnumProtoNumbersAndNestedReferences() {
        val schema: String = publishContract()

        assertEquals(
            listOf("UNKNOWN = 0;", "IN_STOCK = 1;", "BACKORDERED = 2;"),
            block(schema, "enum", "StockState"),
        )
        assertEquals(
            listOf("required string code = 1;", "repeated StockItem items = 2;"),
            block(schema, "message", "Warehouse"),
        )
        assertFalse(block(schema, "message", "Location").any { it.startsWith("optional") })
    }
}
