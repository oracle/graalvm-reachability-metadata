/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

final class SymbolLiteralTest {
  @Test
  def createsInternedSymbolFromLiteral(): Unit = {
    val symbol: Symbol = 'graalvmReachabilityMetadata
    val sameLiteral: Symbol = 'graalvmReachabilityMetadata

    assertThat(symbol.name).isEqualTo("graalvmReachabilityMetadata")
    assertThat(sameLiteral).isSameAs(symbol)
  }
}
