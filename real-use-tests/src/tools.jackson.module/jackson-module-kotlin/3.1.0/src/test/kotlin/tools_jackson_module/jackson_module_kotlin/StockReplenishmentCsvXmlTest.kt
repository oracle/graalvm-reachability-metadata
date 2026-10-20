/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonParser
import tools.jackson.databind.ObjectReader
import tools.jackson.dataformat.csv.CsvMapper
import tools.jackson.dataformat.csv.CsvReadFeature
import tools.jackson.dataformat.csv.CsvSchema
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import tools.jackson.module.kotlin.readValuesTyped
import java.io.ByteArrayInputStream
import java.io.StringReader

/**
 * Kotlin data classes bound through the CSV and XML dataformat mappers with the Kotlin module
 * registered, streamed with the reified `ObjectReader.readValuesTyped` and read with `readValue`,
 * as a stock-sync job turns a warehouse CSV export into XML replenishment orders.
 */
public class StockReplenishmentCsvXmlTest {

    /** One row of the nightly stock export; `reorderLevel` and `note` are optional columns. */
    @JsonPropertyOrder("sku", "warehouse", "quantity", "reorderLevel", "note")
    data class StockRow(
        val sku: String,
        val warehouse: String,
        val quantity: Int,
        val reorderLevel: Int = 10,
        val note: String? = null,
    ) {
        fun needsReorder(): Boolean = quantity < reorderLevel
    }

    enum class Priority { NORMAL, URGENT }

    /** A single order line written as `<line sku="..." quantity="..."/>`. */
    data class OrderLine(
        @JacksonXmlProperty(isAttribute = true) val sku: String,
        @JacksonXmlProperty(isAttribute = true) val quantity: Int,
    )

    /** The replenishment order the supplier gateway accepts as XML. */
    @JacksonXmlRootElement(localName = "replenishmentOrder")
    @JsonPropertyOrder("id", "warehouse", "priority", "lines", "tag", "comment")
    data class ReplenishmentOrder(
        @JacksonXmlProperty(isAttribute = true) val id: String,
        @JacksonXmlProperty(isAttribute = true) val warehouse: String,
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "line")
        val lines: List<OrderLine>,
        val priority: Priority = Priority.NORMAL,
        @JacksonXmlElementWrapper(useWrapping = false)
        val tag: List<String> = emptyList(),
        val comment: String? = null,
    )

    private val csvMapper: CsvMapper = CsvMapper.builder()
        .addModule(kotlinModule())
        .enable(CsvReadFeature.EMPTY_STRING_AS_NULL)
        .build()

    private val xmlMapper: XmlMapper = XmlMapper.builder()
        .addModule(kotlinModule())
        .build()

    private fun readStock(csv: String): List<StockRow> {
        val reader: ObjectReader = csvMapper.readerFor(StockRow::class.java)
            .with(CsvSchema.emptySchema().withHeader())
        val parser: JsonParser = reader.createParser(csv)
        return parser.use { reader.readValuesTyped<StockRow>(it).asSequence().toList() }
    }

    private fun planOrders(rows: List<StockRow>): List<ReplenishmentOrder> =
        rows.filter { it.needsReorder() }
            .groupBy { it.warehouse }
            .map { (warehouse: String, low: List<StockRow>) ->
                ReplenishmentOrder(
                    id = "R-$warehouse",
                    warehouse = warehouse,
                    lines = low.map { OrderLine(it.sku, it.reorderLevel * 2 - it.quantity) },
                    priority = if (low.any { it.quantity == 0 }) {
                        Priority.URGENT
                    } else {
                        Priority.NORMAL
                    },
                    tag = low.mapNotNull { it.note },
                )
            }

    @Test
    fun headerDrivenCsvStreamsIntoDataClassesWithKotlinDefaults() {
        val csv: String = """
            sku,warehouse,quantity,note
            A-100,BER,3,
            A-200,BER,0,fragile
            B-300,MUC,25,
            B-400,MUC,7,seasonal
        """.trimIndent() + "\n"

        val rows: List<StockRow> = readStock(csv)

        assertThat(rows).containsExactly(
            StockRow("A-100", "BER", 3),
            StockRow("A-200", "BER", 0, note = "fragile"),
            StockRow("B-300", "MUC", 25),
            StockRow("B-400", "MUC", 7, note = "seasonal"),
        )
        assertThat(rows.map { it.reorderLevel }).containsOnly(10)
        assertThat(rows.filter { it.needsReorder() }.map { it.sku })
            .containsExactly("A-100", "A-200", "B-400")
    }

    @Test
    fun dataClassSchemaWritesAndReadsBackTheFullExport() {
        val rows: List<StockRow> = listOf(
            StockRow("C-500", "HAM", 4, reorderLevel = 6),
            StockRow("C-600", "HAM", 12, reorderLevel = 5, note = "pallet, top shelf"),
        )
        val schema: CsvSchema = csvMapper.schemaFor(StockRow::class.java).withHeader()

        val csv: String = csvMapper.writer(schema).writeValueAsString(rows)

        assertThat(csv.lines().filter { it.isNotEmpty() }).containsExactly(
            "sku,warehouse,quantity,reorderLevel,note",
            "C-500,HAM,4,6,",
            "C-600,HAM,12,5,\"pallet, top shelf\"",
        )
        assertThat(readStock(csv)).isEqualTo(rows)
    }

    @Test
    fun replenishmentOrdersRoundTripThroughXml() {
        val csv: String = """
            sku,warehouse,quantity,reorderLevel,note
            A-100,BER,3,10,
            A-200,BER,0,4,fragile
            B-300,MUC,25,10,
        """.trimIndent() + "\n"
        val orders: List<ReplenishmentOrder> = planOrders(readStock(csv))
        assertThat(orders).hasSize(1)
        val order: ReplenishmentOrder = orders.single()

        val xml: String = xmlMapper.writeValueAsString(order)

        assertThat(xml).startsWith(
            """<replenishmentOrder id="R-BER" warehouse="BER"><priority>URGENT</priority>""",
        )
        assertThat(xml).contains(
            """<line sku="A-100" quantity="17"/><line sku="A-200" quantity="8"/>""",
            "<tag>fragile</tag>",
        )
        assertThat(xml).doesNotContain("<lines>")
        assertThat(xmlMapper.readValue<ReplenishmentOrder>(xml)).isEqualTo(order)
    }

    @Test
    fun supplierXmlFillsOmittedElementsFromKotlinDefaults() {
        val xml: String = """
            <replenishmentOrder id="R-7" warehouse="MUC">
              <line sku="B-400" quantity="13"/>
              <tag>seasonal</tag>
              <tag>backorder</tag>
            </replenishmentOrder>
        """.trimIndent()

        val fromReader: ReplenishmentOrder = xmlMapper.readValue(StringReader(xml))
        val fromStream: ReplenishmentOrder =
            xmlMapper.readValue(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        assertThat(fromReader).isEqualTo(
            ReplenishmentOrder(
                id = "R-7",
                warehouse = "MUC",
                lines = listOf(OrderLine("B-400", 13)),
                tag = listOf("seasonal", "backorder"),
            ),
        )
        assertThat(fromReader.priority).isEqualTo(Priority.NORMAL)
        assertThat(fromReader.comment).isNull()
        assertThat(fromStream).isEqualTo(fromReader)
    }
}
