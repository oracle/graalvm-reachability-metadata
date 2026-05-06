/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_engine_2_13

import io.getquill.CamelCase
import io.getquill.Escape
import io.getquill.H2Dialect
import io.getquill.IdiomContext
import io.getquill.Literal
import io.getquill.MirrorIdiom
import io.getquill.MirrorSqlDialect
import io.getquill.MysqlEscape
import io.getquill.NamingStrategy
import io.getquill.PluralizedTableNames
import io.getquill.PostgresDialect
import io.getquill.PostgresEscape
import io.getquill.SnakeCase
import io.getquill.UpperCase
import io.getquill.ast.Aggregation
import io.getquill.ast.AggregationOperator
import io.getquill.ast.Assignment
import io.getquill.ast.BinaryOperation
import io.getquill.ast.Constant
import io.getquill.ast.Delete
import io.getquill.ast.Entity
import io.getquill.ast.EqualityOperator
import io.getquill.ast.Filter
import io.getquill.ast.Ident
import io.getquill.ast.If
import io.getquill.ast.Infix
import io.getquill.ast.InnerJoin
import io.getquill.ast.Insert
import io.getquill.ast.Join
import io.getquill.ast.Map
import io.getquill.ast.NumericOperator
import io.getquill.ast.Property
import io.getquill.ast.PropertyAlias
import io.getquill.ast.QuotedReference
import io.getquill.ast.Renameable
import io.getquill.ast.ScalarTag
import io.getquill.ast.SortBy
import io.getquill.ast.Tuple
import io.getquill.ast.Update
import io.getquill.ast.Visibility
import io.getquill.context.ExecutionType
import io.getquill.norm.TranspileConfig
import io.getquill.quat.Quat
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test

class Quill_engine_2_13Test {
  private val personQuat: Quat.Product = Quat.Product(
    "Person",
    "id" -> Quat.Value,
    "firstName" -> Quat.Value,
    "age" -> Quat.Value,
    "active" -> Quat.BooleanValue
  )

  private val addressQuat: Quat.Product = Quat.Product(
    "Address",
    "id" -> Quat.Value,
    "personId" -> Quat.Value,
    "city" -> Quat.Value
  )

  private def person: Entity = Entity("Person", Nil, personQuat)

  private def address: Entity = Entity("Address", Nil, addressQuat)

  private def translateSql(ast: io.getquill.ast.Ast, naming: NamingStrategy = Literal): String = {
    implicit val implicitNaming: NamingStrategy = naming
    val context: IdiomContext = IdiomContext(
      TranspileConfig.Empty,
      IdiomContext.QueryType.discoverFromAst(ast, batchAlias = None)
    )
    MirrorSqlDialect.translate(ast, ast.quat, ExecutionType.Static, context)._2.toString
  }

  @Test
  def namingStrategiesTransformTableAndColumnNamesInOrder(): Unit = {
    val snakeThenUpper: NamingStrategy = NamingStrategy(SnakeCase, UpperCase)
    assertThat(snakeThenUpper.default("firstName"))
      .isEqualTo("FIRST_NAME")

    val pluralizedAndEscaped: NamingStrategy = NamingStrategy(PluralizedTableNames, Escape)
    assertThat(pluralizedAndEscaped.table("Person"))
      .isEqualTo("\"Persons\"")
    assertThat(pluralizedAndEscaped.column("firstName"))
      .isEqualTo("\"firstName\"")

    assertThat(CamelCase.default("first_name"))
      .isEqualTo("firstName")
    assertThat(PostgresEscape.column("$1"))
      .isEqualTo("$1")
    assertThat(PostgresEscape.column("firstName"))
      .isEqualTo("\"firstName\"")
    assertThat(MysqlEscape.table("Person"))
      .isEqualTo("`Person`")
    assertThat(MysqlEscape.default("Person"))
      .isEqualTo("Person")
  }

  @Test
  def quatsSupportLookupRenamingLeastUpperTypeAndSerializationRoundTrip(): Unit = {
    val nestedQuat: Quat.Product = Quat.Product(
      "Contact",
      "person" -> personQuat,
      "email" -> Quat.Value
    )

    assertThat(nestedQuat.lookup(List("person", "age"), failNonExist = true))
      .isEqualTo(Quat.Value)
    assertThat(nestedQuat.countFields)
      .isEqualTo(7)

    val renamed: Quat = personQuat.withRenames(List("firstName" -> "first_name", "active" -> "is_active"))
    assertThat(renamed.renames.toList)
      .isEqualTo(List("firstName" -> "first_name", "active" -> "is_active"))
    assertThat(renamed.applyRenames.asInstanceOf[Quat.Product].fields.keys.toList)
      .isEqualTo(List("id", "first_name", "age", "is_active"))

    val compatible: Quat.Product = Quat.Product(
      "PersonProjection",
      "id" -> Quat.Value,
      "firstName" -> Quat.Value,
      "age" -> Quat.Value,
      "active" -> Quat.BooleanExpression
    )
    assertThat(personQuat.leastUpperType(compatible))
      .isEqualTo(Some(personQuat))
    assertThat(Quat.fromSerialized(nestedQuat.serialize))
      .isEqualTo(nestedQuat)

    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = nestedQuat.lookup("missing", failNonExist = true)
    }).isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("missing")
  }

  @Test
  def entityPropertyAliasesSynchronizeWithProductQuats(): Unit = {
    val aliasedEntity: Entity = Entity.Opinionated(
      "people",
      List(
        PropertyAlias(List("firstName"), "first_name"),
        PropertyAlias(List("active"), "is_active")
      ),
      personQuat,
      Renameable.Fixed
    )

    val synced: Entity = aliasedEntity.syncToQuat
    assertThat(synced.renameable)
      .isEqualTo(Renameable.Fixed)
    assertThat(synced.quat.renames.toList)
      .isEqualTo(List("firstName" -> "first_name", "active" -> "is_active"))

    val personIdent: Ident = Ident("p", synced.quat)
    val fixedProperty: Property = Property.Opinionated(
      personIdent,
      "first_name",
      Renameable.Fixed,
      Visibility.Visible
    )
    assertThat(fixedProperty.prevName)
      .isEqualTo(Some("first_name"))
    assertThat(fixedProperty.copy(name = "is_active").renameable)
      .isEqualTo(Renameable.Fixed)
  }

  @Test
  def astNodesPreserveQuatsEqualityAndCopies(): Unit = {
    val id: Ident = Ident("p", personQuat)
    val hiddenId: Ident = Ident.Opinionated("p", personQuat, Visibility.Hidden)
    val copiedId: Ident = hiddenId.copy(name = "person")

    assertThat(id)
      .isEqualTo(hiddenId)
    assertThat(copiedId.visibility)
      .isEqualTo(Visibility.Hidden)
    assertThat(copiedId.quat)
      .isEqualTo(personQuat)

    val generatedRecord: io.getquill.ast.CaseClass = io.getquill.ast.CaseClass(
      "GeneratedAtCompileTime",
      List("name" -> Property(id, "firstName"), "age" -> Property(id, "age"))
    )
    val sameShapeRecord: io.getquill.ast.CaseClass = io.getquill.ast.CaseClass(
      "DifferentRuntimeName",
      List("name" -> Property(id, "firstName"), "age" -> Property(id, "age"))
    )
    assertThat(generatedRecord)
      .isEqualTo(sameShapeRecord)
    assertThat(generatedRecord.quat.shortString)
      .isEqualTo("GeneratedAtCompileTime(name:V,age:V)")

    val transparentInfix: Infix = Infix(
      List("BOOL_OR(", ")"),
      List(Property(id, "active")),
      pure = true,
      transparent = true,
      Quat.Generic
    )
    val improved: io.getquill.ast.Ast = Quat.improveInfixQuat(transparentInfix)
    assertThat(improved.quat)
      .isEqualTo(Quat.BooleanValue)
    assertThat(transparentInfix.copy(pure = false).pure)
      .isFalse()
  }

  @Test
  def mirrorIdiomRendersNormalizedAstShape(): Unit = {
    implicit val naming: NamingStrategy = Literal
    val p: Ident = Ident("p", personQuat)
    val ast: Map = Map(
      Filter(
        person,
        p,
        BinaryOperation(Property(p, "active"), EqualityOperator.`_==`, Constant.auto(true))
      ),
      p,
      Tuple(List(Property(p, "firstName"), Property(p, "age")))
    )
    val context: IdiomContext = IdiomContext(TranspileConfig.Empty, IdiomContext.QueryType.Select)

    val statement: String = MirrorIdiom.translate(ast, ast.quat, ExecutionType.Dynamic, context)._2.toString

    assertThat(statement)
      .isEqualTo("querySchema(\"Person\").filter(p => p.active == true).map(p => (p.firstName, p.age))")
  }

  @Test
  def mirrorSqlDialectTranslatesSelectFilterSortAndAggregation(): Unit = {
    val p: Ident = Ident("p", personQuat)
    val adultPeople: Filter = Filter(
      person,
      p,
      BinaryOperation(Property(p, "age"), NumericOperator.`>`, Constant.auto(21))
    )
    val sortedNames: Map = Map(
      SortBy(adultPeople, p, Property(p, "firstName"), io.getquill.ast.Asc),
      p,
      Tuple(List(Property(p, "firstName"), Property(p, "age")))
    )

    assertThat(translateSql(sortedNames))
      .isEqualTo("SELECT p.firstName AS _1, p.age AS _2 FROM Person p WHERE p.age > 21 ORDER BY p.firstName ASC")

    val countAdults: Aggregation = Aggregation(AggregationOperator.size, adultPeople)
    assertThat(translateSql(countAdults))
      .isEqualTo("SELECT COUNT(p.*) FROM Person p WHERE p.age > 21")
  }

  @Test
  def mirrorDialectsRenderInnerJoinsWithBothSidesAvailable(): Unit = {
    implicit val naming: NamingStrategy = Literal
    val p: Ident = Ident("p", personQuat)
    val a: Ident = Ident("a", addressQuat)
    val join: Join = Join(
      InnerJoin,
      person,
      address,
      p,
      a,
      BinaryOperation(Property(p, "id"), EqualityOperator.`_==`, Property(a, "personId"))
    )
    val context: IdiomContext = IdiomContext(TranspileConfig.Empty, IdiomContext.QueryType.Select)

    assertThat(MirrorIdiom.translate(join, join.quat, ExecutionType.Static, context)._2.toString)
      .isEqualTo("""querySchema("Person").join(querySchema("Address")).on((p, a) => p.id == a.personId)""")

    val sql: String = translateSql(join)
    assertThat(sql)
      .contains("FROM Person p INNER JOIN Address a ON p.id = a.personId")
    assertThat(sql)
      .contains("p.firstName")
      .contains("a.city")
  }

  @Test
  def mirrorSqlDialectTranslatesConditionalExpressionsToCaseStatements(): Unit = {
    val p: Ident = Ident("p", personQuat)
    val ageGroup: If = If(
      BinaryOperation(Property(p, "age"), NumericOperator.`<`, Constant.auto(18)),
      Constant.auto("minor"),
      If(
        BinaryOperation(Property(p, "age"), NumericOperator.`>=`, Constant.auto(65)),
        Constant.auto("senior"),
        Constant.auto("adult")
      )
    )
    val query: Map = Map(person, p, ageGroup)

    assertThat(translateSql(query))
      .isEqualTo(
        "SELECT CASE WHEN p.age < 18 THEN 'minor' WHEN p.age >= 65 THEN 'senior' ELSE 'adult' END FROM Person p"
      )
  }

  @Test
  def mirrorSqlDialectTranslatesInsertUpdateAndDeleteActions(): Unit = {
    val p: Ident = Ident("p", personQuat)
    val insert: Insert = Insert(
      person,
      List(
        Assignment(p, Property(p, "firstName"), Constant.auto("Ada")),
        Assignment(p, Property(p, "age"), Constant.auto(36)),
        Assignment(p, Property(p, "active"), Constant.auto(true))
      )
    )
    assertThat(translateSql(insert))
      .isEqualTo("INSERT INTO Person (firstName,age,active) VALUES ('Ada', 36, true)")

    val seniorPeople: Filter = Filter(
      person,
      p,
      BinaryOperation(Property(p, "age"), NumericOperator.`>=`, Constant.auto(65))
    )
    val update: Update = Update(
      seniorPeople,
      List(Assignment(p, Property(p, "active"), Constant.auto(false)))
    )
    assertThat(translateSql(update))
      .isEqualTo("UPDATE Person AS p SET active = false WHERE p.age >= 65")

    val delete: Delete = Delete(
      Filter(person, p, BinaryOperation(Property(p, "active"), EqualityOperator.`_==`, Constant.auto(false)))
    )
    assertThat(translateSql(delete))
      .isEqualTo("DELETE FROM Person AS p WHERE p.active = false")
  }

  @Test
  def dialectsExposeVendorSpecificRenderingHelpers(): Unit = {
    assertThat(MirrorSqlDialect.liftingPlaceholder(0))
      .isEqualTo("?")
    assertThat(H2Dialect.prepareForProbing("SELECT * FROM Person WHERE id = ?"))
      .startsWith("PREPARE p")
      .contains(" AS SELECT * FROM Person WHERE id = ?")
    assertThat(PostgresDialect.prepareForProbing("SELECT * FROM Person WHERE id = ? AND active = ?"))
      .contains("PREPARE p")
      .contains("id = $1")
      .contains("active = $2")
    assertThat(io.getquill.MySQLDialect.prepareForProbing("SELECT 'Ada'"))
      .startsWith("PREPARE p")
      .endsWith(" FROM 'SELECT \\'Ada\\''")

    implicit val naming: NamingStrategy = Literal
    val context: IdiomContext = IdiomContext(TranspileConfig.Empty, IdiomContext.QueryType.Select)
    val p: Ident = Ident("p", personQuat)
    val taggedQuery: Filter = Filter(
      person,
      p,
      BinaryOperation(Property(p, "id"), EqualityOperator.`_==`, ScalarTag("idTag", io.getquill.ast.External.Source.Parser))
    )

    assertThat(MirrorSqlDialect.translate(taggedQuery, taggedQuery.quat, ExecutionType.Static, context)._2.toString)
      .isEqualTo("SELECT p.id, p.firstName, p.age, p.active FROM Person p WHERE p.id = lift(idTag)")
  }

  @Test
  def quotedReferencesDelegateToUnderlyingAstForTokenization(): Unit = {
    implicit val naming: NamingStrategy = Literal
    val p: Ident = Ident("p", personQuat)
    val property: Property = Property(p, "firstName")
    val quoted: QuotedReference = QuotedReference("compile-time-tree", property)
    val context: IdiomContext = IdiomContext(TranspileConfig.Empty, IdiomContext.QueryType.Select)

    assertThat(MirrorIdiom.translate(quoted, quoted.quat, ExecutionType.Static, context)._2.toString)
      .isEqualTo("p.firstName")
    assertThat(quoted.quat)
      .isEqualTo(Quat.Value)
  }
}
