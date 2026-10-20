/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.data.OptionT
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats OptionT to resolve layered application settings with validation errors.
  * Missing required settings prevent startup, while malformed overrides remain explicit errors.
  */
@Timeout(60)
class LayeredSettingsOptionTTest {
  private case class Settings(queue: String, batchSize: Int)

  private type Checked[A] = Either[String, A]
  private type Setting[A] = OptionT[Checked, A]

  private def lookup(
    key: String,
    overrides: Map[String, String],
    defaults: Map[String, String]
  ): Setting[String] = {
    OptionT.fromOption[Checked](overrides.get(key))
      .orElse(OptionT.fromOption[Checked](defaults.get(key)))
      .map(_.trim)
  }

  private def nonBlankQueue(raw: String): Checked[String] = {
    Either.cond(raw.nonEmpty, raw, "queue must not be blank")
  }

  private def positiveBatchSize(raw: String): Checked[Int] = {
    raw.toIntOption.toRight("batchSize must be a whole number").flatMap { size =>
      Either.cond(size > 0, size, "batchSize must be positive")
    }
  }

  private def load(
    overrides: Map[String, String],
    defaults: Map[String, String]
  ): Checked[Option[Settings]] = {
    val settings: Setting[Settings] = for {
      queue <- lookup("queue", overrides, defaults).semiflatMap(nonBlankQueue)
      batchSize <- lookup("batchSize", overrides, defaults).semiflatMap(positiveBatchSize)
    } yield Settings(queue, batchSize)
    settings.value
  }

  @Test
  def combinesOverridesWithDefaultsAndNormalizesValues(): Unit = {
    val defaults: Map[String, String] = Map("queue" -> "general", "batchSize" -> "50")

    assertEquals(Right(Some(Settings("general", 50))), load(Map.empty, defaults))
    assertEquals(
      Right(Some(Settings("priority", 50))),
      load(Map("queue" -> " priority "), defaults)
    )
    assertEquals(
      Right(Some(Settings("general", 12))),
      load(Map("batchSize" -> " 12 "), defaults)
    )
    assertEquals(Right(Some(Settings("general", 50))), load(Map.empty, defaults))
  }

  @Test
  def reportsAbsentRequiredSettingsWithoutConstructingPartialSettings(): Unit = {
    assertEquals(Right(None), load(Map.empty, Map("batchSize" -> "50")))
    assertEquals(Right(None), load(Map("queue" -> "priority"), Map.empty))
    assertEquals(
      Right(Some(Settings("priority", 10))),
      load(Map("queue" -> "priority"), Map("batchSize" -> "10"))
    )
  }

  @Test
  def rejectsMalformedOverridesInsteadOfSilentlyUsingDefaults(): Unit = {
    val defaults: Map[String, String] = Map("queue" -> "general", "batchSize" -> "50")

    assertEquals(
      Left("queue must not be blank"),
      load(Map("queue" -> " "), defaults)
    )
    assertEquals(
      Left("batchSize must be a whole number"),
      load(Map("batchSize" -> "many"), defaults)
    )
    assertEquals(
      Left("batchSize must be positive"),
      load(Map("batchSize" -> "0"), defaults)
    )
    assertEquals(
      Left("queue must not be blank"),
      load(Map("queue" -> " ", "batchSize" -> "many"), defaults)
    )
  }
}
