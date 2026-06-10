/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import sttp.model.{Method, StatusCode}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointOutput
import sttp.tapir.SchemaType
import sttp.tapir.*
import sttp.tapir.generic.auto.*

class Tapir_core_3Test {
  private final case class Book(id: Long, title: String, tags: List[String], available: Option[Boolean])

  private sealed trait ApiResult
  private final case class Created(id: String) extends ApiResult
  private final case class Queued(ticket: String) extends ApiResult

  @Test
  def endpointDslBuildsDocumentedSecureEndpoint(): Unit = {
    val endpointUnderTest = endpoint
      .name("find-books")
      .summary("Find books")
      .description("Searches the book catalogue")
      .tag("books")
      .securityIn(
        auth
          .bearer[String]()
          .description("OAuth bearer token")
          .securitySchemeName("bearerAuth")
      )
      .get
      .in("api" / "v1" / "books")
      .in(path[Long]("bookId").description("Book identifier").validate(Validator.min(1L)))
      .in(query[Option[Boolean]]("includeOutOfPrint").description("Include unavailable titles"))
      .in(header[Option[String]]("X-Correlation-Id"))
      .errorOut(statusCode.and(stringBody))
      .out(statusCode(StatusCode.Ok).and(stringBody).and(header[String]("ETag")))

    assertEquals(Some(Method.GET), endpointUnderTest.method)
    assertEquals(Some("find-books"), endpointUnderTest.info.name)
    assertEquals(Some("Find books"), endpointUnderTest.info.summary)
    assertEquals(Some("Searches the book catalogue"), endpointUnderTest.info.description)
    assertEquals(Vector("books"), endpointUnderTest.info.tags)
    assertEquals("[find-books]", endpointUnderTest.showShort)
    assertEquals(
      "/api/v1/books/{bookId}?includeOutOfPrint={includeOutOfPrint}",
      endpointUnderTest.showPathTemplate()
    )

    val shownEndpoint = endpointUnderTest.show
    assertTrue(shownEndpoint.contains("{header Authorization}"), shownEndpoint)
    assertTrue(shownEndpoint.contains("GET"), shownEndpoint)
    assertTrue(shownEndpoint.contains("/books"), shownEndpoint)
    assertTrue(shownEndpoint.contains("{header ETag}"), shownEndpoint)
    assertTrue(endpointUnderTest.showDetail.contains("securityin: auth(Bearer http"), endpointUnderTest.showDetail)
  }

  @Test
  def codecsDecodeEncodeAndValidateEndpointInputs(): Unit = {
    val pageInput = query[Int]("page").validate(Validator.inRange(1, 10))

    assertDecodeValue(3, pageInput.codec.decode(List("3")))
    assertEquals(List("7"), pageInput.codec.encode(7))
    assertEquals(DecodeResult.Missing, pageInput.codec.decode(Nil))
    assertMultipleDecode(List("1", "2"), pageInput.codec.decode(List("1", "2")))

    pageInput.codec.decode(List("0")) match {
      case DecodeResult.InvalidValue(errors) =>
        assertEquals(1, errors.size)
        assertEquals(0, errors.head.invalidValue)
      case other => fail(s"Expected a validation failure for an out-of-range page, got: $other")
    }

    pageInput.codec.decode(List("not-a-number")) match {
      case DecodeResult.Error(original, error) =>
        assertEquals("not-a-number", original)
        assertTrue(error.isInstanceOf[NumberFormatException], error.toString)
      case other => fail(s"Expected a parsing failure for a non-numeric page, got: $other")
    }

    final case class NonEmptyName(value: String)
    val nameCodec = summon[Codec[String, String, TextPlain]]
      .mapValidate(Validator.nonEmptyString[String])(NonEmptyName.apply)(_.value)

    assertDecodeValue(NonEmptyName("tapir"), nameCodec.decode("tapir"))
    assertEquals("core", nameCodec.encode(NonEmptyName("core")))
    assertTrue(nameCodec.decode("").isInstanceOf[DecodeResult.InvalidValue])
  }

  @Test
  def schemaDerivationExposesProductFieldsAndNestedValidators(): Unit = {
    val bookSchema = summon[Schema[Book]]
    val documentedSchema = bookSchema
      .modifyUnsafe[Long]("id")(_.description("Database id").validate(Validator.min(1L)))
      .modifyUnsafe[String]("title")(_.validate(Validator.minLength(2)))
      .modifyUnsafe[String]("tags", Schema.ModifyCollectionElements)(_.validate(Validator.nonEmptyString[String]))

    bookSchema.schemaType match {
      case product: SchemaType.SProduct[Book] =>
        assertEquals(List("id", "title", "tags", "available"), product.fields.map(_.name.name))
        assertEquals(List("id", "title"), product.required.map(_.name))
      case other => fail(s"Expected a product schema for Book, got: $other")
    }

    assertTrue(documentedSchema.applyValidation(Book(1L, "Tapir", List("core", "schema"), Some(true))).isEmpty)

    val validationErrors = documentedSchema.applyValidation(Book(0L, "T", List(""), None))
    assertEquals(3, validationErrors.size)
    assertTrue(validationErrors.exists(_.path.map(_.name).contains("id")), validationErrors.toString)
    assertTrue(validationErrors.exists(_.path.map(_.name).contains("title")), validationErrors.toString)
    assertTrue(validationErrors.exists(_.path.map(_.name).contains("tags")), validationErrors.toString)
    assertTrue(documentedSchema.showValidators.exists(_.contains("id")))
    assertTrue(documentedSchema.showValidators.exists(_.contains("title")))
  }

  @Test
  def oneOfOutputsAndContentNegotiatedBodiesKeepVariantMetadata(): Unit = {
    val createdOutput: EndpointOutput[Created] = stringBody.map(Created.apply)(_.id)
    val queuedOutput: EndpointOutput[Queued] = stringBody.map(Queued.apply)(_.ticket)
    val resultOutput: EndpointOutput[ApiResult] = oneOf[ApiResult](
      oneOfVariantValueMatcher(StatusCode.Created, createdOutput) { case Created(_) => true },
      oneOfVariantValueMatcher(StatusCode.Accepted, queuedOutput) { case Queued(_) => true }
    )

    val oneOfOutput = resultOutput.asInstanceOf[EndpointOutput.OneOf[ApiResult, ApiResult]]
    assertEquals(2, oneOfOutput.variants.size)
    assertTrue(oneOfOutput.variants.head.appliesTo(Created("book-1")))
    assertFalse(oneOfOutput.variants.head.appliesTo(Queued("ticket-1")))
    assertTrue(oneOfOutput.variants(1).appliesTo(Queued("ticket-2")))
    assertFalse(oneOfOutput.variants(1).appliesTo(Created("book-2")))
    assertTrue(oneOfOutput.show.contains("status code (201)"), oneOfOutput.show)
    assertTrue(oneOfOutput.show.contains("status code (202)"), oneOfOutput.show)

    val negotiatedBody: EndpointIO.OneOfBody[String, String] = oneOfBody(stringBody, htmlBodyUtf8)
    assertEquals(2, negotiatedBody.variants.size)
    assertTrue(negotiatedBody.show.contains("text/plain"), negotiatedBody.show)
    assertTrue(negotiatedBody.show.contains("text/html"), negotiatedBody.show)
  }

  private def assertDecodeValue[T](expected: T, actual: DecodeResult[T]): Unit = {
    actual match {
      case DecodeResult.Value(value) => assertEquals(expected, value)
      case other                     => fail(s"Expected decoded value $expected, got: $other")
    }
  }

  private def assertMultipleDecode[T](expected: Seq[T], actual: DecodeResult[?]): Unit = {
    actual match {
      case DecodeResult.Multiple(values) =>
        assertEquals(expected.map(String.valueOf).mkString("|"), values.map(String.valueOf).mkString("|"))
      case other                         => fail(s"Expected multiple decoded values $expected, got: $other")
    }
  }
}
