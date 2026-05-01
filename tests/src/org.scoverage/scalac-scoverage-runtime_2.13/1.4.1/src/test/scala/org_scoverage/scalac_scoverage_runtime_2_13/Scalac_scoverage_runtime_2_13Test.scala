/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scoverage.scalac_scoverage_runtime_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scoverage.Invoker
import scoverage.Platform

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

final class Scalac_scoverage_runtime_2_13Test {
  @Test
  def invokedWritesEachStatementIdOnceAndCanReadTheMeasurementsBack(): Unit = {
    val measurementsDirectory: File = newMeasurementsDirectory("single-thread")
    val dataDir: String = measurementsDirectory.getAbsolutePath

    Invoker.invoked(101, dataDir)
    Invoker.invoked(202, dataDir)
    Invoker.invoked(101, dataDir)

    val measurementFile: File = Invoker.measurementFile(measurementsDirectory)
    assertThat(measurementFile).isFile()
    assertThat(measurementFile.getParentFile).isEqualTo(measurementsDirectory)
    assertThat(measurementFile.getName).startsWith("scoverage.measurements.")
    assertThat(Files.readAllLines(measurementFile.toPath, StandardCharsets.UTF_8)).containsExactly("101", "202")
    assertThat(invokedIds(measurementFile)).containsExactlyInAnyOrder(Integer.valueOf(101), Integer.valueOf(202))
  }

  @Test
  def measurementFileAndFinderOverloadsUseTheSameDirectorySemantics(): Unit = {
    val measurementsDirectory: File = newMeasurementsDirectory("overloads")
    val ignoredFile: File = new File(measurementsDirectory, "not-a-measurement.txt")
    val ignoredDirectory: File = new File(measurementsDirectory, "ordinary-directory")

    Files.writeString(ignoredFile.toPath, "999\n", StandardCharsets.UTF_8)
    assertThat(ignoredDirectory.mkdir()).isTrue()
    Invoker.invoked(303, measurementsDirectory.getAbsolutePath)

    val fromFile: File = Invoker.measurementFile(measurementsDirectory)
    val fromString: File = Invoker.measurementFile(measurementsDirectory.getAbsolutePath)
    assertThat(fromFile).isEqualTo(fromString)

    val foundFromFile: Array[File] = Invoker.findMeasurementFiles(measurementsDirectory)
    val foundFromString: Array[File] = Invoker.findMeasurementFiles(measurementsDirectory.getAbsolutePath)

    assertThat(fileNames(foundFromFile)).containsExactly(fromFile.getName)
    assertThat(fileNames(foundFromString)).containsExactly(fromFile.getName)
    assertThat(invokedIds(foundFromFile: _*)).containsExactly(Integer.valueOf(303))
  }

  @Test
  def invokedAppendsToAnExistingThreadMeasurementFileWithoutTruncatingIt(): Unit = {
    val measurementsDirectory: File = newMeasurementsDirectory("append-existing")
    val measurementFile: File = Invoker.measurementFile(measurementsDirectory)
    val dataDir: String = measurementsDirectory.getAbsolutePath

    Files.writeString(
      measurementFile.toPath,
      Seq("610", "").mkString(System.lineSeparator()),
      StandardCharsets.UTF_8
    )

    Invoker.invoked(611, dataDir)
    Invoker.invoked(612, dataDir)

    assertThat(Files.readAllLines(measurementFile.toPath, StandardCharsets.UTF_8)).containsExactly(
      "610",
      "611",
      "612"
    )
  }

  @Test
  def invokedKeepsIndependentMeasurementStateForEachDataDirectory(): Unit = {
    val firstDirectory: File = newMeasurementsDirectory("first-data-dir")
    val secondDirectory: File = newMeasurementsDirectory("second-data-dir")

    Invoker.invoked(404, firstDirectory.getAbsolutePath)
    Invoker.invoked(404, secondDirectory.getAbsolutePath)
    Invoker.invoked(505, secondDirectory.getAbsolutePath)

    val firstDirectoryFiles: IndexedSeq[File] = Invoker.findMeasurementFiles(firstDirectory).toIndexedSeq
    val secondDirectoryFiles: IndexedSeq[File] = Invoker.findMeasurementFiles(secondDirectory).toIndexedSeq

    assertThat(invokedIds(firstDirectoryFiles: _*)).containsExactly(Integer.valueOf(404))
    assertThat(invokedIds(secondDirectoryFiles: _*)).containsExactlyInAnyOrder(
      Integer.valueOf(404),
      Integer.valueOf(505)
    )
    assertThat(Files.readAllLines(Invoker.measurementFile(firstDirectory).toPath, StandardCharsets.UTF_8))
      .containsExactly("404")
    assertThat(Files.readAllLines(Invoker.measurementFile(secondDirectory).toPath, StandardCharsets.UTF_8))
      .containsExactly("404", "505")
  }

  @Test
  def invokedReaderMergesSeveralFilesDeduplicatesIdsAndIgnoresBlankLines(): Unit = {
    val measurementsDirectory: File = newMeasurementsDirectory("reader")
    val firstFile: File = writeMeasurementFile(measurementsDirectory, "first.measurements", "1", "", "2", "2")
    val secondFile: File = writeMeasurementFile(measurementsDirectory, "second.measurements", "3", "1", "")
    val emptyFile: File = writeMeasurementFile(measurementsDirectory, "empty.measurements")

    assertThat(invokedIds(firstFile, secondFile, emptyFile)).containsExactlyInAnyOrder(
      Integer.valueOf(1),
      Integer.valueOf(2),
      Integer.valueOf(3)
    )
  }

  @Test
  def concurrentInvocationsUseThreadSpecificMeasurementFilesAndASharedInvokedIdSet(): Unit = {
    val measurementsDirectory: File = newMeasurementsDirectory("concurrent")
    val dataDir: String = measurementsDirectory.getAbsolutePath
    val errors: ConcurrentLinkedQueue[Throwable] = new ConcurrentLinkedQueue[Throwable]()
    val expectedIds: Seq[Integer] = (0 until WorkerCount).flatMap { workerIndex =>
      val firstId: Int = 1000 + workerIndex * 10
      Seq(Integer.valueOf(firstId), Integer.valueOf(firstId + 1))
    }
    val threads: Seq[Thread] = (0 until WorkerCount).map { workerIndex =>
      new Thread(new Runnable {
        override def run(): Unit = {
          try {
            val firstId: Int = 1000 + workerIndex * 10
            Invoker.invoked(firstId, dataDir)
            Invoker.invoked(firstId + 1, dataDir)
            Invoker.invoked(firstId, dataDir)
          } catch {
            case throwable: Throwable => errors.add(throwable)
          }
        }
      }, s"scoverage-runtime-test-$workerIndex")
    }

    threads.foreach(_.start())
    threads.foreach(_.join())

    assertThat(errors).isEmpty()
    val measurementFiles: Array[File] = Invoker.findMeasurementFiles(measurementsDirectory)
    assertThat(measurementFiles).hasSize(WorkerCount)
    assertThat(invokedIds(measurementFiles: _*)).containsExactlyInAnyOrder(expectedIds: _*)
  }

  @Test
  def platformExposesScalaSourceAndThreadSafeMapFactoriesUsedByTheRuntime(): Unit = {
    val measurementsDirectory: File = newMeasurementsDirectory("platform")
    val dataFile: File = writeMeasurementFile(measurementsDirectory, "ids.txt", "41", "42")
    val source = Platform.Source.fromFile(dataFile)
    val sourceLines: List[String] = try source.getLines().toList finally source.close()
    val threadSafeMap = Platform.ThreadSafeMap.empty[String, Int]

    threadSafeMap.put("covered", sourceLines.map(_.toInt).sum)
    threadSafeMap.put("count", sourceLines.size)

    assertThat(sourceLines.asJava).containsExactly("41", "42")
    assertThat(threadSafeMap.apply("covered")).isEqualTo(83)
    assertThat(threadSafeMap.apply("count")).isEqualTo(2)
  }

  private def invokedIds(files: File*): java.util.Set[Integer] =
    Invoker.invoked(files.toSeq).map(id => Integer.valueOf(id)).asJava

  private def fileNames(files: Array[File]): java.util.List[String] =
    files.toSeq.map(_.getName).asJava

  private def newMeasurementsDirectory(testName: String): File =
    Files.createTempDirectory(s"scoverage-runtime-$testName-").toFile

  private def writeMeasurementFile(directory: File, name: String, lines: String*): File = {
    val file: File = new File(directory, name)
    val content: String = lines.mkString(System.lineSeparator())
    Files.writeString(file.toPath, content, StandardCharsets.UTF_8)
    file
  }

  private val WorkerCount: Int = 4
}
