/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sangria_graphql.sangria_derivation_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import sangria.ast.{BooleanValue, Document, ListValue, ObjectValue, StringValue, Value}
import sangria.execution.Executor
import sangria.macros.derive.*
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput}
import sangria.marshalling.queryAst.queryAstResultMarshaller
import sangria.parser.QueryParser
import sangria.renderer.SchemaFilter
import sangria.schema.{Argument, Context, EnumType, Field, InputObjectType, IntType, ListType, ObjectType, Schema, StringType, fields}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class Sangria_derivation_3Test {
  private given ExecutionContext = ExecutionContext.global

  private sealed trait Genre

  private object Genre:
    case object SciFi extends Genre
    case object Fantasy extends Genre
    case object History extends Genre
    case object Hidden extends Genre

  private final case class Author(id: String, fullName: String, age: Option[Int], secret: String):
    def displayName(prefix: String, repeat: Int): String =
      Vector.fill(repeat)(s"$prefix$fullName").mkString(" | ")

  private final case class Book(title: String, pages: Int, genre: Genre, author: Author, internalCode: String)

  private final case class Recommendation(title: String):
    @GraphQLField
    def normalizedTitle: Future[String] = Future.successful(title.toUpperCase)

  private final case class BookFilter(
      term: String,
      minPages: Option[Int],
      genre: Option[Genre],
      ignored: String = "not exposed"
  )

  private final class ViewerCatalog(private val viewerName: String):
    @GraphQLField
    def greeting(@GraphQLDefault("Welcome") salutation: String): String =
      s"$salutation, $viewerName"

    @GraphQLField
    @GraphQLName("largeBookTitles")
    def booksWithAtLeast(minimumPages: Int)(context: Context[ViewerCatalogContext, Unit]): List[String] =
      context.ctx.availableBooks.filter(_.pages >= minimumPages).map(_.title).toList

  private final case class ViewerCatalogContext(viewerCatalog: ViewerCatalog, availableBooks: Vector[Book])

  private given GenreType: EnumType[Genre] = deriveEnumType[Genre](
    EnumTypeName("BookGenre"),
    EnumTypeDescription("A derived enum for catalog genres."),
    RenameValue("SciFi", "SCIENCE_FICTION"),
    DocumentValue("SciFi", "Speculative fiction with science themes."),
    DocumentValue("Fantasy", "Stories with magic or myths."),
    DeprecateValue("History", "Historical shelves are archived."),
    ExcludeValues("Hidden")
  )

  private given AuthorType: ObjectType[Unit, Author] = deriveObjectType[Unit, Author](
    ObjectTypeName("Writer"),
    ObjectTypeDescription("A derived object type for book authors."),
    RenameField("fullName", "name"),
    DocumentField("fullName", "The public author name."),
    DocumentField("age", "Known author age, when available."),
    DeprecateField("age", "Author age is no longer curated."),
    ExcludeFields("secret"),
    IncludeMethods("displayName"),
    MethodArgumentRename("displayName", "prefix", "withPrefix"),
    MethodArgumentDescription("displayName", "prefix", "Text prepended to the author name."),
    MethodArgumentDefault("displayName", "repeat", 1)
  )

  private given BookType: ObjectType[Unit, Book] = deriveObjectType[Unit, Book](
    ObjectTypeName("CatalogBook"),
    ObjectTypeDescription("A derived object type for catalog books."),
    ReplaceField("pages", Field("pageCount", IntType, resolve = _.value.pages)),
    AddFields(Field("summary", StringType, resolve = context => s"${context.value.title} by ${context.value.author.fullName}")),
    ExcludeFields("internalCode")
  )

  private given RecommendationType: ObjectType[Unit, Recommendation] = deriveObjectType[Unit, Recommendation](
    ObjectTypeName("Recommendation")
  )

  private given BookFilterInputType: InputObjectType[BookFilter] = deriveInputObjectType[BookFilter](
    InputObjectTypeName("BookFilterInput"),
    RenameInputField("minPages", "minimumPages"),
    DocumentInputField("term", "Case-insensitive title fragment."),
    DocumentInputField("genre", "Optional genre filter."),
    ExcludeInputFields("ignored")
  )

  private given BookFilterFromInput: FromInput[BookFilter] with
    override val marshaller: CoercedScalaResultMarshaller = CoercedScalaResultMarshaller.default

    override def fromResult(node: marshaller.Node): BookFilter =
      node match {
        case filter: BookFilter => filter
        case values: Map[?, ?] =>
          val fields: Map[String, Any] = values.asInstanceOf[Map[String, Any]]
          BookFilter(
            term = fields("term").asInstanceOf[String],
            minPages = optionalInt(fields.getOrElse("minPages", fields.getOrElse("minimumPages", None))),
            genre = optionalGenre(fields.getOrElse("genre", None))
          )
        case other => fail(s"Expected derived input object result, got $other")
      }

  private val FrankHerbert: Author = Author("a-1", "Frank Herbert", Some(65), "private-note")
  private val UrsulaLeGuin: Author = Author("a-2", "Ursula K. Le Guin", Some(88), "private-note")
  private val Catalog: Vector[Book] = Vector(
    Book("Dune", 412, Genre.SciFi, FrankHerbert, "sf-001"),
    Book("Children of Dune", 444, Genre.SciFi, FrankHerbert, "sf-002"),
    Book("A Wizard of Earthsea", 183, Genre.Fantasy, UrsulaLeGuin, "fn-001"),
    Book("A People's History", 729, Genre.History, FrankHerbert, "hi-001")
  )

  private val FilterArg: Argument[BookFilter] = Argument("filter", BookFilterInputType)

  private lazy val QueryType: ObjectType[Unit, Unit] = ObjectType(
    "Query",
    fields[Unit, Unit](
      Field(
        "books",
        ListType(BookType),
        arguments = FilterArg :: Nil,
        resolve = context => filterBooks(context.arg(FilterArg))
      ),
      Field("featuredAuthor", AuthorType, resolve = _ => FrankHerbert)
    )
  )

  private lazy val CatalogSchema: Schema[Unit, Unit] = Schema(QueryType)

  private lazy val RecommendationQueryType: ObjectType[Unit, Unit] = ObjectType(
    "RecommendationQuery",
    fields[Unit, Unit](
      Field(
        "recommendations",
        ListType(RecommendationType),
        resolve = _ => Vector(Recommendation("dune"), Recommendation("earthsea"))
      )
    )
  )

  private lazy val RecommendationSchema: Schema[Unit, Unit] = Schema(RecommendationQueryType)

  private lazy val ViewerCatalogQueryType: ObjectType[ViewerCatalogContext, Unit] =
    deriveContextObjectType[ViewerCatalogContext, ViewerCatalog, Unit](_.viewerCatalog)

  private lazy val ViewerCatalogSchema: Schema[ViewerCatalogContext, Unit] = Schema(ViewerCatalogQueryType)

  @Test
  def derivesObjectTypesWithRenamedReplacedAddedExcludedAndMethodFields(): Unit = {
    val result: ObjectValue = execute(
      """
        |{
        |  books(filter: {term: "dune", minimumPages: 400, genre: SCIENCE_FICTION}) {
        |    title
        |    pageCount
        |    genre
        |    summary
        |    author {
        |      id
        |      name
        |      age
        |      displayName(withPrefix: "Dr. ")
        |      repeatedName: displayName(withPrefix: "Dr. ", repeat: 2)
        |    }
        |  }
        |}
        |""".stripMargin
    )

    val books: Vector[Value] = listField(objectField(result, "data"), "books").values
    assertEquals(2, books.size)

    val firstBook: ObjectValue = books.head.asInstanceOf[ObjectValue]
    assertStringField(firstBook, "title", "Dune")
    assertIntField(firstBook, "pageCount", 412)
    assertStringField(firstBook, "genre", "SCIENCE_FICTION")
    assertStringField(firstBook, "summary", "Dune by Frank Herbert")
    assertFalse(firstBook.fieldsByName.contains("pages"))
    assertFalse(firstBook.fieldsByName.contains("internalCode"))

    val author: ObjectValue = objectField(firstBook, "author")
    assertStringField(author, "id", "a-1")
    assertStringField(author, "name", "Frank Herbert")
    assertIntField(author, "age", 65)
    assertStringField(author, "displayName", "Dr. Frank Herbert")
    assertStringField(author, "repeatedName", "Dr. Frank Herbert | Dr. Frank Herbert")
    assertFalse(author.fieldsByName.contains("fullName"))
    assertFalse(author.fieldsByName.contains("secret"))
  }

  @Test
  def derivesInputObjectsThatCoerceRenamedOptionalAndEnumFields(): Unit = {
    val result: ObjectValue = execute(
      """
        |{
        |  books(filter: {term: "earth", minimumPages: 100, genre: Fantasy}) {
        |    title
        |    pageCount
        |    genre
        |  }
        |}
        |""".stripMargin
    )

    val books: Vector[Value] = listField(objectField(result, "data"), "books").values
    assertEquals(1, books.size)

    val book: ObjectValue = books.head.asInstanceOf[ObjectValue]
    assertStringField(book, "title", "A Wizard of Earthsea")
    assertIntField(book, "pageCount", 183)
    assertStringField(book, "genre", "Fantasy")
  }

  @Test
  def derivesEnumTypesWithCustomNamesDescriptionsDeprecationAndExclusions(): Unit = {
    val result: ObjectValue = execute(
      """
        |{
        |  books(filter: {term: "dune", minimumPages: 400, genre: SCIENCE_FICTION}) {
        |    genre
        |  }
        |  __type(name: "BookGenre") {
        |    name
        |    description
        |    enumValues(includeDeprecated: true) {
        |      name
        |      description
        |      isDeprecated
        |      deprecationReason
        |    }
        |  }
        |}
        |""".stripMargin
    )

    val data: ObjectValue = objectField(result, "data")
    val books: Vector[Value] = listField(data, "books").values
    assertFalse(books.isEmpty)
    assertTrue(books.forall(book => stringField(book.asInstanceOf[ObjectValue], "genre") == "SCIENCE_FICTION"))

    val enumType: ObjectValue = objectField(data, "__type")
    assertStringField(enumType, "name", "BookGenre")
    assertStringField(enumType, "description", "A derived enum for catalog genres.")

    val enumValues: Vector[ObjectValue] = listField(enumType, "enumValues").values.map(_.asInstanceOf[ObjectValue])
    val names: Vector[String] = enumValues.map(value => stringField(value, "name"))
    assertEquals(Set("SCIENCE_FICTION", "Fantasy", "History"), names.toSet)
    assertEquals(3, names.size)
    assertFalse(names.contains("Hidden"))

    val sciFi: ObjectValue = enumValues.find(value => stringField(value, "name") == "SCIENCE_FICTION").getOrElse(fail("Missing SCIENCE_FICTION value"))
    assertStringField(sciFi, "description", "Speculative fiction with science themes.")
    assertBooleanField(sciFi, "isDeprecated", expected = false)

    val history: ObjectValue = enumValues.find(value => stringField(value, "name") == "History").getOrElse(fail("Missing History value"))
    assertBooleanField(history, "isDeprecated", expected = true)
    assertStringField(history, "deprecationReason", "Historical shelves are archived.")
  }

  @Test
  def derivesObjectMethodsReturningFutureValues(): Unit = {
    val result: ObjectValue = executeRecommendations(
      """
        |{
        |  recommendations {
        |    title
        |    normalizedTitle
        |  }
        |}
        |""".stripMargin
    )

    val recommendations: Vector[ObjectValue] =
      listField(objectField(result, "data"), "recommendations").values.map(_.asInstanceOf[ObjectValue])
    assertEquals(2, recommendations.size)
    assertStringField(recommendations.head, "title", "dune")
    assertStringField(recommendations.head, "normalizedTitle", "DUNE")
    assertStringField(recommendations(1), "title", "earthsea")
    assertStringField(recommendations(1), "normalizedTitle", "EARTHSEA")
  }

  @Test
  def derivesContextObjectTypesFromAUserContextValue(): Unit = {
    val result: ObjectValue = executeViewerCatalog(
      """
        |{
        |  greeting
        |  customGreeting: greeting(salutation: "Hello")
        |  largeBookTitles(minimumPages: 400)
        |}
        |""".stripMargin,
      ViewerCatalogContext(new ViewerCatalog("Ada"), Catalog)
    )

    val data: ObjectValue = objectField(result, "data")
    assertStringField(data, "greeting", "Welcome, Ada")
    assertStringField(data, "customGreeting", "Hello, Ada")
    assertEquals(Vector("Dune", "Children of Dune", "A People's History"), stringListField(data, "largeBookTitles"))
  }

  @Test
  def rendersDerivedSchemaMetadataForOutputAndInputTypes(): Unit = {
    val rendered: String = CatalogSchema.renderPretty(SchemaFilter.default)

    assertTrue(rendered.contains("type CatalogBook"))
    assertTrue(rendered.contains("pageCount: Int!"))
    assertTrue(rendered.contains("summary: String!"))
    assertTrue(rendered.contains("type Writer"))
    assertTrue(rendered.contains("name: String!"))
    assertTrue(rendered.contains("displayName("))
    assertTrue(rendered.contains("withPrefix: String!"))
    assertTrue(rendered.contains("repeat: Int"))
    assertTrue(rendered.contains("= 1"))
    assertTrue(rendered.contains("input BookFilterInput"))
    assertTrue(rendered.contains("minimumPages: Int"))
    assertTrue(rendered.contains("enum BookGenre"))
    assertFalse(rendered.contains("internalCode"))
    assertFalse(rendered.contains("secret"))
    assertFalse(rendered.contains("ignored"))
  }

  private def optionalInt(value: Any): Option[Int] =
    value match {
      case None | null => None
      case Some(inner) => optionalInt(inner)
      case intValue: Int => Some(intValue)
      case bigIntValue: BigInt => Some(bigIntValue.toInt)
      case longValue: Long => Some(longValue.toInt)
      case other => fail(s"Expected optional integer input value, got $other")
    }

  private def optionalGenre(value: Any): Option[Genre] =
    value match {
      case None | null => None
      case Some(inner) => optionalGenre(inner)
      case genre: Genre => Some(genre)
      case "SCIENCE_FICTION" | "SciFi" => Some(Genre.SciFi)
      case "Fantasy" => Some(Genre.Fantasy)
      case "History" => Some(Genre.History)
      case other => fail(s"Expected optional genre input value, got $other")
    }

  private def filterBooks(filter: BookFilter): Vector[Book] = {
    val loweredTerm: String = filter.term.toLowerCase
    Catalog.filter { book =>
      book.title.toLowerCase.contains(loweredTerm) &&
        filter.minPages.forall(book.pages >= _) &&
        filter.genre.forall(_ == book.genre)
    }
  }

  private def execute(query: String): ObjectValue = {
    val document: Document = QueryParser.parse(query).get
    val result: Value = Await.result(Executor.execute(CatalogSchema, document), 5.seconds)

    result match {
      case objectValue: ObjectValue => objectValue
      case other => fail(s"Expected GraphQL execution result object, got $other")
    }
  }

  private def executeRecommendations(query: String): ObjectValue = {
    val document: Document = QueryParser.parse(query).get
    val result: Value = Await.result(Executor.execute(RecommendationSchema, document), 5.seconds)

    result match {
      case objectValue: ObjectValue => objectValue
      case other => fail(s"Expected GraphQL execution result object, got $other")
    }
  }

  private def executeViewerCatalog(query: String, context: ViewerCatalogContext): ObjectValue = {
    val document: Document = QueryParser.parse(query).get
    val result: Value = Await.result(Executor.execute(ViewerCatalogSchema, document, userContext = context), 5.seconds)

    result match {
      case objectValue: ObjectValue => objectValue
      case other => fail(s"Expected GraphQL execution result object, got $other")
    }
  }

  private def objectField(value: ObjectValue, fieldName: String): ObjectValue =
    value.fieldsByName(fieldName) match {
      case objectValue: ObjectValue => objectValue
      case other => fail(s"Expected field '$fieldName' to be an object, got $other")
    }

  private def listField(value: ObjectValue, fieldName: String): ListValue =
    value.fieldsByName(fieldName) match {
      case listValue: ListValue => listValue
      case other => fail(s"Expected field '$fieldName' to be a list, got $other")
    }

  private def assertStringField(value: ObjectValue, fieldName: String, expected: String): Unit =
    assertEquals(expected, stringField(value, fieldName))

  private def stringListField(value: ObjectValue, fieldName: String): Vector[String] =
    listField(value, fieldName).values.map {
      case stringValue: StringValue => stringValue.value
      case other => fail(s"Expected field '$fieldName' to contain string values, got $other")
    }

  private def stringField(value: ObjectValue, fieldName: String): String =
    value.fieldsByName(fieldName) match {
      case stringValue: StringValue => stringValue.value
      case enumValue: sangria.ast.EnumValue => enumValue.value
      case other => fail(s"Expected field '$fieldName' to be a string-like value, got $other")
    }

  private def assertIntField(value: ObjectValue, fieldName: String, expected: Int): Unit =
    value.fieldsByName(fieldName) match {
      case intValue: sangria.ast.IntValue => assertEquals(BigInt(expected), intValue.value)
      case other => fail(s"Expected field '$fieldName' to be an int value, got $other")
    }

  private def assertBooleanField(value: ObjectValue, fieldName: String, expected: Boolean): Unit =
    value.fieldsByName(fieldName) match {
      case booleanValue: BooleanValue => assertEquals(expected, booleanValue.value)
      case other => fail(s"Expected field '$fieldName' to be a boolean value, got $other")
    }
}
