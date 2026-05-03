/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package eu_timepit.refined_3

import eu.timepit.refined.api.RefType
import eu.timepit.refined.api.RefType.ops.*
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.Validate
import eu.timepit.refined.boolean.{AllOf, And, AnyOf, Not, Or, Xor}
import eu.timepit.refined.char.{Digit, Letter, LetterOrDigit, LowerCase, UpperCase, Whitespace}
import eu.timepit.refined.collection.{Contains, Count, Empty, Exists, Forall, Head, Index, Last, MaxSize, MinSize, NonEmpty, Size}
import eu.timepit.refined.generic.Equal
import eu.timepit.refined.numeric.{Divisible, Even, Greater, GreaterEqual, Interval, Less, LessEqual, Modulo, Negative, NonNaN, NonNegative, Odd, Positive}
import eu.timepit.refined.refineV
import eu.timepit.refined.string.{EndsWith, IPv4, IPv6, MatchesRegex, Regex, StartsWith, Trimmed, Uri, Url, Uuid, ValidBigDecimal, ValidBigInt, ValidByte, ValidDouble, ValidFloat, ValidInt, ValidLong, ValidShort, XPath}
import eu.timepit.refined.types.{char as charTypes, digests as digestTypes, net as netTypes, numeric as numericTypes, string as stringTypes, time as timeTypes}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class Refined_3Test {
  @Test
  def refinedTypeCompanionsValidateNumericAliasesAndExposeBounds(): Unit = {
    val positiveInt: numericTypes.PosInt = expectRight(numericTypes.PosInt.from(42))
    assertThat(positiveInt.value).isEqualTo(42)
    assertThat(numericTypes.PosInt.MinValue.value).isEqualTo(1)
    assertThat(numericTypes.PosInt.MaxValue.value).isEqualTo(Int.MaxValue)

    val negativeLong: numericTypes.NegLong = expectRight(numericTypes.NegLong.from(-100L))
    assertThat(negativeLong.value).isEqualTo(-100L)
    assertThat(numericTypes.NonNegBigInt.unsafeFrom(BigInt("12345678901234567890")).value).isEqualTo(BigInt("12345678901234567890"))
    assertThat(numericTypes.NonPosBigDecimal.unsafeFrom(BigDecimal("0.00")).value).isEqualTo(BigDecimal("0.00"))
    assertThat(expectRight(numericTypes.NonNaNDouble.from(1.25)).value).isEqualTo(1.25)

    assertThat(numericTypes.PosInt.from(0).left.toOption.get).contains("Predicate failed")
    assertThat(numericTypes.NonNaNFloat.from(Float.NaN).isLeft).isTrue
    val exception = assertThrows(classOf[IllegalArgumentException], () => numericTypes.NegInt.unsafeFrom(1))
    assertThat(exception.getMessage).contains("Predicate failed")
  }

  @Test
  def refineVSupportsPrimitiveNumericPredicatesAndBooleanCombinations(): Unit = {
    type SmallPositive = Positive And LessEqual[10]
    type OutsideMiddle = Less[0] Or Greater[100]
    type NotSeven = Not[Equal[7]]

    assertThat(expectRight(refineV[SmallPositive](10)).value).isEqualTo(10)
    assertThat(expectRight(refineV[Interval.Open[0, 10]](5)).value).isEqualTo(5)
    assertThat(expectRight(refineV[Interval.ClosedOpen[0, 10]](0)).value).isEqualTo(0)
    assertThat(expectRight(refineV[OutsideMiddle](101)).value).isEqualTo(101)
    assertThat(expectRight(refineV[NotSeven](6)).value).isEqualTo(6)
    assertThat(expectRight(refineV[Even](24)).value).isEqualTo(24)
    assertThat(expectRight(refineV[Odd](25)).value).isEqualTo(25)
    assertThat(expectRight(refineV[Divisible[5]](35)).value).isEqualTo(35)
    assertThat(expectRight(refineV[Modulo[4, 1]](9)).value).isEqualTo(9)
    assertThat(expectRight(refineV[NonNegative](0)).value).isEqualTo(0)
    assertThat(expectRight(refineV[Negative](-1)).value).isEqualTo(-1)
    assertThat(expectRight(refineV[NonNaN](Double.PositiveInfinity)).value).isEqualTo(Double.PositiveInfinity)

    assertThat(expectLeft(refineV[SmallPositive](11))).contains("Right predicate")
    assertThat(refineV[Interval.Open[0, 10]](10).isLeft).isTrue
    assertThat(refineV[Odd](8).isLeft).isTrue
  }

  @Test
  def nAryAndExclusiveBooleanPredicatesValidateCompositeRules(): Unit = {
    type PositiveOddUnderTen = AllOf[(Positive, Odd, Less[10])]
    type OutsideOrBoundary = AnyOf[(Less[0], Equal[0], Greater[100])]
    type PositiveXorEven = Xor[Positive, Even]

    assertThat(expectRight(refineV[PositiveOddUnderTen](7)).value).isEqualTo(7)
    assertThat(refineV[PositiveOddUnderTen](8).isLeft).isTrue
    assertThat(refineV[PositiveOddUnderTen](11).isLeft).isTrue

    assertThat(expectRight(refineV[OutsideOrBoundary](-1)).value).isEqualTo(-1)
    assertThat(expectRight(refineV[OutsideOrBoundary](0)).value).isEqualTo(0)
    assertThat(expectRight(refineV[OutsideOrBoundary](101)).value).isEqualTo(101)
    assertThat(refineV[OutsideOrBoundary](50).isLeft).isTrue

    assertThat(expectRight(refineV[PositiveXorEven](3)).value).isEqualTo(3)
    assertThat(expectRight(refineV[PositiveXorEven](-4)).value).isEqualTo(-4)
    assertThat(refineV[PositiveXorEven](4).isLeft).isTrue
    assertThat(refineV[PositiveXorEven](-3).isLeft).isTrue
  }

  @Test
  def stringPredicatesCoverParsingNetworkRegexAndTrimmingChecks(): Unit = {
    assertThat(expectRight(refineV[StartsWith["graal"]]("graalvm-native")).value).isEqualTo("graalvm-native")
    assertThat(expectRight(refineV[EndsWith[".org"]]("example.org")).value).isEqualTo("example.org")
    assertThat(expectRight(refineV[MatchesRegex["[a-z]+-[0-9]+"]]("build-21")).value).isEqualTo("build-21")
    assertThat(expectRight(refineV[Regex]("[a-z]+-[0-9]+"))).isEqualTo("[a-z]+-[0-9]+")
    assertThat(expectRight(refineV[Trimmed]("no surrounding whitespace")).value).isEqualTo("no surrounding whitespace")

    assertThat(expectRight(refineV[IPv4]("192.168.10.20")).value).isEqualTo("192.168.10.20")
    assertThat(expectRight(refineV[IPv6]("2001:db8::1")).value).isEqualTo("2001:db8::1")
    assertThat(expectRight(refineV[Uri]("urn:isbn:0451450523")).value).isEqualTo("urn:isbn:0451450523")
    assertThat(expectRight(refineV[Url]("https://example.org/index.html")).value).isEqualTo("https://example.org/index.html")
    assertThat(expectRight(refineV[Uuid]("123e4567-e89b-12d3-a456-426614174000")).value).isEqualTo("123e4567-e89b-12d3-a456-426614174000")
    assertThat(expectRight(refineV[XPath]("/library/book[1]/title")).value).isEqualTo("/library/book[1]/title")

    assertThat(expectRight(refineV[ValidByte]("127")).value).isEqualTo("127")
    assertThat(expectRight(refineV[ValidShort]("32000")).value).isEqualTo("32000")
    assertThat(expectRight(refineV[ValidInt]("2147483647")).value).isEqualTo("2147483647")
    assertThat(expectRight(refineV[ValidLong]("9223372036854775807")).value).isEqualTo("9223372036854775807")
    assertThat(expectRight(refineV[ValidFloat]("3.5")).value).isEqualTo("3.5")
    assertThat(expectRight(refineV[ValidDouble]("2.25")).value).isEqualTo("2.25")
    assertThat(expectRight(refineV[ValidBigInt]("123456789012345678901234567890")).value).isEqualTo("123456789012345678901234567890")
    assertThat(expectRight(refineV[ValidBigDecimal]("12345.67890")).value).isEqualTo("12345.67890")

    assertThat(refineV[IPv4]("999.1.1.1").isLeft).isTrue
    assertThat(refineV[Url]("not a url").isLeft).isTrue
    assertThat(refineV[XPath]("//*[" ).isLeft).isTrue
    assertThat(expectLeft(refineV[Trimmed](" padded"))).contains("Predicate failed")
  }

  @Test
  def collectionPredicatesValidateIterableAndStringStructure(): Unit = {
    assertThat(expectRight(refineV[NonEmpty](List("native", "image"))).value).isEqualTo(List("native", "image"))
    assertThat(expectRight(refineV[Empty](Vector.empty[Int])).value).isEqualTo(Vector.empty[Int])
    assertThat(expectRight(refineV[Forall[Positive]](List(1, 2, 3))).value).isEqualTo(List(1, 2, 3))
    assertThat(expectRight(refineV[Exists[Equal["admin"]]](List("guest", "admin"))).value).isEqualTo(List("guest", "admin"))
    assertThat(expectRight(refineV[Contains[2]](List(1, 2, 3))).value).isEqualTo(List(1, 2, 3))
    assertThat(expectRight(refineV[Count[Positive, Equal[2]]](List(-1, 4, 5))).value).isEqualTo(List(-1, 4, 5))
    assertThat(expectRight(refineV[Head[StartsWith["prod"]]](List("prod-a", "stage-a"))).value).isEqualTo(List("prod-a", "stage-a"))
    assertThat(expectRight(refineV[Index[1, EndsWith[".conf"]]](Vector("app.txt", "native.conf"))).value).isEqualTo(Vector("app.txt", "native.conf"))
    assertThat(expectRight(refineV[Last[Greater[10]]](List(2, 5, 13))).value).isEqualTo(List(2, 5, 13))
    assertThat(expectRight(refineV[Size[Interval.Closed[3, 8]]]("refined")).value).isEqualTo("refined")
    assertThat(expectRight(refineV[MinSize[2]](Array(1, 2, 3))).value).containsExactly(1, 2, 3)
    assertThat(expectRight(refineV[MaxSize[4]]("tiny")).value).isEqualTo("tiny")

    assertThat(refineV[Forall[Positive]](List(1, -2, 3)).isLeft).isTrue
    assertThat(refineV[Head[Positive]](List.empty[Int]).isLeft).isTrue
    assertThat(refineV[Index[2, Equal["missing"]]](Vector("zero", "one")).isLeft).isTrue
    assertThat(refineV[Size[Equal[4]]]("five!").isLeft).isTrue
  }

  @Test
  def refinedStringCompanionsProvideSafeConstructorsAndHelpers(): Unit = {
    val nonEmpty: stringTypes.NonEmptyString = expectRight(stringTypes.NonEmptyString.from("metadata"))
    assertThat(nonEmpty.value).isEqualTo("metadata")
    assertThat(stringTypes.NonEmptyString.unapply("metadata").map(_.value)).isEqualTo(Some("metadata"))
    assertThat(stringTypes.NonEmptyString.unapply("")).isEqualTo(None)

    val finite = stringTypes.FiniteString[5]
    assertThat(finite.maxLength).isEqualTo(5)
    assertThat(expectRight(finite.from("abcde")).value).isEqualTo("abcde")
    assertThat(finite.from("abcdef").isLeft).isTrue
    assertThat(finite.truncate("abcdefg").value).isEqualTo("abcde")

    val nonEmptyFinite = stringTypes.NonEmptyFiniteString[3]
    assertThat(nonEmptyFinite.truncate("abcdef").map(_.value)).isEqualTo(Some("abc"))
    assertThat(nonEmptyFinite.truncate("")).isEqualTo(None)
    assertThat(nonEmptyFinite.truncateNes(nonEmpty).value).isEqualTo("met")

    assertThat(stringTypes.TrimmedString.trim("  native image  ").value).isEqualTo("native image")
    assertThat(expectRight(stringTypes.HexString.from("0A1B2C")).value).isEqualTo("0A1B2C")
    assertThat(stringTypes.HexString.from("xyz").isLeft).isTrue
  }

  @Test
  def refinedTypeAliasesForNetworkTimeCharactersAndDigestsValidateDomainRanges(): Unit = {
    assertThat(expectRight(netTypes.PortNumber.from(0)).value).isEqualTo(0)
    assertThat(expectRight(netTypes.SystemPortNumber.from(1023)).value).isEqualTo(1023)
    assertThat(expectRight(netTypes.UserPortNumber.from(8080)).value).isEqualTo(8080)
    assertThat(expectRight(netTypes.DynamicPortNumber.from(49152)).value).isEqualTo(49152)
    assertThat(expectRight(netTypes.NonSystemPortNumber.from(65535)).value).isEqualTo(65535)
    assertThat(netTypes.PortNumber.from(65536).isLeft).isTrue
    assertThat(expectRight(RefType.applyRef[netTypes.PrivateNetwork]("10.1.2.3")).value).isEqualTo("10.1.2.3")
    assertThat(expectRight(RefType.applyRef[netTypes.Rfc5737Testnet]("203.0.113.42")).value).isEqualTo("203.0.113.42")
    assertThat(RefType.applyRef[netTypes.PrivateNetwork]("8.8.8.8").isLeft).isTrue

    assertThat(expectRight(timeTypes.Month.from(12)).value).isEqualTo(12)
    assertThat(expectRight(timeTypes.Day.from(31)).value).isEqualTo(31)
    assertThat(expectRight(timeTypes.Hour.from(23)).value).isEqualTo(23)
    assertThat(expectRight(timeTypes.Minute.from(59)).value).isEqualTo(59)
    assertThat(expectRight(timeTypes.Second.from(0)).value).isEqualTo(0)
    assertThat(expectRight(timeTypes.Millis.from(999)).value).isEqualTo(999)
    assertThat(timeTypes.Month.from(13).isLeft).isTrue

    assertThat(expectRight(charTypes.LowerCaseChar.from('a')).value).isEqualTo('a')
    assertThat(expectRight(charTypes.UpperCaseChar.from('Z')).value).isEqualTo('Z')
    assertThat(expectRight(refineV[Digit]('7')).value).isEqualTo('7')
    assertThat(expectRight(refineV[Letter]('λ')).value).isEqualTo('λ')
    assertThat(expectRight(refineV[LetterOrDigit]('9')).value).isEqualTo('9')
    assertThat(expectRight(refineV[Whitespace]('\n')).value).isEqualTo('\n')
    assertThat(refineV[UpperCase]('z').isLeft).isTrue
    assertThat(refineV[LowerCase]('Z').isLeft).isTrue

    assertThat(expectRight(digestTypes.MD5.from("0123456789abcdef0123456789abcdef")).value).isEqualTo("0123456789abcdef0123456789abcdef")
    assertThat(expectRight(digestTypes.SHA1.from("0123456789abcdef0123456789abcdef01234567")).value).isEqualTo("0123456789abcdef0123456789abcdef01234567")
    assertThat(digestTypes.SHA256.from("not-hex").isLeft).isTrue
  }

  @Test
  def autoUnwrapProvidesImplicitAccessToUnderlyingValues(): Unit = {
    import eu.timepit.refined.auto.autoUnwrap
    import scala.language.implicitConversions

    def addOne(value: Int): Int = value + 1
    def hasConfigurationSuffix(value: String): Boolean = value.endsWith(".conf")

    val positive: numericTypes.PosInt = expectRight(numericTypes.PosInt.from(41))
    val configurationPath: String Refined EndsWith[".conf"] = expectRight(refineV[EndsWith[".conf"]]("native-image.conf"))

    assertThat(addOne(positive)).isEqualTo(42)
    assertThat(hasConfigurationSuffix(configurationPath)).isTrue
  }

  @Test
  def lowLevelPublicApiSupportsRefTypeOpsPatternMatchingAndValidationInspection(): Unit = {
    val refined: Int Refined Positive = expectRight(refineV[Positive](3))
    val Refined(unwrapped) = refined
    assertThat(unwrapped).isEqualTo(3)

    val refType = RefType[Refined]
    val rewrapped: Int Refined GreaterEqual[0] = refType.unsafeRewrap[Int, Positive, GreaterEqual[0]](refined)
    assertThat(refType.unwrap(rewrapped)).isEqualTo(3)
    assertThat(refined.unwrap).isEqualTo(3)
    assertThat(expectRight(refined.mapRefine(_ + 1)).value).isEqualTo(4)
    assertThat(expectRight(refined.coflatMapRefine(_.value * 2)).value).isEqualTo(6)
    assertThat(refined.mapRefine(_ => -1).isLeft).isTrue

    val positiveValidator = Validate[Int, Positive]
    assertThat(positiveValidator.isValid(1)).isTrue
    assertThat(positiveValidator.notValid(0)).isTrue
    assertThat(positiveValidator.showExpr(5)).contains("5")
    assertThat(positiveValidator.showResult(0, positiveValidator.validate(0))).contains("Predicate failed")
  }

  private def expectRight[A](actual: Either[String, A]): A =
    actual.fold(message => throw new AssertionError(s"Expected Right but got Left($message)"), identity)

  private def expectLeft[A](actual: Either[String, A]): String =
    actual.fold(identity, value => throw new AssertionError(s"Expected Left but got Right($value)"))
}
