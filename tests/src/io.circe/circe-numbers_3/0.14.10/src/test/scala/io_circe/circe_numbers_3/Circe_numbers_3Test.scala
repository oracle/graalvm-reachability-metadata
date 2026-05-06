/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_numbers_3

import io.circe.numbers.BiggerDecimal
import java.math.{BigDecimal as JBigDecimal, BigInteger as JBigInteger}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.{assertThrows, assertTrue}
import org.junit.jupiter.api.Test

class Circe_numbers_3Test {
  @Test
  def parsesCanonicalIntegerDecimalAndExponentForms(): Unit = {
    val cases: List[(String, String, Option[Long], Boolean)] = List(
      ("0", "0", Some(0L), true),
      ("42", "42", Some(42L), true),
      ("-17", "-17", Some(-17L), true),
      ("1000.00", "1e3", Some(1000L), true),
      ("-12.3400e+2", "-1234", Some(-1234L), true),
      ("1.234e-2", "1234e-5", None, false),
      ("12E3", "12e3", Some(12000L), true),
      ("0.0012300", "123e-5", None, false)
    )

    cases.foreach {
      case (input: String, expectedText: String, expectedLong: Option[Long], expectedWhole: Boolean) =>
        val number: BiggerDecimal = parse(input)

        assertThat(number.toString).isEqualTo(expectedText)
        assertThat(number.isWhole).isEqualTo(expectedWhole)
        assertThat(number.toLong).isEqualTo(expectedLong)
        assertThat(number.isNegativeZero).isFalse()
    }
  }

  @Test
  def rejectsMalformedNumberStrings(): Unit = {
    val invalidInputs: List[String] = List(
      "",
      "-",
      "+1",
      "abc",
      "1.",
      "1e",
      "1e+",
      "1e-",
      "1.2.3",
      "1e2e3",
      "--1",
      "1a"
    )

    invalidInputs.foreach { (input: String) =>
      assertThat(BiggerDecimal.parseBiggerDecimal(input)).isEqualTo(None)
      assertThat(BiggerDecimal.parseBiggerDecimalUnsafe(input)).isNull()
    }
  }

  @Test
  def preservesTheSignOfNegativeZeroAcrossConstructorsAndParsing(): Unit = {
    val negativeZeroValues: List[BiggerDecimal] = List(
      BiggerDecimal.NegativeZero,
      parse("-0"),
      parse("-0.000"),
      BiggerDecimal.fromDoubleUnsafe(-0.0d),
      BiggerDecimal.fromFloat(-0.0f)
    )
    val unsignedZero: BiggerDecimal = BiggerDecimal.fromLong(0L)

    negativeZeroValues.foreach { (number: BiggerDecimal) =>
      assertThat(number).isEqualTo(BiggerDecimal.NegativeZero)
      assertThat(number).isNotEqualTo(unsignedZero)
      assertThat(number.isWhole).isTrue()
      assertThat(number.isNegativeZero).isTrue()
      assertThat(number.signum).isZero()
      assertThat(number.toBigDecimal).isEqualTo(Some(JBigDecimal.ZERO))
      assertThat(number.toBigInteger).isEqualTo(Some(JBigInteger.ZERO))
      assertThat(number.toLong).isEqualTo(Some(0L))
      assertThat(number.toString).isEqualTo("-0")
      assertThat(java.lang.Double.doubleToRawLongBits(number.toDouble))
        .isEqualTo(java.lang.Double.doubleToRawLongBits(-0.0d))
      assertThat(java.lang.Float.floatToRawIntBits(number.toFloat))
        .isEqualTo(java.lang.Float.floatToRawIntBits(-0.0f))
    }

    assertThat(unsignedZero.isNegativeZero).isFalse()
    assertThat(unsignedZero.toString).isEqualTo("0")
    assertThat(java.lang.Double.doubleToRawLongBits(unsignedZero.toDouble))
      .isEqualTo(java.lang.Double.doubleToRawLongBits(0.0d))
  }

  @Test
  def convertsFromJavaNumbersAndNormalizesTrailingZeros(): Unit = {
    val fromBigInteger: BiggerDecimal =
      BiggerDecimal.fromBigInteger(new JBigInteger("100000000000000000000"))
    val fromBigDecimal: BiggerDecimal = BiggerDecimal.fromBigDecimal(new JBigDecimal("123.4500"))
    val fromLong: BiggerDecimal = BiggerDecimal.fromLong(Long.MinValue)
    val fromDouble: BiggerDecimal = BiggerDecimal.fromDoubleUnsafe(1.25d)
    val fromFloat: BiggerDecimal = BiggerDecimal.fromFloat(12.5f)

    assertThat(fromBigInteger.toString).isEqualTo("1e20")
    assertThat(fromBigInteger.isWhole).isTrue()
    assertThat(fromBigInteger.toBigInteger).isEqualTo(Some(new JBigInteger("100000000000000000000")))

    assertThat(fromBigDecimal.toString).isEqualTo("12345e-2")
    assertThat(fromBigDecimal.isWhole).isFalse()
    assertThat(fromBigDecimal.toBigDecimal).isEqualTo(Some(new JBigDecimal("123.45")))
    assertThat(fromBigDecimal.toBigInteger).isEqualTo(None)

    assertThat(fromLong.toLong).isEqualTo(Some(Long.MinValue))
    assertThat(fromLong.toString).isEqualTo(Long.MinValue.toString)

    assertThat(fromDouble.toString).isEqualTo("125e-2")
    assertThat(fromDouble.toDouble).isEqualTo(1.25d)

    assertThat(fromFloat.toString).isEqualTo("125e-1")
    assertThat(fromFloat.toFloat).isEqualTo(12.5f)
  }

  @Test
  def comparesEquivalentNonZeroValuesAcrossParsingAndConstructors(): Unit = {
    val reference: BiggerDecimal = parse("1000")
    val equivalentValues: List[BiggerDecimal] = List(
      parse("1e3"),
      parse("1000.000"),
      parse("10e2"),
      BiggerDecimal.fromBigInteger(new JBigInteger("1000")),
      BiggerDecimal.fromBigDecimal(new JBigDecimal("1000.000")),
      BiggerDecimal.fromLong(1000L),
      BiggerDecimal.fromDoubleUnsafe(1000.0d),
      BiggerDecimal.fromFloat(1000.0f)
    )

    equivalentValues.foreach { (number: BiggerDecimal) =>
      assertThat(number).isEqualTo(reference)
      assertThat(number.hashCode()).isEqualTo(reference.hashCode())
    }

    assertThat(parse("1000.1")).isNotEqualTo(reference)
    assertThat(BiggerDecimal.fromLong(1001L)).isNotEqualTo(reference)
  }

  @Test
  def detectsIntegralLongBoundaries(): Unit = {
    val validIntegrals: List[String] = List(
      "0",
      "1",
      "-1",
      "9223372036854775807",
      "-9223372036854775808"
    )
    val invalidIntegrals: List[String] = List(
      "9223372036854775808",
      "10000000000000000000",
      "-9223372036854775809",
      "-10000000000000000000"
    )

    validIntegrals.foreach { (value: String) =>
      assertThat(BiggerDecimal.integralIsValidLong(value)).isTrue()
      assertThat(parse(value).toLong.isDefined).isTrue()
    }

    invalidIntegrals.foreach { (value: String) =>
      assertThat(BiggerDecimal.integralIsValidLong(value)).isFalse()
      assertThat(parse(value).toLong).isEqualTo(None)
    }
  }

  @Test
  def limitsBigIntegerConversionByWholeNumberAndMaximumDigits(): Unit = {
    val whole: BiggerDecimal = parse("12345e2")
    val fractional: BiggerDecimal = parse("123.45")
    val hugeWhole: BiggerDecimal = parse("1e262145")

    assertThat(whole.isWhole).isTrue()
    assertThat(whole.toBigIntegerWithMaxDigits(JBigInteger.valueOf(6L))).isEqualTo(None)
    assertThat(whole.toBigIntegerWithMaxDigits(JBigInteger.valueOf(7L)))
      .isEqualTo(Some(new JBigInteger("1234500")))
    assertThat(whole.toBigInteger).isEqualTo(Some(new JBigInteger("1234500")))

    assertThat(fractional.isWhole).isFalse()
    assertThat(fractional.toBigInteger).isEqualTo(None)
    assertThat(fractional.toBigIntegerWithMaxDigits(JBigInteger.valueOf(100L))).isEqualTo(None)

    assertThat(hugeWhole.isWhole).isTrue()
    assertThat(hugeWhole.toBigInteger).isEqualTo(None)
    assertThat(hugeWhole.toBigIntegerWithMaxDigits(JBigInteger.valueOf(10L))).isEqualTo(None)
  }

  @Test
  def handlesValuesWhoseScaleCannotBeRepresentedByJavaBigDecimal(): Unit = {
    val hugePositiveExponent: BiggerDecimal = parse("1e2147483649")
    val hugeNegativeExponent: BiggerDecimal = parse("1e-2147483648")
    val hugeNegativeNumber: BiggerDecimal = parse("-1e2147483649")

    assertThat(hugePositiveExponent.toString).isEqualTo("1e2147483649")
    assertThat(hugePositiveExponent.toBigDecimal).isEqualTo(None)
    assertThat(hugePositiveExponent.toBigInteger).isEqualTo(None)
    assertThat(hugePositiveExponent.toDouble).isEqualTo(Double.PositiveInfinity)
    assertThat(hugePositiveExponent.toFloat).isEqualTo(Float.PositiveInfinity)

    assertThat(hugeNegativeExponent.toString).isEqualTo("1e-2147483648")
    assertThat(hugeNegativeExponent.toBigDecimal).isEqualTo(None)
    assertThat(hugeNegativeExponent.toBigInteger).isEqualTo(None)
    assertThat(hugeNegativeExponent.toDouble).isEqualTo(0.0d)
    assertThat(hugeNegativeExponent.toFloat).isEqualTo(0.0f)

    assertThat(hugeNegativeNumber.signum).isEqualTo(-1)
    assertThat(hugeNegativeNumber.toDouble).isEqualTo(Double.NegativeInfinity)
    assertThat(hugeNegativeNumber.toFloat).isEqualTo(Float.NegativeInfinity)
  }

  @Test
  def throwsForNonFiniteFloatingPointInputs(): Unit = {
    assertThrows(classOf[NumberFormatException], () => BiggerDecimal.fromDoubleUnsafe(Double.NaN))
    assertThrows(
      classOf[NumberFormatException],
      () => BiggerDecimal.fromDoubleUnsafe(Double.PositiveInfinity)
    )
    assertThrows(classOf[NumberFormatException], () => BiggerDecimal.fromFloat(Float.NaN))
    assertThrows(classOf[NumberFormatException], () => BiggerDecimal.fromFloat(Float.NegativeInfinity))
  }

  private def parse(input: String): BiggerDecimal = {
    val parsed: Option[BiggerDecimal] = BiggerDecimal.parseBiggerDecimal(input)

    assertTrue(parsed.isDefined, input)
    parsed.get
  }
}
