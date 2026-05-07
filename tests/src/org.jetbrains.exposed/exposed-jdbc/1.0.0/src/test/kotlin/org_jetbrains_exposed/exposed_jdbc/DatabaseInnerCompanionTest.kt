/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.ExposedConnectionImpl
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

public class DatabaseInnerCompanionTest {
    @Test
    public fun connectWithRegisteredJdbcDriverInstantiatesDriverClass(): Unit {
        val prefix: String = "jdbc:exposed-dynamic-access:"
        val constructorCallsBefore: Int = DatabaseCompanionTestDriver.constructorCalls.get()

        Database.registerJdbcDriver(
            prefix = prefix,
            driverClassName = DatabaseCompanionTestDriver::class.java.name,
            dialect = H2Dialect.dialectName
        )

        val database: Database = Database.connect(
            url = "${prefix}memory",
            connectionAutoRegistration = ExposedConnectionImpl()
        )

        assertThat(database.vendor).isEqualTo(H2Dialect.dialectName)
        assertThat(DatabaseCompanionTestDriver.constructorCalls.get()).isEqualTo(constructorCallsBefore + 1)
    }
}

public class DatabaseCompanionTestDriver {
    public companion object {
        public val constructorCalls: AtomicInteger = AtomicInteger()
    }

    public constructor() {
        constructorCalls.incrementAndGet()
    }
}
