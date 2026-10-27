/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.dsl.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Accessor discovery flags set project-wide through an implicit `TransformerConfiguration`: Java
 * Bean getters and setters, unmatched bean setters, method and inherited accessors, and
 * `Option` targets defaulting to `None`.
 *
 * A CRM synchronisation job talks to a vendor SDK whose records are mutable Java Beans with a
 * no-arg constructor, `getX()`/`isX()` getters and `setX(...)` setters. The job enables bean
 * support once in a `given` configuration, so every `transformInto` reads beans into immutable
 * case classes and writes case classes back into fresh beans. SDK-managed setters the domain
 * knows nothing about are left untouched, computed `def`s and inherited `val`s feed target
 * fields, and `Option` fields absent from the source come out empty unless a call overrides them.
 */
class LegacyCrmJavaBeanInteropTest {

  /** A vendor SDK record: mutable state behind Java Bean accessors. */
  class CrmContactBean {
    private var firstNameValue: String = ""
    private var lastNameValue: String = ""
    private var emailValue: String = ""
    private var vipValue: Boolean = false
    private var syncTokenValue: String = "unsynced"

    def getFirstName(): String = firstNameValue
    def setFirstName(value: String): Unit = firstNameValue = value
    def getLastName(): String = lastNameValue
    def setLastName(value: String): Unit = lastNameValue = value
    def getEmail(): String = emailValue
    def setEmail(value: String): Unit = emailValue = value
    def isVip(): Boolean = vipValue
    def setVip(value: Boolean): Unit = vipValue = value

    /** Managed by the SDK when it uploads the record; the domain never sets it. */
    def getSyncToken(): String = syncTokenValue
    def setSyncToken(value: String): Unit = syncTokenValue = value
  }

  final case class Contact(firstName: String, lastName: String, email: String, vip: Boolean) {
    def displayName: String = s"$firstName $lastName"
  }

  /** A read model that also carries a field the SDK has no notion of. */
  final case class ContactView(firstName: String, lastName: String, vip: Boolean, notes: Option[String])

  /** A mailing-list entry built from a computed method of the domain contact. */
  final case class MailingEntry(displayName: String, email: String)

  /** Every record the job stores belongs to a tenant fixed by the deployment. */
  abstract class TenantScoped {
    val tenant: String = "acme-eu"
  }

  final case class SyncTicket(contactEmail: String, attempt: Int) extends TenantScoped

  final case class SyncTicketRow(contactEmail: String, attempt: Int, tenant: String)

  /** The job-wide configuration every derivation in this class picks up. */
  transparent inline given TransformerConfiguration[?] = TransformerConfiguration.default
    .enableBeanGetters
    .enableBeanSetters
    .enableIgnoreUnmatchedBeanSetters
    .enableMethodAccessors
    .enableInheritedAccessors
    .enableOptionDefaultsToNone

  private def vendorRecord(first: String, last: String, email: String, vip: Boolean): CrmContactBean = {
    val bean: CrmContactBean = new CrmContactBean
    bean.setFirstName(first)
    bean.setLastName(last)
    bean.setEmail(email)
    bean.setVip(vip)
    bean.setSyncToken("sync-7f3a")
    bean
  }

  @Test
  def readsVendorBeanIntoDomainContactThroughGetters(): Unit = {
    val bean: CrmContactBean = vendorRecord("Grace", "Hopper", "grace@navy.example", vip = true)

    val contact: Contact = bean.transformInto[Contact]

    assertEquals(Contact("Grace", "Hopper", "grace@navy.example", vip = true), contact)
  }

  @Test
  def writesDomainContactIntoFreshBeanAndLeavesSdkManagedSetterAlone(): Unit = {
    val contact: Contact = Contact("Alan", "Turing", "alan@bletchley.example", vip = false)

    val bean: CrmContactBean = contact.transformInto[CrmContactBean]

    assertEquals("Alan", bean.getFirstName())
    assertEquals("Turing", bean.getLastName())
    assertEquals("alan@bletchley.example", bean.getEmail())
    assertFalse(bean.isVip())
    assertEquals("unsynced", bean.getSyncToken())
  }

  @Test
  def roundTripsBatchOfContactsThroughVendorBeans(): Unit = {
    val contacts: List[Contact] = List(
      Contact("Ada", "Lovelace", "ada@engine.example", vip = true),
      Contact("Edsger", "Dijkstra", "edsger@paths.example", vip = false)
    )

    val uploaded: List[CrmContactBean] = contacts.transformInto[List[CrmContactBean]]
    uploaded.foreach(_.setSyncToken("uploaded"))
    val downloaded: Vector[Contact] = uploaded.transformInto[Vector[Contact]]

    assertEquals(contacts.toVector, downloaded)
    assertTrue(uploaded.forall(_.getSyncToken() == "uploaded"))
    assertTrue(uploaded.head.isVip())
  }

  @Test
  def fillsMissingOptionalFieldsWithNoneUnlessCallOverridesThem(): Unit = {
    val bean: CrmContactBean = vendorRecord("Barbara", "Liskov", "barbara@clu.example", vip = true)

    val defaulted: ContactView = bean.transformInto[ContactView]
    val annotated: ContactView = bean.into[ContactView].withFieldConst(_.notes, Some("keynote speaker")).transform

    assertEquals(ContactView("Barbara", "Liskov", vip = true, notes = None), defaulted)
    assertEquals(Some("keynote speaker"), annotated.notes)
    assertEquals(defaulted.copy(notes = Some("keynote speaker")), annotated)
  }

  @Test
  def buildsMailingEntriesFromComputedDisplayName(): Unit = {
    val bean: CrmContactBean = vendorRecord("Donald", "Knuth", "don@taocp.example", vip = false)

    val entry: MailingEntry = bean.transformInto[Contact].transformInto[MailingEntry]

    assertEquals(MailingEntry("Donald Knuth", "don@taocp.example"), entry)
  }

  @Test
  def copiesInheritedTenantIntoStoredTicketRow(): Unit = {
    val tickets: List[SyncTicket] = List(SyncTicket("grace@navy.example", 1), SyncTicket("alan@bletchley.example", 3))

    val rows: List[SyncTicketRow] = tickets.transformInto[List[SyncTicketRow]]

    assertEquals(
      List(SyncTicketRow("grace@navy.example", 1, "acme-eu"), SyncTicketRow("alan@bletchley.example", 3, "acme-eu")),
      rows
    )
  }
}
