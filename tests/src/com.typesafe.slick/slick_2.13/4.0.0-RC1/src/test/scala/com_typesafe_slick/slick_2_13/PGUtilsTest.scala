/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import slick.jdbc.PGUtils

class PGUtilsTest {
  @Test
  def createsPostgresObjectWithTypeAndValue(): Unit = {
    val dbType: String = "timestamp"
    val value: String = "2026-05-06 12:34:56"

    val pgObject: AnyRef = PGUtils.createPGObject(value, dbType)

    assertThat(pgObject).isInstanceOf(classOf[PGobject])
    val typedPgObject: PGobject = pgObject.asInstanceOf[PGobject]
    assertThat(typedPgObject.getType).isEqualTo(dbType)
    assertThat(typedPgObject.getValue).isEqualTo(value)
  }
}
