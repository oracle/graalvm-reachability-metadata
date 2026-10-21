/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.Patcher
import io.scalaland.chimney.dsl.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `Patcher` derivation that applies partial update requests onto stored case classes.
 *
 * An account service keeps a stored account record and receives PATCH-style requests from its
 * REST API. A request carries only the fields a client wants to change: plain `Option` fields
 * replace the stored value when present, `Option[Option[...]]` fields distinguish "leave as is"
 * from "clear", and nested settings are patched field by field. The service tunes the derived
 * patchers with `ignoreNoneInPatch`, `appendCollectionInPatch`, `ignoreRedundantPatcherFields`,
 * a scoped `withPatchedValueFlag` and `withField*` overrides, and keeps one reusable `Patcher`
 * as a `given` that `patchUsing` picks up.
 */
class AccountSettingsPatcherTest {

  final case class Notifications(email: Boolean, sms: Boolean, digestHour: Int)
  final case class Account(
      id: String,
      displayName: String,
      email: String,
      phone: Option[String],
      nickname: Option[String],
      tags: List[String],
      notifications: Notifications,
      version: Int
  )

  final case class NotificationsPatch(email: Option[Boolean], sms: Option[Boolean], digestHour: Option[Int])
  final case class AccountPatch(
      displayName: Option[String],
      email: Option[String],
      phone: Option[Option[String]],
      nickname: Option[String],
      tags: List[String],
      notifications: NotificationsPatch
  )

  final case class ContactPatch(email: Option[String], phone: Option[String], requestId: String)
  final case class RenameRequest(displayName: String, reason: String)

  private val stored: Account = Account(
    id = "ACC-1",
    displayName = "Ada Lovelace",
    email = "ada@example.org",
    phone = Some("+44 20 7946 0000"),
    nickname = Some("countess"),
    tags = List("beta", "newsletter"),
    notifications = Notifications(email = true, sms = false, digestHour = 8),
    version = 4
  )

  private val noNotificationChange: NotificationsPatch = NotificationsPatch(None, None, None)

  /** The patcher the REST layer reuses: nickname `None` keeps the stored value, tags append. */
  given Patcher[Account, AccountPatch] = Patcher
    .define[Account, AccountPatch]
    .withPatchedValueFlag(_.nickname)
    .ignoreNoneInPatch
    .appendCollectionInPatch
    .buildPatcher

  @Test
  def appliesPresentFieldsAndKeepsOrClearsNestedOptionalValues(): Unit = {
    val patch: AccountPatch = AccountPatch(
      displayName = Some("Augusta Ada King"),
      email = None,
      phone = Some(None),
      nickname = None,
      tags = List("vip"),
      notifications = NotificationsPatch(email = None, sms = Some(true), digestHour = Some(20))
    )

    val patched: Account = stored.patchUsing(patch)

    assertEquals(
      stored.copy(
        displayName = "Augusta Ada King",
        phone = None,
        tags = List("beta", "newsletter", "vip"),
        notifications = Notifications(email = true, sms = true, digestHour = 20)
      ),
      patched
    )
  }

  @Test
  def leavesPhoneAndNicknameUntouchedWhenThePatchOmitsThem(): Unit = {
    val patch: AccountPatch = AccountPatch(
      displayName = None,
      email = Some("ada@analytical.engine"),
      phone = None,
      nickname = None,
      tags = Nil,
      notifications = noNotificationChange
    )

    val patched: Account = stored.patchUsing(patch)

    assertEquals(stored.copy(email = "ada@analytical.engine"), patched)
  }

  @Test
  def reusesGivenPatcherUnlessAnOverrideForcesDerivationWithDefaultFlags(): Unit = {
    val patch: AccountPatch = AccountPatch(
      displayName = None,
      email = None,
      phone = Some(Some("+44 20 7946 1111")),
      nickname = None,
      tags = List("archived"),
      notifications = NotificationsPatch(email = Some(false), sms = None, digestHour = None)
    )

    val viaGiven: Account = stored.using(patch).patch
    assertEquals(
      stored.copy(
        phone = Some("+44 20 7946 1111"),
        tags = List("beta", "newsletter", "archived"),
        notifications = stored.notifications.copy(email = false)
      ),
      viaGiven
    )

    val adHoc: Account = stored.using(patch).withFieldIgnored(_.notifications).patch
    assertEquals(stored.copy(phone = Some("+44 20 7946 1111"), nickname = None, tags = List("archived")), adHoc)
  }

  @Test
  def ignoresRedundantRequestFieldsAndAppliesFieldOverrides(): Unit = {
    val contact: ContactPatch = ContactPatch(email = None, phone = Some("+1 555 0100"), requestId = "REQ-9")

    val withContact: Account = stored
      .using(contact)
      .ignoreRedundantPatcherFields
      .withFieldComputed(_.version, (_: ContactPatch) => stored.version + 1)
      .patch

    assertEquals(stored.copy(phone = Some("+1 555 0100"), version = 5), withContact)

    val rename: RenameRequest = RenameRequest("A. Lovelace", "legal name change")
    val renamed: Account = withContact
      .using(rename)
      .withFieldIgnored(_.reason)
      .withFieldConst(_.nickname, None)
      .patch

    assertEquals(withContact.copy(displayName = "A. Lovelace", nickname = None), renamed)
  }
}
