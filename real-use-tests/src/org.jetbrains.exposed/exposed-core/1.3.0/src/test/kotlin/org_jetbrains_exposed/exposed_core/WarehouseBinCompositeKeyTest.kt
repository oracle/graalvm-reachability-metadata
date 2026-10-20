/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.dao.CompositeEntity
import org.jetbrains.exposed.v1.dao.CompositeEntityClass
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Composite primary keys: a warehouse addresses storage bins by (warehouse, aisle, shelf) in a
 * `CompositeIdTable`, writes and looks bins up by `CompositeID` through both the SQL DSL and
 * `CompositeEntity`, and places pallets on bins through a multi-column foreign key.
 */
class WarehouseBinCompositeKeyTest {

    object BinLocations : CompositeIdTable("bin_locations") {
        val warehouse: Column<EntityID<String>> = varchar("warehouse", 8).entityId()
        val aisle: Column<EntityID<Int>> = integer("aisle").entityId()
        val shelf: Column<EntityID<Int>> = integer("shelf").entityId()
        val zone: Column<String> = varchar("zone", 16)
        val capacity: Column<Int> = integer("capacity")

        override val primaryKey = PrimaryKey(warehouse, aisle, shelf)
    }

    object Pallets : IntIdTable("pallets") {
        val sku: Column<String> = varchar("sku", 16)
        val units: Column<Int> = integer("units")
        val warehouse: Column<String> = varchar("warehouse", 8)
        val aisle: Column<Int> = integer("aisle")
        val shelf: Column<Int> = integer("shelf")

        init {
            foreignKey(warehouse, aisle, shelf, target = BinLocations.primaryKey)
        }
    }

    class BinLocation(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<BinLocation>(BinLocations)

        var zone: String by BinLocations.zone
        var capacity: Int by BinLocations.capacity
        val pallets by Pallet referrersOn Pallets
    }

    class Pallet(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Pallet>(Pallets)

        var sku: String by Pallets.sku
        var units: Int by Pallets.units
        var location: BinLocation by BinLocation referencedOn Pallets
    }

    private fun binAddress(warehouse: String, aisle: Int, shelf: Int): CompositeID = CompositeID {
        it[BinLocations.warehouse] = warehouse
        it[BinLocations.aisle] = aisle
        it[BinLocations.shelf] = shelf
    }

    @Test
    fun addressesBinsByCompositeKey() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:warehouse_bins;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) {
            SchemaUtils.create(BinLocations, Pallets)
        }

        val amsAisle3Shelf2: EntityID<CompositeID> = transaction(database) {
            BinLocations.insert {
                it[warehouse] = "AMS"
                it[aisle] = 3
                it[shelf] = 1
                it[zone] = "ambient"
                it[capacity] = 4
            }
            BinLocations.insertAndGetId {
                it[warehouse] = "AMS"
                it[aisle] = 3
                it[shelf] = 2
                it[zone] = "ambient"
                it[capacity] = 2
            }
        }
        assertThat(amsAisle3Shelf2.value[BinLocations.warehouse].value).isEqualTo("AMS")
        assertThat(amsAisle3Shelf2.value[BinLocations.aisle].value).isEqualTo(3)
        assertThat(amsAisle3Shelf2.value[BinLocations.shelf].value).isEqualTo(2)
        assertThat(amsAisle3Shelf2.value).isEqualTo(binAddress("AMS", 3, 2))

        assertThatThrownBy {
            transaction(database) {
                BinLocations.insert {
                    it[warehouse] = "AMS"
                    it[aisle] = 3
                    it[shelf] = 2
                    it[zone] = "frozen"
                    it[capacity] = 9
                }
            }
        }.isInstanceOf(ExposedSQLException::class.java)

        transaction(database) {
            BinLocation.new(binAddress("RTM", 1, 1)) {
                zone = "frozen"
                capacity = 3
            }
            BinLocation.new(binAddress("RTM", 1, 2)) {
                zone = "frozen"
                capacity = 1
            }
        }

        val rtmBin: Pair<String, Int>? = transaction(database) {
            BinLocation.findById(binAddress("RTM", 1, 2))?.let { it.zone to it.capacity }
        }
        assertThat(rtmBin).isEqualTo("frozen" to 1)
        assertThat(transaction(database) { BinLocation.findById(binAddress("RTM", 2, 1)) }).isNull()

        transaction(database) {
            BinLocation.findByIdAndUpdate(binAddress("AMS", 3, 1)) { it.capacity = 6 }
        }
        val amsCapacities: List<String> = transaction(database) {
            BinLocations.selectAll()
                .where { (BinLocations.warehouse eq "AMS") and (BinLocations.aisle eq 3) }
                .orderBy(BinLocations.shelf to SortOrder.ASC)
                .map { row -> "${row[BinLocations.shelf].value}:${row[BinLocations.capacity]}" }
        }
        assertThat(amsCapacities).containsExactly("1:6", "2:2")

        transaction(database) {
            val ambient: BinLocation = BinLocation[amsAisle3Shelf2]
            val frozen: BinLocation = BinLocation[binAddress("RTM", 1, 1)]
            Pallet.new {
                sku = "TEA-01"
                units = 40
                location = ambient
            }
            Pallet.new {
                sku = "TEA-02"
                units = 25
                location = ambient
            }
            Pallet.new {
                sku = "ICE-07"
                units = 60
                location = frozen
            }
        }

        val storedAddresses: List<String> = transaction(database) {
            Pallets.selectAll()
                .orderBy(Pallets.sku to SortOrder.ASC)
                .map { row -> "${row[Pallets.sku]}@${row[Pallets.warehouse]}/${row[Pallets.aisle]}/${row[Pallets.shelf]}" }
        }
        assertThat(storedAddresses).containsExactly("ICE-07@RTM/1/1", "TEA-01@AMS/3/2", "TEA-02@AMS/3/2")

        val ambientSkus: List<String> = transaction(database) {
            BinLocation[amsAisle3Shelf2].pallets.map { it.sku }.sorted()
        }
        assertThat(ambientSkus).containsExactly("TEA-01", "TEA-02")

        val iceLocation: String = transaction(database) {
            val ice: Pallet = Pallet.find { Pallets.sku eq "ICE-07" }.single()
            val address: CompositeID = ice.location.id.value
            "${address[BinLocations.warehouse].value}/${address[BinLocations.aisle].value} ${ice.location.zone}"
        }
        assertThat(iceLocation).isEqualTo("RTM/1 frozen")

        val unitsPerZone: Map<String, Int?> = transaction(database) {
            val totalUnits = Pallets.units.sum()
            Pallets.join(BinLocations, JoinType.INNER) {
                (Pallets.warehouse eq BinLocations.warehouse) and
                    (Pallets.aisle eq BinLocations.aisle) and
                    (Pallets.shelf eq BinLocations.shelf)
            }
                .select(BinLocations.zone, totalUnits)
                .groupBy(BinLocations.zone)
                .associate { row -> row[BinLocations.zone] to row[totalUnits] }
        }
        assertThat(unitsPerZone).containsExactlyInAnyOrderEntriesOf(mapOf("ambient" to 65, "frozen" to 60))

        assertThatThrownBy {
            transaction(database) {
                Pallets.insert {
                    it[sku] = "LOST-99"
                    it[units] = 1
                    it[warehouse] = "AMS"
                    it[aisle] = 9
                    it[shelf] = 9
                }
            }
        }.isInstanceOf(ExposedSQLException::class.java)

        val remainingBins: List<String> = transaction(database) {
            BinLocation[binAddress("RTM", 1, 2)].delete()
            BinLocations.deleteWhere { (BinLocations.warehouse eq "AMS") and (BinLocations.shelf eq 1) }
            BinLocation.all().map { bin ->
                val address: CompositeID = bin.id.value
                "${address[BinLocations.warehouse].value}/${address[BinLocations.aisle].value}/${address[BinLocations.shelf].value}"
            }.sorted()
        }
        assertThat(remainingBins).containsExactly("AMS/3/2", "RTM/1/1")

        transaction(database) {
            SchemaUtils.drop(Pallets, BinLocations)
        }
    }
}
