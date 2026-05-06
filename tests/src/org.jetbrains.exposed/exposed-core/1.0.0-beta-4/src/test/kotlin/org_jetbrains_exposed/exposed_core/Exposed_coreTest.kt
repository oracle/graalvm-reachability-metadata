/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Version
import org.jetbrains.exposed.v1.core.WindowFrameBound
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.append
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.intParam
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.exceptions.DuplicateColumnException
import org.junit.jupiter.api.Test

public class ExposedCoreTest {
    @Test
    fun tableMetadataTracksColumnsKeysReferencesAndAliases(): Unit {
        assertThat(Users.tableName).isEqualTo("app.users")
        assertThat(Users.schemaName).isEqualTo("app")
        assertThat(Users.columns.map { it.name })
            .containsExactly("id", "email", "display_name", "status", "score", "active", "generated_token")
        val primaryKeyColumns: List<Column<*>> = requireNotNull(Users.primaryKey).columns.toList()
        assertThat(primaryKeyColumns).containsExactly(Users.id)
        assertThat(Users.autoIncColumn).isEqualTo(Users.id)

        val emailIndex: Index = Users.indices.single()
        assertThat(emailIndex.customName).isEqualTo("ux_users_email")
        assertThat(emailIndex.unique).isTrue()
        assertThat(emailIndex.columns).containsExactly(Users.email)

        assertThat(Memberships.user.referee).isEqualTo(Users.id)
        assertThat(Memberships.group.referee).isEqualTo(Groups.id)
        assertThat(Memberships.foreignKeys).hasSize(2)

        val userAlias: Alias<Users> = Users.alias("u")
        val aliasedEmail: Column<String> = userAlias[Users.email]
        assertThat(userAlias.tableName).isEqualTo("u")
        assertThat(userAlias.tableNameWithAlias).isEqualTo("app.users u")
        assertThat(aliasedEmail.name).isEqualTo(Users.email.name)
        assertThat(aliasedEmail.table).isEqualTo(userAlias)
        assertThat(userAlias.originalColumn(aliasedEmail)).isEqualTo(Users.email)
    }

    @Test
    fun joinsInferForeignKeysAndExposeCombinedFieldSets(): Unit {
        val userMembershipJoin: Join = Users.innerJoin(Memberships)

        assertThat(userMembershipJoin.columns).containsExactlyElementsOf(Users.columns + Memberships.columns)
        assertThat(userMembershipJoin.fields).containsExactlyElementsOf(Users.fields + Memberships.fields)
        assertThat(userMembershipJoin.alreadyInJoin(Memberships)).isTrue()
        assertThat(userMembershipJoin.alreadyInJoin(Groups)).isFalse()

        val userMembershipGroupJoin: Join = userMembershipJoin.innerJoin(Groups)

        assertThat(userMembershipGroupJoin.columns)
            .containsExactlyElementsOf(Users.columns + Memberships.columns + Groups.columns)
        assertThat(userMembershipGroupJoin.fields)
            .containsExactlyElementsOf(Users.fields + Memberships.fields + Groups.fields)
        assertThat(userMembershipGroupJoin.alreadyInJoin(Memberships)).isTrue()
        assertThat(userMembershipGroupJoin.alreadyInJoin(Groups)).isTrue()
    }

    @Test
    fun duplicateColumnNamesAreRejected(): Unit {
        val table: Table = Table("duplicate_columns")
        table.integer("id")

        assertThatThrownBy { table.varchar("id", 16) }
            .isInstanceOf(DuplicateColumnException::class.java)
            .hasMessageContaining("id")
            .hasMessageContaining("duplicate_columns")
    }

    @Test
    fun columnDefaultsNullabilityAndGeneratedFlagsAreTracked(): Unit {
        assertThat(Users.email.defaultValueFun == null).isTrue()
        assertThat(Users.email.defaultValueInDb() == null).isTrue()
        assertThat(Users.email.columnType.nullable).isFalse()

        assertThat(Users.displayName.columnType.nullable).isTrue()
        assertThat(Users.status.defaultValueFun?.invoke()).isEqualTo("active")
        assertThat(Users.status.defaultValueInDb().toString()).isEqualTo("'active'")
        assertThat(Users.score.defaultValueFun?.invoke()).isEqualTo(7)
        assertThat(Users.score.defaultValueInDb() == null).isTrue()
        assertThat(Users.active.defaultValueFun?.invoke()).isTrue()
        assertThat(Users.active.defaultValueInDb()).isNotNull()

        Users.generatedToken.defaultValueFun?.invoke().let { value: String? ->
            assertThat(value).isEqualTo("generated")
        }
        assertThat(Users.generatedToken.isDatabaseGenerated()).isTrue()
    }

    @Test
    fun columnTypesValidateAndFormatValues(): Unit {
        assertThat(Users.id.columnType.valueFromDB("42")).isEqualTo(42)
        assertThat(Users.email.columnType.valueFromDB(byteArrayOf(65, 66, 67))).isEqualTo("ABC")
        assertThat(Users.email.columnType.valueToString("O'Reilly\nGuide")).isEqualTo("'O''Reilly\\nGuide'")
        assertThat(Users.displayName.columnType.valueToString(null)).isEqualTo("NULL")

        Users.email.columnType.validateValueBeforeUpdate("short@example.org")
        assertThatThrownBy { Users.email.columnType.validateValueBeforeUpdate("x".repeat(129)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("exceeds length")

        assertThatThrownBy { Users.email.columnType.valueToString(null) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("NULL in non-nullable column")
    }

    @Test
    fun queryBuilderFormatsInlineSqlAndPreparedArguments(): Unit {
        val inlineBuilder: QueryBuilder = QueryBuilder(prepared = false)
        inlineBuilder.append(intLiteral(42), ", ", stringLiteral("O'Reilly"))

        assertThat(inlineBuilder.toString()).isEqualTo("42, 'O''Reilly'")
        assertThat(inlineBuilder.args).isEmpty()

        val preparedBuilder: QueryBuilder = QueryBuilder(prepared = true)
        preparedBuilder.append(intParam(7), ", ", stringParam("alpha"))

        assertThat(preparedBuilder.toString()).isEqualTo("?, ?")
        assertThat(preparedBuilder.args.map { it.second }).containsExactly(7, "alpha")
    }

    @Test
    fun expressionDslBuildsComparisonListsAliasesCasesAndWindows(): Unit {
        val comparison = with(SqlExpressionBuilder) {
            (intLiteral(1) less intLiteral(3)) and (stringLiteral("kotlin") like "kot%")
        }
        assertThat(comparison.toString()).isEqualTo("(1 < 3) AND ('kotlin' LIKE 'kot%')")

        val inList = with(SqlExpressionBuilder) { intLiteral(2) inList listOf(3, 1, 2) }
        assertThat(inList.toString()).isEqualTo("2 IN (3, 1, 2)")

        val arithmetic = with(SqlExpressionBuilder) { ((intLiteral(5) + 4) * intLiteral(2)) % 3 }
        assertThat(arithmetic.toString()).isEqualTo("(((5 + 4) * 2) % 3)")

        val caseExpression = with(SqlExpressionBuilder) {
            case()
                .When(intLiteral(1) eq 1, stringLiteral("one"))
                .Else(stringLiteral("other"))
        }
        assertThat(caseExpression.toString()).isEqualTo("CASE WHEN 1 = 1 THEN 'one' ELSE 'other' END")

        val coalesced = with(SqlExpressionBuilder) {
            coalesce(stringLiteral("primary"), stringLiteral("fallback"), stringLiteral("last"))
        }
        assertThat(coalesced.toString()).isEqualTo("COALESCE('primary', 'fallback', 'last')")

        val aliased = intLiteral(10).alias("ten")
        assertThat(aliased.toString()).isEqualTo("10 ten")
        assertThat(aliased.aliasOnlyExpression().toString()).isEqualTo("ten")

        val window = with(SqlExpressionBuilder) {
            rowNumber()
                .over()
                .partitionBy(stringLiteral("partition"))
                .rows(WindowFrameBound.unboundedPreceding())
        }
        assertThat(window.toString()).isEqualTo("ROW_NUMBER() OVER(PARTITION BY 'partition' ROWS UNBOUNDED PRECEDING)")
    }

    @Test
    fun versionParsingComparesSemanticComponents(): Unit {
        val version: Version = Version.from("1.2.0-beta-4")

        assertThat(version.toString()).isEqualTo("1.2.0")
        assertThat(version.covers("1.1.9")).isTrue()
        assertThat(version.covers("1.2.0")).isTrue()
        assertThat(version.covers("1.2.1")).isFalse()
        assertThat(Version.from("2").covers(1, 9, 9)).isTrue()
    }

    @Test
    @OptIn(ExperimentalKeywordApi::class)
    fun databaseConfigBuilderAppliesDefaultsCustomizationsAndValidation(): Unit {
        val defaultConfig: DatabaseConfig = DatabaseConfig()

        assertThat(defaultConfig.useNestedTransactions).isFalse()
        assertThat(defaultConfig.defaultFetchSize).isNull()
        assertThat(defaultConfig.defaultIsolationLevel).isEqualTo(-1)
        assertThat(defaultConfig.defaultMaxAttempts).isEqualTo(3)
        assertThat(defaultConfig.defaultMinRetryDelay).isZero()
        assertThat(defaultConfig.defaultMaxRetryDelay).isZero()
        assertThat(defaultConfig.defaultReadOnly).isFalse()
        assertThat(defaultConfig.warnLongQueriesDuration).isNull()
        assertThat(defaultConfig.maxEntitiesToStoreInCachePerEntity).isEqualTo(Int.MAX_VALUE)
        assertThat(defaultConfig.keepLoadedReferencesOutOfTransaction).isFalse()
        assertThat(defaultConfig.explicitDialect).isNull()
        assertThat(defaultConfig.defaultSchema).isNull()
        assertThat(defaultConfig.logTooMuchResultSetsThreshold).isZero()
        assertThat(defaultConfig.preserveKeywordCasing).isTrue()

        val schema = Schema("analytics")
        val customConfig: DatabaseConfig = DatabaseConfig {
            useNestedTransactions = true
            defaultFetchSize = 64
            defaultIsolationLevel = 8
            defaultMaxAttempts = 2
            defaultMinRetryDelay = 10L
            defaultMaxRetryDelay = 20L
            defaultReadOnly = true
            warnLongQueriesDuration = 250L
            maxEntitiesToStoreInCachePerEntity = 5
            keepLoadedReferencesOutOfTransaction = true
            defaultSchema = schema
            logTooMuchResultSetsThreshold = 3
            preserveKeywordCasing = false
        }

        assertThat(customConfig.useNestedTransactions).isTrue()
        assertThat(customConfig.defaultFetchSize).isEqualTo(64)
        assertThat(customConfig.defaultIsolationLevel).isEqualTo(8)
        assertThat(customConfig.defaultMaxAttempts).isEqualTo(2)
        assertThat(customConfig.defaultMinRetryDelay).isEqualTo(10L)
        assertThat(customConfig.defaultMaxRetryDelay).isEqualTo(20L)
        assertThat(customConfig.defaultReadOnly).isTrue()
        assertThat(customConfig.warnLongQueriesDuration).isEqualTo(250L)
        assertThat(customConfig.maxEntitiesToStoreInCachePerEntity).isEqualTo(5)
        assertThat(customConfig.keepLoadedReferencesOutOfTransaction).isTrue()
        assertThat(customConfig.defaultSchema).isEqualTo(schema)
        assertThat(customConfig.logTooMuchResultSetsThreshold).isEqualTo(3)
        assertThat(customConfig.preserveKeywordCasing).isFalse()

        assertThatThrownBy { DatabaseConfig { defaultMaxAttempts = 0 } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("defaultMaxAttempts")
    }
}

private object Users : Table("app.users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 128).uniqueIndex("ux_users_email")
    val displayName = varchar("display_name", 64).nullable()
    val status = varchar("status", 32).default("active")
    val score = integer("score").clientDefault { 7 }
    val active = bool("active").default(true)
    val generatedToken = varchar("generated_token", 32).clientDefault { "generated" }.databaseGenerated()

    override val primaryKey = PrimaryKey(id, name = "pk_users")
}

private object Groups : Table("groups") {
    val id = integer("id")
    val name = varchar("name", 80)

    override val primaryKey = PrimaryKey(id)
}

private object Memberships : Table("memberships") {
    val user = reference("user_id", Users.id)
    val group = reference("group_id", Groups.id)
    val role = varchar("role", 32).default("member")

    override val primaryKey = PrimaryKey(user, group, name = "pk_memberships")
}
