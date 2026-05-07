/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_geirsson.metaconfig_pprint_2_13

import metaconfig.pprint.TPrint
import metaconfig.pprint.TPrintColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Metaconfig_pprint_2_13Test {
  @Test
  def defaultTypePrinterRendersCommonScalaTypes(): Unit = {
    assertEquals("Int", renderPlain[Int])
    assertEquals("String", renderPlain[String])
    assertEquals("Option[Int]", renderPlain[Option[Int]])
    assertEquals("List[String]", renderPlain[List[String]])
    assertEquals("Either[String, Int]", renderPlain[Either[String, Int]])
    assertEquals("Map[String, List[Int]]", renderPlain[Map[String, List[Int]]])
  }

  @Test
  def defaultTypePrinterRendersLiteralSingletonTypes(): Unit = {
    assertEquals("42", renderPlain[42])
    assertEquals("true", renderPlain[true])
    assertEquals("ready", renderPlain["ready"])
  }

  @Test
  def defaultTypePrinterRendersTuplesFunctionsAndInfixTypes(): Unit = {
    assertEquals("(String, Int)", renderPlain[(String, Int)])
    assertEquals("(String, Int, Boolean)", renderPlain[(String, Int, Boolean)])
    assertEquals("() => Int", renderPlain[() => Int])
    assertEquals("String => Int", renderPlain[String => Int])
    assertEquals("(String, Int) => Boolean", renderPlain[(String, Int) => Boolean])
    assertEquals("((String, Int)) => Boolean", renderPlain[((String, Int)) => Boolean])
    assertEquals("Int <:< AnyVal", renderPlain[Int <:< AnyVal])
  }

  @Test
  def defaultTypePrinterRendersWildcardTypeBounds(): Unit = {
    assertEquals("Option[_]", renderPlain[Option[_]])
    assertEquals("List[_] forSome { type _ <: AnyVal }", renderPlain[List[_ <: AnyVal]])
    assertEquals("List[_] forSome { type _ >: Int }", renderPlain[List[_ >: Int]])
  }

  @Test
  def explicitTypePrintInstancesOverrideGeneratedPrinter(): Unit = {
    implicit val intPrint: TPrint[Int] = new TPrint[Int] {
      override def render(implicit tpc: TPrintColors): fansi.Str = fansi.Str("number")
    }
    implicit def optionPrint[T](implicit elementPrint: TPrint[T]): TPrint[Option[T]] = new TPrint[Option[T]] {
      override def render(implicit tpc: TPrintColors): fansi.Str = {
        fansi.Str("optional(") ++ elementPrint.render ++ fansi.Str(")")
      }
    }

    val rendered: String = TPrint.implicitly[Option[Int]].render(TPrintColors.BlackWhite).plainText

    assertEquals("optional(number)", rendered)
  }

  @Test
  def recolorMapsGreenSegmentsToRequestedTypeColor(): Unit = {
    val original: fansi.Str =
      fansi.Str("prefix ") ++ fansi.Color.Green("TypeName") ++ fansi.Str(" ") ++ fansi.Color.Red("literal")
    val print: TPrint[String] = TPrint.recolor[String](original)

    val blackWhite: fansi.Str = print.render(TPrintColors.BlackWhite)
    assertEquals("prefix TypeName literal", blackWhite.plainText)
    assertTrue(blackWhite.getColors.forall(_ == 0L), "black-and-white rendering should remove all colors")

    val blueColors: TPrintColors = TPrintColors(fansi.Color.Blue)
    val blue: fansi.Str = print.render(blueColors)
    val blueMask: Long = fansi.Color.Blue.applyMask
    val greenSpan: Range = "prefix ".length until "prefix TypeName".length
    val nonGreenIndexes: IndexedSeq[Int] = blue.getColors.indices.filterNot(greenSpan.contains).toIndexedSeq

    assertEquals("prefix TypeName literal", blue.plainText)
    assertTrue(greenSpan.forall(index => blue.getColor(index) == blueMask), "green source span should become blue")
    assertTrue(nonGreenIndexes.forall(index => blue.getColor(index) == 0L), "non-green source spans should be uncolored")
  }

  @Test
  def nothingPrinterUsesConfiguredColors(): Unit = {
    val blackWhite: fansi.Str = TPrint.NothingTPrint.render(TPrintColors.BlackWhite)
    assertEquals("Nothing", blackWhite.plainText)
    assertTrue(blackWhite.getColors.forall(_ == 0L), "black-and-white Nothing should be uncolored")

    val colored: fansi.Str = TPrint.NothingTPrint.render(TPrintColors.Colors.Colored)
    val greenMask: Long = fansi.Color.Green.applyMask
    assertEquals("Nothing", colored.plainText)
    assertTrue(colored.getColors.forall(_ == greenMask), "colored Nothing should use the configured type color")
  }

  @Test
  def typePrintColorsExposeCaseClassApi(): Unit = {
    val blue: TPrintColors = TPrintColors(fansi.Color.Blue)
    val red: TPrintColors = blue.copy(typeColor = fansi.Color.Red)
    val extracted: Option[fansi.Attrs] = TPrintColors.unapply(red)

    assertEquals(fansi.Color.Blue, blue.typeColor)
    assertEquals(fansi.Color.Red, red.typeColor)
    assertEquals(Some(fansi.Color.Red), extracted)
    assertEquals("TPrintColors", red.productPrefix)
    assertEquals(1, red.productArity)
    assertTrue(red.canEqual(blue))
  }

  private def renderPlain[T](implicit print: TPrint[T]): String = {
    print.render(TPrintColors.BlackWhite).plainText
  }
}
