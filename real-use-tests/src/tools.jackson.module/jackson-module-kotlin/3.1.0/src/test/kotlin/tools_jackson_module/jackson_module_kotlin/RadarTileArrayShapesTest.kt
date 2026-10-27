/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Kotlin array types (primitive arrays, `Array<T>`, nested `Array<IntArray>`, `ByteArray` and
 * `CharArray` payloads, `vararg` creator parameters) and nested read-only collections, as a
 * weather-radar service publishes and reloads reflectivity tiles.
 */
public class RadarTileArrayShapesTest {

    class RadarTile(
        val tileId: String,
        val reflectivity: IntArray,
        val rainfallMm: DoubleArray,
        val clutterMask: BooleanArray,
        val timestamps: LongArray,
        val grid: Array<IntArray>,
        val stationCodes: Array<String>,
        val checksum: ByteArray,
        val qualityFlags: CharArray,
        val bands: List<List<Int>>,
        val legend: Map<String, IntArray>,
    )

    class StationGroup(val region: String, vararg val stations: String)

    data class Station(val code: String, val elevationM: Int)

    private val mapper = jacksonObjectMapper()

    private fun elements(array: JsonNode): List<JsonNode> = (0 until array.size()).map { array.get(it) }

    private fun sampleTile(): RadarTile = RadarTile(
        tileId = "tile-7-12",
        reflectivity = intArrayOf(5, 20, 45),
        rainfallMm = doubleArrayOf(0.0, 1.25, 12.5),
        clutterMask = booleanArrayOf(false, true, false),
        timestamps = longArrayOf(1_700_000_000_000L, 1_700_000_300_000L),
        grid = arrayOf(intArrayOf(1, 2), intArrayOf(3, 4), intArrayOf()),
        stationCodes = arrayOf("KTLX", "KINX"),
        checksum = byteArrayOf(0x52, 0x41, 0x44, 0x41, 0x52),
        qualityFlags = charArrayOf('O', 'K', '!'),
        bands = listOf(listOf(0, 15), listOf(15, 40), emptyList()),
        legend = linkedMapOf("light" to intArrayOf(0, 20), "severe" to intArrayOf(50, 75)),
    )

    @Test
    fun writesPrimitiveAndNestedArraysInTheirJsonShapes(): Unit {
        val tree: JsonNode = mapper.readTree(mapper.writeValueAsString(sampleTile()))

        assertThat(elements(tree.get("reflectivity")).map { it.intValue() }).containsExactly(5, 20, 45)
        assertThat(elements(tree.get("rainfallMm")).map { it.doubleValue() }).containsExactly(0.0, 1.25, 12.5)
        assertThat(elements(tree.get("clutterMask")).map { it.booleanValue() }).containsExactly(false, true, false)
        assertThat(tree.get("timestamps").get(1).longValue()).isEqualTo(1_700_000_300_000L)
        assertThat(tree.get("grid").size()).isEqualTo(3)
        assertThat(elements(tree.get("grid").get(1)).map { it.intValue() }).containsExactly(3, 4)
        assertThat(tree.get("grid").get(2).isEmpty).isTrue()
        assertThat(elements(tree.get("stationCodes")).map { it.asString() }).containsExactly("KTLX", "KINX")
        assertThat(tree.get("checksum").asString()).isEqualTo("UkFEQVI=")
        assertThat(tree.get("qualityFlags").asString()).isEqualTo("OK!")
        assertThat(elements(tree.get("bands").get(1)).map { it.intValue() }).containsExactly(15, 40)
        assertThat(tree.get("legend").propertyNames()).containsExactly("light", "severe")
        assertThat(elements(tree.get("legend").get("severe")).map { it.intValue() }).containsExactly(50, 75)
    }

    @Test
    fun reloadsAPublishedTileIntoKotlinArrays(): Unit {
        val body = """
            {
              "tileId": "tile-3-4",
              "reflectivity": [10, 35],
              "rainfallMm": [0.5, 7.75],
              "clutterMask": [true, false],
              "timestamps": [1700000000000],
              "grid": [[9], [8, 7, 6]],
              "stationCodes": ["KFWS"],
              "checksum": "UkFEQVI=",
              "qualityFlags": "QC",
              "bands": [[1, 2, 3]],
              "legend": {"moderate": [20, 50]}
            }
        """.trimIndent()

        val tile: RadarTile = mapper.readValue(body)

        assertThat(tile.tileId).isEqualTo("tile-3-4")
        assertThat(tile.reflectivity).containsExactly(10, 35)
        assertThat(tile.rainfallMm).containsExactly(0.5, 7.75)
        assertThat(tile.clutterMask).containsExactly(true, false)
        assertThat(tile.timestamps).containsExactly(1_700_000_000_000L)
        assertThat(tile.grid.size).isEqualTo(2)
        assertThat(tile.grid[0]).containsExactly(9)
        assertThat(tile.grid[1]).containsExactly(8, 7, 6)
        assertThat(tile.stationCodes).containsExactly("KFWS")
        assertThat(String(tile.checksum, Charsets.US_ASCII)).isEqualTo("RADAR")
        assertThat(tile.qualityFlags).containsExactly('Q', 'C')
        assertThat(tile.bands).containsExactly(listOf(1, 2, 3))
        assertThat(tile.legend.keys).containsExactly("moderate")
        assertThat(tile.legend.getValue("moderate")).containsExactly(20, 50)
    }

    @Test
    fun roundTripsATileWithoutLosingArrayContents(): Unit {
        val original: RadarTile = sampleTile()

        val echoed: RadarTile = mapper.readValue(mapper.writeValueAsString(original))

        assertThat(echoed.reflectivity.contentEquals(original.reflectivity)).isTrue()
        assertThat(echoed.rainfallMm.contentEquals(original.rainfallMm)).isTrue()
        assertThat(echoed.clutterMask.contentEquals(original.clutterMask)).isTrue()
        assertThat(echoed.timestamps.contentEquals(original.timestamps)).isTrue()
        assertThat(echoed.grid.contentDeepEquals(original.grid)).isTrue()
        assertThat(echoed.stationCodes.contentEquals(original.stationCodes)).isTrue()
        assertThat(echoed.checksum.contentEquals(original.checksum)).isTrue()
        assertThat(echoed.qualityFlags.contentEquals(original.qualityFlags)).isTrue()
        assertThat(echoed.bands).isEqualTo(original.bands)
        assertThat(echoed.legend.mapValues { it.value.toList() })
            .isEqualTo(original.legend.mapValues { it.value.toList() })
    }

    @Test
    fun bindsAVarargCreatorParameterAsAJsonArray(): Unit {
        val group = StationGroup("southern-plains", "KTLX", "KFDR", "KVNX")

        val json: String = mapper.writeValueAsString(group)
        val reloaded: StationGroup = mapper.readValue(json)

        assertThat(json).isEqualTo("""{"region":"southern-plains","stations":["KTLX","KFDR","KVNX"]}""")
        assertThat(reloaded.region).isEqualTo("southern-plains")
        assertThat(reloaded.stations).containsExactly("KTLX", "KFDR", "KVNX")
    }

    @Test
    fun readsTopLevelArraysThroughTheReifiedExtension(): Unit {
        val levels: IntArray = mapper.readValue("[0, 20, 40, 60]")
        val stations: Array<Station> = mapper.readValue(
            """[{"code": "KTLX", "elevationM": 370}, {"code": "KINX", "elevationM": 204}]""",
        )
        val matrix: Array<DoubleArray> = mapper.readValue("[[0.1, 0.2], [0.3]]")

        assertThat(levels).containsExactly(0, 20, 40, 60)
        assertThat(stations).containsExactly(Station("KTLX", 370), Station("KINX", 204))
        assertThat(matrix[0]).containsExactly(0.1, 0.2)
        assertThat(matrix[1]).containsExactly(0.3)
        assertThat(mapper.writeValueAsString(stations))
            .isEqualTo("""[{"code":"KTLX","elevationM":370},{"code":"KINX","elevationM":204}]""")
    }
}
