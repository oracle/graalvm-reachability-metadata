/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import java.util.Locale

import cats.data.AndThen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats AndThen to compile reusable customer-record schema migration pipelines.
  * Each pipeline applies only the migrations missing from the stored schema version.
  */
@Timeout(60)
class CustomerMigrationAndThenTest {
  private type Fields = Map[String, String]

  private case class CustomerRecord(schemaVersion: Int, fields: Fields)

  private def rename(fields: Fields, oldName: String, newName: String): Fields = {
    fields.get(oldName).fold(fields) { value =>
      (fields - oldName).updated(newName, value)
    }
  }

  private val migrations: List[AndThen[Fields, Fields]] = List(
    AndThen { fields =>
      rename(rename(fields, "full_name", "displayName"), "email_address", "email")
    },
    AndThen { fields =>
      val preference: String = if (fields.get("marketing").contains("yes")) "email" else "none"
      (fields - "marketing")
        .updatedWith("displayName")(_.map(_.trim.split("\\s+").mkString(" ")))
        .updatedWith("email")(_.map(_.trim.toLowerCase(Locale.ROOT)))
        .updated("notificationChannel", preference)
    },
    AndThen { fields =>
      val name: String = fields.getOrElse("displayName", "Customer")
      val label: String = fields.get("email").filter(_.nonEmpty)
        .fold(name)(email => s"$name <$email>")
      fields.updated("contactLabel", label)
    }
  )

  private def migrationFrom(schemaVersion: Int): AndThen[Fields, CustomerRecord] = {
    val pending: List[AndThen[Fields, Fields]] = migrations.drop(schemaVersion - 1)
    val pipeline: AndThen[Fields, Fields] = pending.foldLeft(AndThen(identity[Fields])) {
      (compiled, migration) => compiled.andThen(migration)
    }
    pipeline.andThen(fields => CustomerRecord(migrations.size + 1, fields))
  }

  @Test
  def reusesACompiledPipelineForAnImportBatchWithoutMixingCustomerFields(): Unit = {
    val legacy: List[Fields] = List(
      Map(
        "customerId" -> "C-100",
        "full_name" -> "  Ada   Lovelace ",
        "email_address" -> " ADA@EXAMPLE.COM ",
        "marketing" -> "yes"
      ),
      Map(
        "customerId" -> "C-200",
        "full_name" -> " Grace Hopper ",
        "email_address" -> " GRACE@EXAMPLE.COM ",
        "marketing" -> "no",
        "accountTier" -> "gold"
      )
    )
    val migrate: AndThen[Fields, CustomerRecord] = migrationFrom(1)
    val expected: List[CustomerRecord] = List(
      CustomerRecord(4, Map(
        "customerId" -> "C-100",
        "displayName" -> "Ada Lovelace",
        "email" -> "ada@example.com",
        "notificationChannel" -> "email",
        "contactLabel" -> "Ada Lovelace <ada@example.com>"
      )),
      CustomerRecord(4, Map(
        "customerId" -> "C-200",
        "displayName" -> "Grace Hopper",
        "email" -> "grace@example.com",
        "notificationChannel" -> "none",
        "contactLabel" -> "Grace Hopper <grace@example.com>",
        "accountTier" -> "gold"
      ))
    )

    assertEquals(expected, legacy.map(migrate))
    assertEquals(expected.reverse, legacy.reverse.map(migrate))
    assertEquals("  Ada   Lovelace ", legacy.head("full_name"))
  }

  @Test
  def resumesFromTheStoredVersionAndLeavesCurrentRecordsUnchanged(): Unit = {
    val versionTwo: Fields = Map("customerId" -> "C-300", "displayName" -> "  Lin   Chen  ")
    val expected: CustomerRecord = CustomerRecord(4, Map(
      "customerId" -> "C-300",
      "displayName" -> "Lin Chen",
      "notificationChannel" -> "none",
      "contactLabel" -> "Lin Chen"
    ))

    assertEquals(expected, migrationFrom(2)(versionTwo))
    assertEquals(expected, migrationFrom(3)(expected.fields - "contactLabel"))
    assertEquals(expected, migrationFrom(4)(expected.fields))
    assertEquals(expected, migrationFrom(4)(migrationFrom(2)(versionTwo).fields))
  }
}
