/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.util.UUID
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC typed-object binding and retrieval of UUIDs for single-use invitations.
 */
@Timeout(60)
class UuidInvitationRedemptionTest {
    @Test
    fun redeemsAnInvitationOnceWithoutChangingOtherRecipientsOrAccountOwnership() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:uuid_invitation_redemption")
        }
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE invitations (
                        token UUID PRIMARY KEY,
                        email VARCHAR(200) NOT NULL,
                        redeemed_by UUID
                    )
                    """.trimIndent(),
                    0
                )
                val invitations: InvitationStore = InvitationStore(driver)
                val aliceToken: UUID = UUID.fromString("10566789-abcd-4abc-8123-012345678901")
                val bobToken: UUID = UUID.fromString("20566789-abcd-4abc-8123-012345678902")
                val missingToken: UUID = UUID.fromString("30566789-abcd-4abc-8123-012345678903")
                val aliceAccount: UUID = UUID.fromString("40566789-abcd-4abc-8123-012345678904")
                val otherAccount: UUID = UUID.fromString("50566789-abcd-4abc-8123-012345678905")
                val alice: Invitation = Invitation(aliceToken, "alice@example.test", null)
                val bob: Invitation = Invitation(bobToken, "bob@example.test", null)
                invitations.issue(alice)
                invitations.issue(bob)

                assertEquals(alice, invitations.find(aliceToken))
                assertEquals(bob, invitations.find(bobToken))
                assertNull(invitations.find(missingToken))

                assertTrue(invitations.redeem(aliceToken, aliceAccount))
                assertEquals(alice.copy(redeemedBy = aliceAccount), invitations.find(aliceToken))

                // A replay must not transfer the invitation to a different account.
                assertFalse(invitations.redeem(aliceToken, otherAccount))
                assertEquals(alice.copy(redeemedBy = aliceAccount), invitations.find(aliceToken))
                assertEquals(bob, invitations.find(bobToken))
                assertFalse(invitations.redeem(missingToken, otherAccount))
                assertNull(invitations.find(missingToken))

                assertTrue(invitations.redeem(bobToken, otherAccount))
                assertEquals(bob.copy(redeemedBy = otherAccount), invitations.find(bobToken))
                assertEquals(alice.copy(redeemedBy = aliceAccount), invitations.find(aliceToken))
            }
        }
    }

    private class InvitationStore(private val driver: JdbcDriver) {
        fun issue(invitation: Invitation) {
            val changed: Long = driver.execute(
                null,
                "INSERT INTO invitations (token, email, redeemed_by) VALUES (?, ?, ?)",
                3
            ) {
                val statement: JdbcPreparedStatement = this as JdbcPreparedStatement
                statement.bindObject(0, invitation.token)
                statement.bindString(1, invitation.email)
                statement.bindObject(2, invitation.redeemedBy)
            }.value
            check(changed == 1L)
        }

        fun find(token: UUID): Invitation? = driver.executeQuery(
            null,
            "SELECT token, email, redeemed_by FROM invitations WHERE token = ?",
            mapper = { cursor ->
                val jdbcCursor: JdbcCursor = cursor as JdbcCursor
                val invitation: Invitation? = if (jdbcCursor.next().value) {
                    Invitation(
                        token = requireNotNull(jdbcCursor.getObject<UUID>(0)),
                        email = requireNotNull(jdbcCursor.getString(1)),
                        redeemedBy = jdbcCursor.getObject<UUID>(2)
                    )
                } else {
                    null
                }
                QueryResult.Value(invitation)
            },
            parameters = 1,
            binders = { (this as JdbcPreparedStatement).bindObject(0, token) }
        ).value

        fun redeem(token: UUID, account: UUID): Boolean = driver.execute(
            null,
            "UPDATE invitations SET redeemed_by = ? WHERE token = ? AND redeemed_by IS NULL",
            2
        ) {
            val statement: JdbcPreparedStatement = this as JdbcPreparedStatement
            statement.bindObject(0, account)
            statement.bindObject(1, token)
        }.value == 1L
    }

    private data class Invitation(val token: UUID, val email: String, val redeemedBy: UUID?)
}
