/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InheritableSerialInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises serializable class inheritance: abstract `@Serializable` base classes whose body
 * properties generated subclass serializers write first, private base state, base `init` blocks
 * run on decode, abstract properties a subclass stores, open classes and `@InheritableSerialInfo`.
 *
 * An IT asset inventory models hardware as a class hierarchy, exports it to JSON for its REST
 * API and to flat properties for a CMDB feed, and derives purge dates from the retention policy
 * every asset inherits from the base class.
 */
@OptIn(ExperimentalSerializationApi::class)
public class AssetInventoryClassInheritanceTest {

    /** Retention policy for inventory records; subclasses inherit it from the base class. */
    @InheritableSerialInfo
    @Target(AnnotationTarget.CLASS)
    public annotation class KeepFor(val days: Int)

    /** State every inventory asset shares; never stored on its own. */
    @KeepFor(days = 2555)
    @Serializable
    public abstract class Asset {
        public abstract val assetTag: String

        public abstract val site: String

        /** Inventory document schema; older documents are rejected on decode. */
        @SerialName("schema")
        public val schemaVersion: Int = CURRENT_SCHEMA

        /** Person currently responsible for the asset; optional on decode. */
        public var custodian: String? = null

        /** Display label computed from other properties; has no backing field, so not stored. */
        public val label: String
            get() = "$site/$assetTag"

        init {
            require(schemaVersion >= CURRENT_SCHEMA) { "Unsupported inventory schema $schemaVersion" }
        }

        public companion object {
            public const val CURRENT_SCHEMA: Int = 2
        }
    }

    /** Powered hardware with a serial number and a purchase price kept private to the ledger. */
    @Serializable
    public abstract class Device : Asset() {
        public abstract val serialNumber: String

        private var purchaseCents: Long = 0

        public fun recordPurchase(cents: Long) {
            require(cents > 0) { "Purchase price must be positive" }
            purchaseCents = cents
        }

        /** Straight-line book value after [years], over a five-year depreciation schedule. */
        public fun bookValueCents(years: Int): Long = purchaseCents * maxOf(0, 5 - years) / 5
    }

    /** Laptop issued to an employee. */
    @Serializable
    public class Laptop(
        @SerialName("asset_tag") override val assetTag: String,
        override val site: String,
        override val serialNumber: String,
        public val ramGb: Int,
        public val encrypted: Boolean = true,
    ) : Device() {
        init {
            require(assetTag.matches(Regex("LT-[0-9]{4}"))) { "Malformed laptop tag '$assetTag'" }
        }
    }

    /** Desk monitor; tracked with a shorter retention than other assets. */
    @KeepFor(days = 365)
    @Serializable
    public class Monitor(
        @SerialName("asset_tag") override val assetTag: String,
        override val site: String,
        override val serialNumber: String,
        public val diagonalInches: Int,
    ) : Device()

    /** Unpowered accessory; an open class the inventory also stores on its own. */
    @Serializable
    public open class Peripheral(
        @SerialName("asset_tag") override val assetTag: String,
        override val site: String,
        public val model: String,
    ) : Asset()

    /** Reads the retention policy a record type declares or inherits from its descriptor. */
    public object RetentionPolicy {
        public fun retentionDays(descriptor: SerialDescriptor): Int =
            descriptor.annotations.filterIsInstance<KeepFor>().first().days

        public fun purgeDay(descriptor: SerialDescriptor, acquiredOnDay: Long): Long =
            acquiredOnDay + retentionDays(descriptor)
    }

    private val apiJson: Json = Json { encodeDefaults = true }

    private fun issuedLaptop(): Laptop =
        Laptop(assetTag = "LT-0042", site = "berlin", serialNumber = "C02XK1", ramGb = 32).apply {
            custodian = "a.meier"
            recordPurchase(180_000)
        }

    @Test
    fun subclassDocumentsListInheritedPropertiesFirst() {
        val document: String = apiJson.encodeToString(Laptop.serializer(), issuedLaptop())

        assertEquals(
            """{"schema":2,"custodian":"a.meier","purchaseCents":180000,""" +
                """"asset_tag":"LT-0042","site":"berlin","serialNumber":"C02XK1","ramGb":32,"encrypted":true}""",
            document,
        )
        assertEquals(
            listOf("schema", "custodian", "purchaseCents", "asset_tag", "site", "serialNumber", "ramGb", "encrypted"),
            Laptop.serializer().descriptor.elementNames.toList(),
        )
        assertFalse(document.contains("label"))
    }

    @Test
    fun decodingRestoresBaseStateIncludingPrivateAndBodyProperties() {
        val received: String =
            """{"purchaseCents":150000,"asset_tag":"LT-0107","site":"lisbon","serialNumber":"FVFZ9","ramGb":16}"""

        val decoded: Laptop = apiJson.decodeFromString(Laptop.serializer(), received)

        assertEquals("LT-0107", decoded.assetTag)
        assertEquals("lisbon/LT-0107", decoded.label)
        assertEquals("FVFZ9", decoded.serialNumber)
        assertEquals(16, decoded.ramGb)
        assertTrue(decoded.encrypted)
        assertEquals(Asset.CURRENT_SCHEMA, decoded.schemaVersion)
        assertNull(decoded.custodian)
        assertEquals(90_000, decoded.bookValueCents(years = 2))
    }

    @Test
    fun baseAndSubclassInitBlocksRunWhenDecoding() {
        val legacy: String =
            """{"schema":1,"asset_tag":"LT-0001","site":"oslo","serialNumber":"X1","ramGb":8}"""
        val baseError: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            apiJson.decodeFromString(Laptop.serializer(), legacy)
        }
        assertEquals("Unsupported inventory schema 1", baseError.message)

        val mistyped: String = """{"asset_tag":"MO-0001","site":"oslo","serialNumber":"X1","ramGb":8}"""
        val subclassError: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            apiJson.decodeFromString(Laptop.serializer(), mistyped)
        }
        assertEquals("Malformed laptop tag 'MO-0001'", subclassError.message)
    }

    @Test
    fun cmdbFeedFlattensTheWholeHierarchy() {
        val monitor = Monitor(assetTag = "MO-0311", site = "lisbon", serialNumber = "DELL-U27", diagonalInches = 27)
            .apply {
                custodian = "facilities"
                recordPurchase(30_000)
            }

        val feed: Map<String, String> = Properties.encodeToStringMap(Monitor.serializer(), monitor)
        assertEquals(
            mapOf(
                "schema" to "2",
                "custodian" to "facilities",
                "purchaseCents" to "30000",
                "asset_tag" to "MO-0311",
                "site" to "lisbon",
                "serialNumber" to "DELL-U27",
                "diagonalInches" to "27",
            ),
            feed,
        )

        val restored: Monitor = Properties.decodeFromStringMap(Monitor.serializer(), feed)
        assertEquals("lisbon/MO-0311", restored.label)
        assertEquals(27, restored.diagonalInches)
        assertEquals("facilities", restored.custodian)
        assertEquals(6_000, restored.bookValueCents(years = 4))
    }

    @Test
    fun openSubclassSerializesStaticallyWhileTheAbstractBaseIsPolymorphic() {
        val headset = Peripheral(assetTag = "PH-0009", site = "berlin", model = "Jabra Evolve2")

        val document: String = apiJson.encodeToString(Peripheral.serializer(), headset)
        assertEquals(
            """{"schema":2,"custodian":null,"asset_tag":"PH-0009","site":"berlin","model":"Jabra Evolve2"}""",
            document,
        )
        val restored: Peripheral = apiJson.decodeFromString(serializer<Peripheral>(), document)
        assertEquals("berlin/PH-0009", restored.label)
        assertEquals("Jabra Evolve2", restored.model)

        assertEquals(StructureKind.CLASS, serializer<Peripheral>().descriptor.kind)
        val assetSerializer: KSerializer<Asset> = serializer<Asset>()
        assertEquals(PolymorphicKind.OPEN, assetSerializer.descriptor.kind)
    }

    @Test
    fun retentionPolicyIsInheritedUnlessASubclassDeclaresItsOwn() {
        assertEquals(2555, RetentionPolicy.retentionDays(Laptop.serializer().descriptor))
        assertEquals(2555, RetentionPolicy.retentionDays(Peripheral.serializer().descriptor))
        assertEquals(365, RetentionPolicy.retentionDays(Monitor.serializer().descriptor))
        assertEquals(20_365L, RetentionPolicy.purgeDay(Monitor.serializer().descriptor, acquiredOnDay = 20_000))
    }
}
