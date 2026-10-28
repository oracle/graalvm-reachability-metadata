/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import cats.data.NonEmptyList
import com.typesafe.config.{ConfigRenderOptions, ConfigValue}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pureconfig.{ConfigReader, ConfigWriter}
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, KeyNotFound}
import pureconfig.generic.error.UnexpectedValueForFieldCoproductHint
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}
import pureconfig.module.cats.{EmptyTraversableFound, nonEmptyListReader, nonEmptyListWriter}
import pureconfig.module.yaml.YamlConfigSource

/**
 * Exercises derivation for recursive and type-parameterized types read from YAML: semiauto-derived
 * readers and writers for CI pipeline definitions whose steps form a recursive sealed family, whose
 * build matrix is a generic case class, and whose required lists are cats `NonEmptyList`s, loaded
 * from a multi-document YAML file and rendered back to YAML that reloads unchanged.
 */
class BuildPipelineDefinitionsTest {
  import BuildPipelineDefinitionsTest.*
  import BuildPipelineDefinitionsTest.given

  @TempDir
  var workspace: Path = null

  @Test
  def loadsEveryPipelineOfAMultiDocumentYamlFile(): Unit = {
    val pipelines: List[Pipeline] = loadPipelines(writeFile("pipelines.yaml", PipelinesYaml))

    assertEquals(List("verify", "release"), pipelines.map(_.name))

    val verify: Pipeline = pipelines.head
    assertEquals(NonEmptyList.of(Trigger("push", List("main", "release/*")), Trigger("pull-request", Nil)), verify.triggers)
    assertEquals(
      Some(
        Matrix(
          NonEmptyList.of(Target("linux", 21), Target("linux", 25), Target("macos", 25)),
          exclude = List(Target("macos", 21)),
          failFast = false
        )
      ),
      verify.matrix
    )
    assertEquals(
      NonEmptyList.of(
        Run("checkout", "git fetch --depth=1"),
        Parallel(
          "checks",
          NonEmptyList.of(
            Run("unit-tests", "./gradlew test", Map("CI" -> "true", "GRADLE_OPTS" -> "-Xmx2g")),
            Parallel(
              "lint",
              NonEmptyList.of(Run("checkstyle", "./gradlew checkstyle"), Run("spotless", "./gradlew spotlessCheck"))
            )
          ),
          maxConcurrency = Some(2)
        ),
        Cache(NonEmptyList.of("~/.gradle/caches", "~/.m2/repository"), key = "gradle-deps")
      ),
      verify.steps
    )
    assertEquals(4, countRuns(verify.steps))

    val release: Pipeline = pipelines(1)
    assertEquals(NonEmptyList.of(Trigger("tag", List("v*"))), release.triggers)
    assertEquals(None, release.matrix)
    assertEquals(
      NonEmptyList.of(Run("publish", "./gradlew publish", Map("SIGNING" -> "enabled")), Notify(NonEmptyList.of(7, 12))),
      release.steps
    )
  }

  @Test
  def rendersPipelinesAsYamlThatReloadsUnchanged(): Unit = {
    val pipelines: List[Pipeline] = loadPipelines(writeFile("pipelines.yaml", PipelinesYaml))
    val verify: Pipeline = pipelines.head

    val rendered: ConfigValue = ConfigWriter[Pipeline].to(verify)
    val json: String = rendered.render(ConfigRenderOptions.concise())
    assertTrue(json.contains("\"type\":\"parallel\""), json)
    assertTrue(json.contains("\"max-concurrency\":2"), json)
    assertTrue(json.contains("\"fail-fast\":false"), json)

    // JSON is a subset of YAML, so the concise rendering is a valid single-document YAML file.
    val reloaded: Pipeline = YamlConfigSource.file(writeFile("verify.yaml", json)).loadOrThrow[Pipeline]
    assertEquals(verify, reloaded)
  }

  @Test
  def reportsEmptyRequiredListsUnknownStepTypesAndMissingNestedFields(): Unit = {
    val brokenYaml: String = """name: broken
      |triggers:
      |  - event: push
      |    branches: [main]
      |matrix:
      |  include: []
      |steps:
      |  - type: deploy
      |    target: production
      |  - type: parallel
      |    name: checks
      |    steps:
      |      - type: run
      |        name: unit-tests
      |""".stripMargin

    YamlConfigSource.string(brokenYaml).load[Pipeline] match {
      case Right(pipeline) => fail(s"Expected the broken pipeline to be rejected, but loaded $pipeline")
      case Left(failures) =>
        val reasons: Map[String, String] = failureReasons(failures)
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(EmptyTraversableFound(_), _, "matrix.include") => true
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(UnexpectedValueForFieldCoproductHint(_), _, "steps.0.type") => true
            case _ => false
          },
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(KeyNotFound("command", _), _, "steps.1.steps.0") => true
            case _ => false
          },
          failures.prettyPrint()
        )
        assertEquals(3, reasons.size, failures.prettyPrint())
    }
  }

  private def writeFile(name: String, content: String): Path = {
    val file: Path = workspace.resolve(name)
    Files.writeString(file, content, StandardCharsets.UTF_8)
    file
  }

  private def loadPipelines(file: Path): List[Pipeline] =
    YamlConfigSource.file(file).multiDoc.loadOrThrow[List[Pipeline]]

  private def countRuns(steps: NonEmptyList[Step]): Int =
    steps.toList.map {
      case Parallel(_, nested, _) => countRuns(nested)
      case _: Run => 1
      case _ => 0
    }.sum

  private def failureReasons(failures: ConfigReaderFailures): Map[String, String] =
    failures.toList.collect { case ConvertFailure(reason, _, path) => path -> reason.description }.toMap
}

object BuildPipelineDefinitionsTest {

  /** One job of a pipeline; `parallel` steps nest further steps, making the family recursive. */
  sealed trait Step
  final case class Run(name: String, command: String, env: Map[String, String] = Map.empty) extends Step
  final case class Parallel(name: String, steps: NonEmptyList[Step], maxConcurrency: Option[Int] = None)
      extends Step
  final case class Cache(paths: NonEmptyList[String], key: String) extends Step
  final case class Notify(channelIds: NonEmptyList[Int]) extends Step

  given ConfigReader[Step] = deriveReader[Step]
  given ConfigWriter[Step] = deriveWriter[Step]

  final case class Trigger(event: String, branches: List[String] = Nil)

  given ConfigReader[Trigger] = deriveReader[Trigger]
  given ConfigWriter[Trigger] = deriveWriter[Trigger]

  /** A build matrix over any kind of target, derived once for every element type. */
  final case class Matrix[A](include: NonEmptyList[A], exclude: List[A] = Nil, failFast: Boolean = true)

  given matrixReader[A](using ConfigReader[A]): ConfigReader[Matrix[A]] = deriveReader[Matrix[A]]
  given matrixWriter[A](using ConfigWriter[A]): ConfigWriter[Matrix[A]] = deriveWriter[Matrix[A]]

  final case class Target(os: String, jdk: Int)

  given ConfigReader[Target] = deriveReader[Target]
  given ConfigWriter[Target] = deriveWriter[Target]

  final case class Pipeline(
      name: String,
      triggers: NonEmptyList[Trigger],
      matrix: Option[Matrix[Target]],
      steps: NonEmptyList[Step]
  )

  given ConfigReader[Pipeline] = deriveReader[Pipeline]
  given ConfigWriter[Pipeline] = deriveWriter[Pipeline]

  val PipelinesYaml: String = """name: verify
    |triggers:
    |  - event: push
    |    branches: [main, "release/*"]
    |  - event: pull-request
    |matrix:
    |  include:
    |    - {os: linux, jdk: 21}
    |    - {os: linux, jdk: 25}
    |    - {os: macos, jdk: 25}
    |  exclude:
    |    - {os: macos, jdk: 21}
    |  fail-fast: false
    |steps:
    |  - type: run
    |    name: checkout
    |    command: git fetch --depth=1
    |  - type: parallel
    |    name: checks
    |    max-concurrency: 2
    |    steps:
    |      - type: run
    |        name: unit-tests
    |        command: ./gradlew test
    |        env:
    |          CI: "true"
    |          GRADLE_OPTS: -Xmx2g
    |      - type: parallel
    |        name: lint
    |        steps:
    |          - {type: run, name: checkstyle, command: ./gradlew checkstyle}
    |          - {type: run, name: spotless, command: ./gradlew spotlessCheck}
    |  - type: cache
    |    key: gradle-deps
    |    paths: [~/.gradle/caches, ~/.m2/repository]
    |---
    |name: release
    |triggers:
    |  - event: tag
    |    branches: ["v*"]
    |matrix: null
    |steps:
    |  - type: run
    |    name: publish
    |    command: ./gradlew publish
    |    env: {SIGNING: enabled}
    |  - type: notify
    |    channel-ids: [7, 12]
    |""".stripMargin
}
