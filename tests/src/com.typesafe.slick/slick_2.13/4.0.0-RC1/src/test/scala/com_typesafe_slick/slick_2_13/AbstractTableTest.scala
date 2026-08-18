/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.lifted.{Index, PrimaryKey, ProvenShape, Tag}
import slick.memory.MemoryProfile.api._

import scala.jdk.CollectionConverters._

class AbstractTableFixture(tag: Tag) extends Table[(Int, String)](tag, "abstract_table_fixture") {
  def id: Rep[Int] = column[Int]("id")

  def name: Rep[String] = column[String]("name")

  def tablePrimaryKey: PrimaryKey = primaryKey("pk_abstract_table_fixture", id)

  def tableNameIndex: Index = index("idx_abstract_table_fixture_name", name, unique = true)

  override def * : ProvenShape[(Int, String)] = (id, name)
}

class AbstractTableTest {
  @Test
  def discoversPrimaryKeysAndIndexesDeclaredOnTable(): Unit = {
    val tables: TableQuery[AbstractTableFixture] = TableQuery[AbstractTableFixture]
    val table: AbstractTableFixture = tables.shaped.value

    val primaryKeys: IndexedSeq[PrimaryKey] = table.primaryKeys.toIndexedSeq
    val indexes: IndexedSeq[Index] = table.indexes.toIndexedSeq

    assertThat(primaryKeys.map(_.name).asJava).containsExactly("pk_abstract_table_fixture")
    assertThat(primaryKeys.head.columns.asJava).hasSize(1)

    assertThat(indexes.map(_.name).asJava).containsExactly("idx_abstract_table_fixture_name")
    assertThat(indexes.head.unique).isTrue
    assertThat(indexes.head.on.asJava).hasSize(1)
    assertThat(indexes.head.table).isSameAs(table)
  }
}
