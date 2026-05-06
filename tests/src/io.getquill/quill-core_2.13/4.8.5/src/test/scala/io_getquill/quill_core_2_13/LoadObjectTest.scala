/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_core_2_13

import io.getquill.Literal
import io.getquill.MirrorContext
import io.getquill.MirrorIdiom
import io.getquill.util.LoadObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.macros.whitebox.{Context => MacroContext}
import scala.reflect.runtime.universe.typeOf

class LoadObjectTest {
  case class Person(name: String, age: Int)

  @Test
  def staticTranslationLoadsSingletonIdiomAndNaming(): Unit = {
    val context: MirrorContext[MirrorIdiom.type, Literal.type] = new MirrorContext(MirrorIdiom, Literal)
    import context._

    val people = quote {
      querySchema[Person]("people")
    }

    val statement: String = context.translate(people)

    assertThat(statement).contains("people")
  }

  @Test
  def applyLoadsSingletonModuleFromType(): Unit = {
    val macroContext: MacroContext = null.asInstanceOf[MacroContext]
    val mirrorIdiomType: macroContext.Type = typeOf[MirrorIdiom.type].asInstanceOf[macroContext.Type]

    val loaded: MirrorIdiom.type = LoadObject[MirrorIdiom.type](macroContext)(mirrorIdiomType).get

    assertThat(loaded).isSameAs(MirrorIdiom)
  }
}
