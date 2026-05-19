/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
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
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.withThreadLocalTransaction
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.junit.jupiter.api.Test
import java.io.InputStream

public class OracleVectorBinderTest {
    @Test
    public fun oracleVectorParametersAreConvertedThroughOracleJdbcVectorFactories(): Unit = withOracleDialect {
        val statement = CapturingPreparedStatement()
        val float32ColumnType = FloatVectorColumnType(dimensions = 3, format = VectorFormat.FLOAT32)
        val float64ColumnType = FloatVectorColumnType(dimensions = 3, format = VectorFormat.FLOAT64)
        val int8ColumnType = IntVectorColumnType(dimensions = 3, format = VectorFormat.INT8)

        float32ColumnType.setParameter(statement, index = 1, value = floatArrayOf(1.25f, 2.5f, 3.75f))
        float64ColumnType.setParameter(statement, index = 2, value = doubleArrayOf(1.25, 2.5, 3.75))
        int8ColumnType.setParameter(statement, index = 3, value = intArrayOf(1, 2, 3))

        assertThat(statement.boundValues.map { it.index }).containsExactly(1, 2, 3)
        assertThat(statement.boundValues.map { it.value.javaClass.name })
            .containsExactly("oracle.sql.VECTOR", "oracle.sql.VECTOR", "oracle.sql.VECTOR")
    }
}

@OptIn(InternalApi::class)
private fun withOracleDialect(block: () -> Unit): Unit {
    withThreadLocalTransaction(FakeTransaction(FakeDatabase(OracleDialect())), block)
}

private data class BoundValue(
    val index: Int,
    val value: Any,
)

private class CapturingPreparedStatement : PreparedStatementApi {
    val boundValues = mutableListOf<BoundValue>()

    override fun set(index: Int, value: Any, columnType: IColumnType<*>): Unit {
        boundValues += BoundValue(index, value)
    }

    override fun setNull(index: Int, columnType: IColumnType<*>): Unit {
        throw AssertionError("Oracle vector parameters must be converted to non-null VECTOR instances")
    }

    override fun setInputStream(index: Int, inputStream: InputStream, setAsBlobObject: Boolean): Unit {
        throw AssertionError("Vector columns must not bind input streams")
    }

    override fun setArray(index: Int, type: ArrayColumnType<*, *>, array: Array<*>): Unit {
        throw AssertionError("Vector columns must not bind SQL arrays")
    }
}

private class FakeTransaction(override val db: DatabaseApi) : Transaction() {
    override val transactionManager: TransactionManagerApi = FakeTransactionManager()
    override val readOnly: Boolean = false
    override val outerTransaction: Transaction? = null
}

private class FakeTransactionManager : TransactionManagerApi {
    override var defaultReadOnly: Boolean = false
    override var defaultMaxAttempts: Int = 1
    override var defaultMinRetryDelay: Long = 0L
    override var defaultMaxRetryDelay: Long = 0L
}

private class FakeDatabase(override val dialect: DatabaseDialect) : DatabaseApi(
    resolvedVendor = "oracle",
    config = DatabaseConfig()
) {
    override val url: String = "jdbc:oracle:thin:@localhost:1521/test"
    override val vendor: String = "oracle"
    override val dialectMode: String? = null
    override val version: Version = Version.from("23.0.0")
    override val fullVersion: String = "23.0.0"
    override val supportsAlterTableWithAddColumn: Boolean = true
    override val supportsAlterTableWithDropColumn: Boolean = true
    override val supportsMultipleResultSets: Boolean = true
    override val supportsSelectForUpdate: Boolean = true
    override val identifierManager: IdentifierManagerApi = FakeIdentifierManager()
}

private class FakeIdentifierManager : IdentifierManagerApi() {
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
