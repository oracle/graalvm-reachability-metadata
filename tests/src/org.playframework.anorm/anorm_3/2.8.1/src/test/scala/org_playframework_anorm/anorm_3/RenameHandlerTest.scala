/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.assertj.core.api.Assertions.assertThat
import org.joda.convert.RenameHandler
import org.junit.jupiter.api.Test

class RenameHandlerTest {
  @Test
  def loadsRenameConfigurationResourcesFromClasspath(): Unit = {
    val thread: Thread = Thread.currentThread()
    val originalLoader: ClassLoader = thread.getContextClassLoader

    try {
      thread.setContextClassLoader(classOf[RenameHandlerTest].getClassLoader)
      val handler: RenameHandler = RenameHandler.create(true)

      assertThat(handler.lookupType("example.LegacyRenameHandlerType"))
        .isEqualTo(classOf[RenameHandlerTest])
      assertThat(handler.lookupEnum(classOf[java.time.DayOfWeek], "OLD_RENAME_HANDLER_STATUS"))
        .isEqualTo(java.time.DayOfWeek.MONDAY)
    } finally {
      thread.setContextClassLoader(originalLoader)
    }
  }
}
