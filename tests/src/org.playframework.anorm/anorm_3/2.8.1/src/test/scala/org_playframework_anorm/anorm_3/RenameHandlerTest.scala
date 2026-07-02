/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.joda.convert.RenameHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RenameHandlerTest {
  @Test
  def loadsRenameConfigurationResourcesFromClasspath(): Unit = {
    val handler: RenameHandler = RenameHandler.create(true)

    assertSame(classOf[RenameHandlerTest], handler.lookupType("example.LegacyAnormName"))
    assertEquals(Thread.State.RUNNABLE, handler.lookupEnum(classOf[Thread.State], "OLD_RUNNABLE"))
  }
}
