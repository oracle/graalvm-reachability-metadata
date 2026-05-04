/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_akuleshov7.ktoml_core_jvm

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlIndentation
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.TableType
import com.akuleshov7.ktoml.tree.nodes.TomlArrayOfTablesElement
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValueArray
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.TomlTable
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlDateTime
import com.akuleshov7.ktoml.writers.TomlWriter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Ktoml_core_jvmTest {
    @Test
    fun parsesTomlTreeWithCommentsNestedTablesAndArrays(): Unit {
        val input: String = """
            # document comment
            title = "TOML Example" # title comment

            [owner]
            name = "Tom Preston-Werner"

            [database]
            enabled = true
            ports = [8000, 8001, 8002]
        """.trimIndent()

        val tree = TomlParser(TomlInputConfig.compliant()).parseString(input)

        assertThat(tree.name).isEqualTo("rootNode")
        assertThat(tree.children.map { it.name }).containsExactly("title", "owner", "database")

        val title = tree.children[0] as TomlKeyValuePrimitive
        assertThat(title.key.toString()).isEqualTo("title")
        assertThat(title.value.content).isEqualTo("TOML Example")
        assertThat(title.comments).containsExactly("document comment")
        assertThat(title.inlineComment).isEqualTo("title comment")

        val owner = tree.findTableInAstByName("owner") as TomlTable
        assertThat(owner.children.map { it.name }).containsExactly("name")
        assertThat((owner.children.single() as TomlKeyValuePrimitive).value.content).isEqualTo("Tom Preston-Werner")

        val database = tree.findTableInAstByName("database") as TomlTable
        val ports = database.children.single { it.name == "ports" } as TomlKeyValueArray
        assertThat(ports.value.content as List<*>).hasSize(3)
    }

    @Test
    fun writesParsedTreeBackToTomlUsingOutputConfiguration(): Unit {
        val input: String = """
            title = "Writer Example"
            [database]
            enabled = true
            ports = [8000, 8001]
        """.trimIndent()
        val parser = TomlParser(TomlInputConfig.compliant())
        val writer = TomlWriter(
            TomlOutputConfig.compliant(
                indentation = TomlIndentation.TWO_SPACES,
                explicitTables = true,
            )
        )

        val emitted: String = writer.writeToString(parser.parseString(input))

        assertThat(emitted).contains("title = \"Writer Example\"")
        assertThat(emitted).contains("[database]")
        assertThat(emitted).contains("enabled = true")
        assertThat(emitted).contains("ports = [ 8000, 8001 ]")
    }

    @Test
    fun decodesPrimitiveMapsNestedMapsListsAndPartialTables(): Unit {
        val toml = Toml.Default
        val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
        val nestedLongMapSerializer = MapSerializer(
            String.serializer(),
            MapSerializer(String.serializer(), Long.serializer()),
        )

        val metadata: Map<String, String> = toml.decodeFromString(
            stringMapSerializer,
            """
                title = "Decoder Example"
                owner = "Infrastructure"
            """.trimIndent(),
        )
        val ports: List<Long> = toml.decodeFromString(
            ListSerializer(Long.serializer()),
            "ports = [8000, 8001, 8002]",
        )
        val limits: Map<String, Map<String, Long>> = toml.decodeFromString(
            nestedLongMapSerializer,
            """
                [limits]
                min = 1
                max = 10
            """.trimIndent(),
        )
        val primaryDatabaseHost: String = toml.partiallyDecodeFromString(
            String.serializer(),
            """
                [database.primary]
                host = "db1.example.test"

                [database.replica]
                host = "db2.example.test"
            """.trimIndent(),
            "database.primary",
        )

        assertThat(metadata).containsEntry("title", "Decoder Example")
        assertThat(ports).containsExactly(8000L, 8001L, 8002L)
        assertThat(limits.getValue("limits")).containsEntry("min", 1L).containsEntry("max", 10L)
        assertThat(primaryDatabaseHost).isEqualTo("db1.example.test")
    }

    @Test
    fun decodesFromLineSequencesAndEncodesMapsWithArraysAndNestedTables(): Unit {
        val toml = Toml(
            inputConfig = TomlInputConfig.compliant(ignoreUnknownNames = true),
            outputConfig = TomlOutputConfig.compliant(indentation = TomlIndentation.TAB),
        )
        val longMapSerializer = MapSerializer(String.serializer(), Long.serializer())
        val longListMapSerializer = MapSerializer(String.serializer(), ListSerializer(Long.serializer()))
        val nestedStringMapSerializer = MapSerializer(
            String.serializer(),
            MapSerializer(String.serializer(), String.serializer()),
        )

        val decoded: Map<String, Long> = toml.decodeFromString(
            longMapSerializer,
            sequenceOf("timeout = 30", "retries = 3"),
        )
        val encodedArrays: String = toml.encodeToString(
            longListMapSerializer,
            mapOf("ports" to listOf(8000L, 8001L), "weights" to listOf(10L, 20L)),
        )
        val encodedNestedTable: String = toml.encodeToString(
            nestedStringMapSerializer,
            mapOf("owner" to mapOf("name" to "Tom", "organization" to "GitHub")),
        )

        assertThat(decoded).containsEntry("timeout", 30L).containsEntry("retries", 3L)
        assertThat(encodedArrays).contains("ports = [ 8000, 8001 ]")
        assertThat(encodedArrays).contains("weights = [ 10, 20 ]")
        assertThat(encodedNestedTable).contains("[owner]")
        assertThat(encodedNestedTable).contains("name = \"Tom\"")
        assertThat(encodedNestedTable).contains("organization = \"GitHub\"")
    }

    @Test
    fun decodesInlineTablesAndParsesArraysOfTables(): Unit {
        val toml = Toml.Default
        val nestedStringMapSerializer = MapSerializer(
            String.serializer(),
            MapSerializer(String.serializer(), String.serializer()),
        )

        val servers: Map<String, Map<String, String>> = toml.decodeFromString(
            nestedStringMapSerializer,
            "servers = { alpha = \"10.0.0.1\", beta = \"10.0.0.2\" }",
        )
        val tree = TomlParser(TomlInputConfig.compliant()).parseString(
            """
                [[products]]
                name = "Hammer"
                sku = "738594937"

                [[products]]
                name = "Nail"
                sku = "284758393"
            """.trimIndent(),
        )
        val products = tree.findTableInAstByName("products") as TomlTable
        val productElements: List<TomlArrayOfTablesElement> = products.children
            .filterIsInstance<TomlArrayOfTablesElement>()
        val productFields: List<Map<String, String>> = productElements.map { element ->
            element.children
                .filterIsInstance<TomlKeyValuePrimitive>()
                .associate { it.name to it.value.content.toString() }
        }

        assertThat(servers.getValue("servers"))
            .containsEntry("alpha", "10.0.0.1")
            .containsEntry("beta", "10.0.0.2")
        assertThat(products.type).isEqualTo(TableType.ARRAY)
        assertThat(productFields).containsExactly(
            mapOf("name" to "Hammer", "sku" to "738594937"),
            mapOf("name" to "Nail", "sku" to "284758393"),
        )
    }

    @Test
    fun parsesAndEmitsTomlDateAndTimeValues(): Unit {
        val input: String = """
            released = 2024-02-29
            starts = 09:30:15
            created = 2024-02-29T09:30:15
        """.trimIndent()

        val tree = TomlParser(TomlInputConfig.compliant()).parseString(input)
        val temporalValues: Map<String, TomlDateTime> = tree.children
            .filterIsInstance<TomlKeyValuePrimitive>()
            .associate { it.name to it.value as TomlDateTime }
        val emitted: String = TomlWriter(TomlOutputConfig.compliant()).writeToString(tree)

        assertThat(temporalValues.getValue("released").content).isEqualTo(LocalDate(2024, 2, 29))
        assertThat(temporalValues.getValue("starts").content).isEqualTo(LocalTime(9, 30, 15))
        assertThat(temporalValues.getValue("created").content).isEqualTo(LocalDateTime(2024, 2, 29, 9, 30, 15))
        assertThat(emitted).contains("released = 2024-02-29")
        assertThat(emitted).contains("starts = 09:30:15")
        assertThat(emitted).contains("created = 2024-02-29T09:30:15")
    }

    @Test
    fun exposesConfigurationFactoriesAndRejectsSpecViolationsInCompliantMode(): Unit {
        val inputConfig = TomlInputConfig.compliant(ignoreUnknownNames = true, allowEmptyToml = false)
        val outputConfig = TomlOutputConfig.compliant(
            indentation = TomlIndentation.NONE,
            ignoreDefaultValues = true,
            explicitTables = true,
        )

        assertThat(inputConfig.ignoreUnknownNames).isTrue()
        assertThat(inputConfig.allowEmptyValues).isFalse()
        assertThat(inputConfig.allowNullValues).isFalse()
        assertThat(inputConfig.allowEmptyToml).isFalse()
        assertThat(inputConfig.allowEscapedQuotesInLiteralStrings).isFalse()
        assertThat(outputConfig.indentation).isEqualTo(TomlIndentation.NONE)
        assertThat(outputConfig.ignoreDefaultValues).isTrue()
        assertThat(outputConfig.explicitTables).isTrue()
        assertThat(outputConfig.allowEscapedQuotesInLiteralStrings).isFalse()

        assertThatThrownBy {
            Toml(inputConfig = inputConfig).decodeFromString(String.serializer(), "")
        }.isInstanceOf(TomlDecodingException::class.java)
        assertThatThrownBy {
            TomlParser(inputConfig).parseString("optional = null")
        }.isInstanceOf(TomlDecodingException::class.java)
    }
}
