/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_core_2_13

import io.getquill.util.LoadObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.macros.whitebox.Context
import scala.reflect.runtime.universe

object LoadObjectSingleton {
  val value: String = "loaded"
}

class LoadObjectTest {
  @Test
  def loadsSingletonModuleFromScalaType(): Unit = {
    val macroContext: Context = null
    val singletonType: macroContext.Type =
      universe.typeOf[LoadObjectSingleton.type].asInstanceOf[macroContext.Type]

    val loaded: LoadObjectSingleton.type = LoadObject[LoadObjectSingleton.type](macroContext)(singletonType).get

    assertThat(loaded).isSameAs(LoadObjectSingleton)
    assertThat(loaded.value).isEqualTo("loaded")
  }
}
