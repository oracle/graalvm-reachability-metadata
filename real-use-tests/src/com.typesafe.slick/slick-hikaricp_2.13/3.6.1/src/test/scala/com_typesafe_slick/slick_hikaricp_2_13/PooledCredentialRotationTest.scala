/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Connection

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.Driver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP live credential rotation and soft eviction for a running job service. */
class PooledCredentialRotationTest {
  @Test
  @Timeout(60)
  def newConnectionsUseRotatedCredentialsWithoutInterruptingAnActiveJob(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_credential_rotation"
      user = "sa"
      password = "initial-secret"
      maxConnections = 2
      minConnections = 0
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)
    val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
      config,
      new Driver,
      "credential-rotation-pool",
      getClass.getClassLoader
    )

    try {
      Using.resource(source.createConnection()) { activeJob: Connection =>
        Using.resource(activeJob.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("create table jobs (job_id integer primary key, status varchar(24) not null)")
          assertEquals(1, statement.executeUpdate("insert into jobs values (17, 'running')"))
          statement.executeUpdate("alter user sa set password 'rotated-secret'")
        }

        source.ds.getHikariConfigMXBean.setPassword("rotated-secret")
        source.ds.getHikariPoolMXBean.softEvictConnections()

        // Soft eviction lets the request already holding a connection finish normally.
        assertFalse(activeJob.isClosed)
        assertEquals("running", readJobStatus(activeJob))

        // With no idle reserve and the old lease held, this must authenticate a new connection.
        Using.resource(source.createConnection()) { nextRequest: Connection =>
          assertEquals("running", readJobStatus(nextRequest))
          Using.resource(nextRequest.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            assertEquals(1, statement.executeUpdate("update jobs set status = 'completed' where job_id = 17"))
          }
          assertEquals("completed", readJobStatus(activeJob))
        }
      }

      // The retired lease has now returned; subsequent work still sees the same database.
      Using.resource(source.createConnection()) { request: Connection =>
        assertEquals("completed", readJobStatus(request))
      }
    } finally {
      source.close()
    }
  }

  private def readJobStatus(connection: Connection): String = {
    Using.resource(connection.createStatement()) { statement =>
      statement.setQueryTimeout(10)
      Using.resource(statement.executeQuery("select status from jobs where job_id = 17")) { rows =>
        assertTrue(rows.next())
        val status: String = rows.getString(1)
        assertFalse(rows.next())
        status
      }
    }
  }
}
