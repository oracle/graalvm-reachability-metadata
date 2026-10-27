/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.CountDownLatch
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.duration._

/** Exercises CountDownLatch startup readiness for a directory loaded from parallel office imports. */
class StartupDirectoryReadinessTest {
  private case class Employee(id: String, name: String, department: String)

  private def importOffice(
      path: Path,
      directory: Ref[IO, Map[String, Employee]],
      ready: CountDownLatch[IO]
  ): IO[Unit] = {
    for {
      employees <- IO.blocking {
        Files.readString(path, UTF_8).linesIterator.map { line =>
          val fields: Array[String] = line.split(",")
          val employee: Employee = Employee(fields(0), fields(1), fields(2))
          employee.id -> employee
        }.toMap
      }
      _ <- directory.update(_ ++ employees)
      // An office is ready only after all its employees are visible to directory queries.
      _ <- ready.release
    } yield ()
  }

  private def findDepartment(
      department: String,
      directory: Ref[IO, Map[String, Employee]],
      ready: CountDownLatch[IO]
  ): IO[List[String]] = {
    ready.await *> directory.get.map { employees =>
      employees.values.filter(_.department == department).map(_.name).toList.sorted
    }
  }

  @Test
  @Timeout(30)
  def requestsWaitForEveryOfficeImportAndLaterRequestsUseTheReadyDirectory(): Unit = {
    val root: Path = Files.createTempDirectory("directory-startup-")
    val offices: List[(String, String)] = List(
      "north.csv" -> "N-1,Ada,engineering\nN-2,Ben,support\n",
      "south.csv" -> "S-1,Cora,support\nS-2,Dev,engineering\n",
      "west.csv" -> "W-1,Eva,engineering\nW-2,Finn,finance\n"
    )
    val paths: List[Path] = offices.map { case (name, _) => root.resolve(name) }

    try {
      offices.foreach { case (name, contents) =>
        Files.writeString(root.resolve(name), contents, UTF_8)
      }
      val program: IO[(List[List[String]], List[String])] = for {
        directory <- Ref.of[IO, Map[String, Employee]](Map.empty)
        ready <- CountDownLatch[IO](paths.size)
        // Requests and imports start together; no request may see a partially loaded directory.
        batch <- IO.both(
          List("engineering", "support", "finance").parTraverse { department =>
            findDepartment(department, directory, ready)
          },
          paths.parTraverse_(path => importOffice(path, directory, ready))
        )
        later <- findDepartment("engineering", directory, ready)
      } yield (batch._1, later)

      val (departments, later) = program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Directory startup did not finish within 20 seconds")
      }
      assertEquals(List(List("Ada", "Dev", "Eva"), List("Ben", "Cora"), List("Finn")),
        departments, "Every startup request must include employees from all office imports")
      assertEquals(List("Ada", "Dev", "Eva"), later,
        "Requests arriving after startup must pass the readiness gate without another import")
    } finally {
      paths.foreach(Files.deleteIfExists)
      Files.delete(root)
    }
  }
}
