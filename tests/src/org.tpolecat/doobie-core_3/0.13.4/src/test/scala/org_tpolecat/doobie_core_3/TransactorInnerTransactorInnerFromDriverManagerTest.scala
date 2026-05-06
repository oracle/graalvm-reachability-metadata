/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_core_3

import doobie.util.transactor.Transactor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactorInnerTransactorInnerFromDriverManagerTest {
  @Test
  def fromDriverManagerLazyValBitmapFieldIsReachable(): Unit = {
    assertThat(Transactor.fromDriverManager).isNotNull
  }
}
