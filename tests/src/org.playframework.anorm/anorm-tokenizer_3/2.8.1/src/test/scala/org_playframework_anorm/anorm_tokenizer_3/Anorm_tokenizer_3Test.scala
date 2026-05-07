/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_tokenizer_3

import anorm.Show
import anorm.StringShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class Anorm_tokenizer_3Test {
  private final case class Identifier(value: String)

  @Test
  def stringShowReturnsTheUnderlyingValueUnchanged(): Unit = {
    val samples: Seq[String] = Seq(
      "",
      "select * from users",
      "%",
      "{placeholder}",
      "\"quoted\"",
      "line\nbreak",
      "unicode ☕"
    )

    samples.foreach { sample =>
      val rendered: String = new StringShow(sample).show
      assertEquals(sample, rendered)
    }
  }

  @Test
  def showCanBeImplementedByUserCode(): Unit = {
    val customShow: Show = new Show {
      override def show: String = "computed-value"
    }

    assertEquals("computed-value", customShow.show)
  }

  @Test
  def identityMakerReturnsTheSameShowInstance(): Unit = {
    val original: Show = new StringShow("select id from users where name = {name}")
    val rendered: String = Show.mkString(original)(Show.Maker.Identity)

    assertSame(original, Show.Maker.Identity(original))
    assertEquals("select id from users where name = {name}", rendered)
  }

  @Test
  def mkStringUsesTheProvidedShowMaker(): Unit = {
    given identifierShowMaker: Show.Maker[Identifier] with {
      override def apply(subject: Identifier): Show = {
        val escaped: String = subject.value.replace("`", "``")
        new StringShow(s"`$escaped`")
      }
    }

    assertEquals("`users`", Show.mkString(Identifier("users")))
    assertEquals("`tenant``users`", Show.mkString(Identifier("tenant`users")))
  }

  @Test
  def showMakerBehavesAsAFunction(): Unit = {
    val squareMaker: Show.Maker[Int] = new Show.Maker[Int] {
      override def apply(subject: Int): Show = new StringShow((subject * subject).toString)
    }
    val renderSquare: Int => String = squareMaker.andThen(_.show)
    val rendered: List[String] = List(1, 2, 3, 4).map(renderSquare)

    assertEquals(List("1", "4", "9", "16"), rendered)
  }
}
