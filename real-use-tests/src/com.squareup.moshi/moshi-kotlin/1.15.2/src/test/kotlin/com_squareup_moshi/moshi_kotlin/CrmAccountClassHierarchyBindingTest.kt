/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises class-hierarchy binding in `KotlinJsonAdapterFactory`: properties inherited from
 * generic and open superclasses, constructor parameters that feed superclass properties,
 * private setters, `lateinit` and computed properties, and rejection of class shapes the
 * factory cannot bind.
 *
 * A CRM service keeps its domain entities in a small class hierarchy rooted at a generic
 * `Entity` base and syncs them as JSON with a partner system.
 */
@OptIn(ExperimentalStdlibApi::class)
public class CrmAccountClassHierarchyBindingTest {

    /** Support tier of a customer account. */
    public enum class Tier {
        @Json(name = "free")
        FREE,

        @Json(name = "gold")
        GOLD,
    }

    /** Base of every persisted entity, keyed by a type the subclass chooses. */
    public abstract class Entity<K> {
        public var key: K? = null

        public var version: Int = 0

        @Json(name = "updated_at")
        public var updatedAt: Long = 0
    }

    /** A customer account keyed by its string handle. */
    public class CustomerAccount(
        public val email: String,
        public val tier: Tier = Tier.FREE,
    ) : Entity<String>() {
        public var displayName: String? = null

        /** Only the account itself counts logins; the partner may still report the total. */
        public var loginCount: Int = 0
            private set

        /** Assigned when the account is first routed to a data region. */
        public lateinit var region: String

        /** Derived from the tier; never bound to JSON. */
        public val isVip: Boolean
            get() = tier == Tier.GOLD

        public fun recordLogin() {
            loginCount++
        }

        public fun hasRegion(): Boolean = this::region.isInitialized
    }

    /** A sales contact; concrete and open so leads can extend it. */
    public open class Contact(public val name: String) {
        public var phone: String? = null
    }

    /** A contact being qualified; its `name` parameter feeds the inherited property. */
    public class Lead(name: String, public val score: Int) : Contact(name)

    /** Ticket counter keyed by a numeric id. */
    public class Ticket(public val subject: String) : Entity<Long>()

    /** Free-form note bound to the enclosing test instance. */
    public inner class Note(public val text: String)

    /** Process-wide CRM settings. */
    public object CrmSettings {
        public const val SYNC_BATCH: Int = 50
    }

    /** Fingerprint built from a token the class never stores. */
    public class TokenFingerprint(token: String) {
        public val length: Int = token.length
    }

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val accountAdapter: JsonAdapter<CustomerAccount> = moshi.adapter<CustomerAccount>()

    @Test
    fun accountDecodesConstructorInheritedAndPrivateSetterProperties() {
        val payload: String = """
            {
              "key": "acme-ops",
              "version": 7,
              "updated_at": 1760000000000,
              "email": "ops@acme.example",
              "tier": "gold",
              "displayName": "Acme Operations",
              "loginCount": 41,
              "region": "eu-west",
              "isVip": false
            }
        """.trimIndent()

        val account: CustomerAccount = accountAdapter.fromJson(payload)!!

        assertEquals("acme-ops", account.key)
        assertEquals(7, account.version)
        assertEquals(1760000000000L, account.updatedAt)
        assertEquals("ops@acme.example", account.email)
        assertEquals(Tier.GOLD, account.tier)
        assertEquals("Acme Operations", account.displayName)
        assertEquals(41, account.loginCount)
        assertTrue(account.hasRegion())
        assertEquals("eu-west", account.region)
        assertTrue(account.isVip)

        account.recordLogin()
        assertEquals(42, account.loginCount)
    }

    @Test
    fun missingOptionalFieldsKeepClassDefaultsAndLeaveLateinitUnset() {
        val account: CustomerAccount = accountAdapter.fromJson("""{"email":"new@acme.example"}""")!!

        assertNull(account.key)
        assertEquals(0, account.version)
        assertEquals(0L, account.updatedAt)
        assertEquals(Tier.FREE, account.tier)
        assertNull(account.displayName)
        assertEquals(0, account.loginCount)
        assertFalse(account.hasRegion())
        assertFalse(account.isVip)
    }

    @Test
    fun accountEncodesOwnAndInheritedPropertiesButNotComputedOnes() {
        val account: CustomerAccount = CustomerAccount("sales@acme.example", Tier.GOLD)
        account.key = "acme-sales"
        account.version = 3
        account.updatedAt = 1760000123000L
        account.displayName = "Acme Sales"
        account.region = "us-east"
        account.recordLogin()
        account.recordLogin()

        val json: String = accountAdapter.toJson(account)
        val fields: Map<String, Any?> = moshi.adapter<Map<String, Any?>>().fromJson(json)!!

        assertEquals(
            mapOf<String, Any?>(
                "key" to "acme-sales",
                "version" to 3.0,
                "updated_at" to 1760000123000.0,
                "email" to "sales@acme.example",
                "tier" to "gold",
                "displayName" to "Acme Sales",
                "loginCount" to 2.0,
                "region" to "us-east",
            ),
            fields,
        )

        val roundTripped: CustomerAccount = accountAdapter.fromJson(json)!!
        assertEquals(account.key, roundTripped.key)
        assertEquals(account.loginCount, roundTripped.loginCount)
        assertEquals(account.region, roundTripped.region)
    }

    @Test
    fun genericSuperclassKeyResolvesToTheSubclassTypeArgument() {
        val ticketAdapter: JsonAdapter<Ticket> = moshi.adapter<Ticket>()

        val ticket: Ticket = ticketAdapter.fromJson(
            """{"key":9000000001,"version":2,"subject":"Invoice mismatch"}""",
        )!!

        assertEquals(9000000001L, ticket.key)
        assertEquals("Invoice mismatch", ticket.subject)
        assertEquals(2, ticket.version)
        val json: String = ticketAdapter.toJson(ticket)
        assertTrue(json.contains("\"key\":9000000001"))
        assertEquals(
            mapOf<String, Any?>(
                "subject" to "Invoice mismatch",
                "key" to 9000000001.0,
                "version" to 2.0,
                "updated_at" to 0.0,
            ),
            moshi.adapter<Map<String, Any?>>().fromJson(json),
        )
    }

    @Test
    fun subclassConstructorParameterBindsTheInheritedProperty() {
        val leadAdapter: JsonAdapter<Lead> = moshi.adapter<Lead>()
        val contactAdapter: JsonAdapter<Contact> = moshi.adapter<Contact>()

        val lead: Lead = leadAdapter.fromJson("""{"name":"Dana Ruiz","score":82,"phone":"555-0102"}""")!!

        assertEquals("Dana Ruiz", lead.name)
        assertEquals(82, lead.score)
        assertEquals("555-0102", lead.phone)

        val asContact: Map<String, Any?> = moshi.adapter<Map<String, Any?>>()
            .fromJson(contactAdapter.toJson(lead))!!
        assertEquals(mapOf<String, Any?>("name" to "Dana Ruiz", "phone" to "555-0102"), asContact)

        val asLead: Map<String, Any?> = moshi.adapter<Map<String, Any?>>()
            .fromJson(leadAdapter.toJson(lead))!!
        assertEquals(
            mapOf<String, Any?>("name" to "Dana Ruiz", "score" to 82.0, "phone" to "555-0102"),
            asLead,
        )
    }

    @Test
    fun factoryRejectsClassShapesItCannotBind() {
        val abstractFailure: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            moshi.adapter<Entity<String>>()
        }
        assertTrue(abstractFailure.message!!.contains("Cannot serialize abstract class ${Entity::class.java.name}"))

        val innerFailure: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            moshi.adapter<Note>()
        }
        assertTrue(innerFailure.message!!.contains("Cannot serialize inner class ${Note::class.java.name}"))

        val objectFailure: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            moshi.adapter<CrmSettings>()
        }
        assertTrue(objectFailure.message!!.contains("Cannot serialize object declaration ${CrmSettings::class.java.name}"))
        assertEquals(50, CrmSettings.SYNC_BATCH)

        val parameterFailure: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            moshi.adapter<TokenFingerprint>()
        }
        assertTrue(parameterFailure.message!!.contains("No property for required constructor"))
        assertTrue(parameterFailure.message!!.contains("token"))
        assertEquals(6, TokenFingerprint("s3cr3t").length)
    }
}
