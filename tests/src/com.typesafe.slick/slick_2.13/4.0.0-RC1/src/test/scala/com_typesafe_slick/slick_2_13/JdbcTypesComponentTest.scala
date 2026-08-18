/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import java.sql.Types

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.jdbc.meta.{MColumn, MQName}

class JdbcTypesComponentTest {
  @Test
  def resolvesJdbcTypeNamesFromMetadataWrapper(): Unit = {
    val tableName: MQName = MQName(None, None, "users")
    val column: MColumn = MColumn(
      table = tableName,
      name = "id",
      sqlType = Types.INTEGER,
      typeName = "INTEGER",
      size = Some(10),
      decimalDigits = Some(0),
      numPrecRadix = 10,
      nullable = Some(false),
      remarks = Some("primary key"),
      columnDef = None,
      charOctetLength = 0,
      ordinalPosition = 1,
      isNullable = Some(false),
      scope = None,
      sourceDataType = None,
      isAutoInc = Some(true),
      isGenerated = Some(false))

    assertThat(column.sqlTypeName).isEqualTo(Some("INTEGER"))
  }
}
