/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.FloatVectorColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntVectorColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.VectorFormat
import org.jetbrains.exposed.v1.core.Version
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.withThreadLocalTransaction
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.junit.jupiter.api.Test
import java.io.InputStream

public class SqlServerVectorBinderTest {
    @Test
    public fun sqlServerVectorParametersAreConvertedThroughJdbcVectorConstructorAndReadBack(): Unit =
        withSqlServerDialect {
            val statement = SqlServerCapturingPreparedStatement()
            val floatColumnType = FloatVectorColumnType(dimensions = 3, format = VectorFormat.FLOAT32)
            val intColumnType = IntVectorColumnType(dimensions = 3, format = VectorFormat.INT8)

            floatColumnType.setParameter(statement, index = 1, value = floatArrayOf(1.25f, 2.5f, 3.75f))
            assertThatThrownBy { intColumnType.setParameter(statement, index = 2, value = intArrayOf(1, 2, 3)) }
                .hasRootCauseInstanceOf(IllegalArgumentException::class.java)

            assertThat(statement.boundValues.map { it.index }).containsExactly(1)
            assertThat(statement.boundValues.map { it.value.javaClass.name }).containsExactly("microsoft.sql.Vector")

            val floatData = floatColumnType.readObject(SqlServerVectorRow(statement.boundValues[0].value), index = 1)

            assertThat(floatData as Array<*>).containsExactly(1.25f, 2.5f, 3.75f)
        }
}

@OptIn(InternalApi::class)
private fun withSqlServerDialect(block: () -> Unit): Unit {
    withThreadLocalTransaction(SqlServerFakeTransaction(SqlServerFakeDatabase(SQLServerDialect())), block)
}

private data class SqlServerBoundValue(
    val index: Int,
    val value: Any,
)

private class SqlServerCapturingPreparedStatement : PreparedStatementApi {
    val boundValues = mutableListOf<SqlServerBoundValue>()

    override fun set(index: Int, value: Any, columnType: IColumnType<*>): Unit {
        boundValues += SqlServerBoundValue(index, value)
    }

    override fun setNull(index: Int, columnType: IColumnType<*>): Unit {
        throw AssertionError("SQL Server vector parameters must be converted to non-null Vector instances")
    }

    override fun setInputStream(index: Int, inputStream: InputStream, setAsBlobObject: Boolean): Unit {
        throw AssertionError("Vector columns must not bind input streams")
    }

    override fun setArray(index: Int, type: ArrayColumnType<*, *>, array: Array<*>): Unit {
        throw AssertionError("Vector columns must not bind SQL arrays")
    }
}

private class SqlServerVectorRow(private val vector: Any) : RowApi {
    override fun getObject(index: Int): Any? = vector

    override fun getObject(name: String): Any? = vector

    override fun <T> getObject(index: Int, type: Class<T>): T? =
        if (type.isInstance(vector)) type.cast(vector) else null

    override fun <T> getObject(name: String, type: Class<T>): T? =
        if (type.isInstance(vector)) type.cast(vector) else null

    override fun getString(index: Int): String? = vector.toString()
}

private class SqlServerFakeTransaction(override val db: DatabaseApi) : Transaction() {
    override val transactionManager: TransactionManagerApi = SqlServerFakeTransactionManager()
    override val readOnly: Boolean = false
    override val outerTransaction: Transaction? = null
}

private class SqlServerFakeTransactionManager : TransactionManagerApi {
    override var defaultReadOnly: Boolean = false
    override var defaultMaxAttempts: Int = 1
    override var defaultMinRetryDelay: Long = 0L
    override var defaultMaxRetryDelay: Long = 0L
}

private class SqlServerFakeDatabase(override val dialect: DatabaseDialect) : DatabaseApi(
    resolvedVendor = "sqlserver",
    config = DatabaseConfig()
) {
    override val url: String = "jdbc:sqlserver://localhost;databaseName=test"
    override val vendor: String = "sqlserver"
    override val dialectMode: String? = null
    override val version: Version = Version.from("17.0.0")
    override val fullVersion: String = "17.0.0"
    override val supportsAlterTableWithAddColumn: Boolean = true
    override val supportsAlterTableWithDropColumn: Boolean = true
    override val supportsMultipleResultSets: Boolean = true
    override val supportsSelectForUpdate: Boolean = true
    override val identifierManager: IdentifierManagerApi = SqlServerFakeIdentifierManager()
}

private class SqlServerFakeIdentifierManager : IdentifierManagerApi() {
    override val quoteString: String = "\""
    override val isUpperCaseIdentifiers: Boolean = false
    override val isUpperCaseQuotedIdentifiers: Boolean = false
    override val isLowerCaseIdentifiers: Boolean = false
    override val isLowerCaseQuotedIdentifiers: Boolean = false
    override val supportsMixedIdentifiers: Boolean = true
    override val supportsMixedQuotedIdentifiers: Boolean = true
    override val extraNameCharacters: String = ""
    override val oracleVersion: OracleVersion = OracleVersion.NonOracle
    override val maxColumnNameLength: Int = 128

    override fun dbKeywords(): List<String> = emptyList()
}
