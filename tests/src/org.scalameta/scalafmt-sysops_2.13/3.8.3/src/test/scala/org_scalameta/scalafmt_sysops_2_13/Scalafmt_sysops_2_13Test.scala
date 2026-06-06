/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.scalafmt_sysops_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.scalafmt.sysops.AbsoluteFile
import org.scalafmt.sysops.BatchPathFinder
import org.scalafmt.sysops.FileOps
import org.scalafmt.sysops.GitOps
import org.scalafmt.sysops.OsSpecific

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import scala.io.Codec
import scala.jdk.CollectionConverters._
import scala.util.Success

class Scalafmt_sysops_2_13Test {
  @Test
  def absoluteFileWrapsPathsAndPerformsFilesystemOperations(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      implicit val codec: Codec = Codec.UTF8
      val root: AbsoluteFile = AbsoluteFile(tempDirectory)
      val nested: AbsoluteFile = root / "nested"
      val file: AbsoluteFile = nested / Paths.get("example.scala")

      assertTrue(root.exists)
      assertTrue(root.isDirectory)
      assertEquals(tempDirectory.toAbsolutePath, root.path)
      assertEquals(tempDirectory.toUri, root.toUri)
      assertEquals(tempDirectory.toFile, root.jfile)
      assertEquals(tempDirectory.getFileName.toString, root.getFileName)

      nested.mkdirs()
      assertTrue(nested.isDirectory)
      file.writeFile("object Example\n")
      assertTrue(file.exists)
      assertTrue(file.isRegularFile)
      assertTrue(file.isRegularFileNoLinks)
      assertEquals("object Example\n", file.readFile)
      assertEquals(nested.path, file.parent.path)
      assertEquals("example.scala", file.getFileName)
      assertTrue(file.attributes.size() > 0L)

      val listedNames: Set[String] = root.listFiles.map(_.getFileName).toSet
      assertEquals(Set("example.scala"), listedNames)

      val joined: Seq[AbsoluteFile] = root.join(Seq(Paths.get("a"), Paths.get("b")))
      assertEquals(Seq(root.path.resolve("a"), root.path.resolve("b")), joined.map(_.path))
      assertEquals(file.path, AbsoluteFile(file.jfile).path)
      assertTrue(AbsoluteFile.fromPathIfAbsolute(file.path.toString).contains(file))
      assertTrue(AbsoluteFile.fromPathIfAbsolute("relative/path").isEmpty)
      assertNotNull(AbsoluteFile.userDir.path)

      file.delete()
      assertFalse(file.exists)
    }
  }

  @Test
  def fileOpsReadsWritesClassifiesAndListsFiles(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      implicit val codec: Codec = Codec.UTF8
      val root: Path = tempDirectory.toAbsolutePath
      val scalaFile: Path = root.resolve("src/Main.scala")
      val markdownFile: Path = root.resolve("README.md")
      val ammoniteFile: Path = root.resolve("script.sc")
      val sbtFile: Path = root.resolve("build.sbt")
      Files.createDirectories(scalaFile.getParent)

      FileOps.writeFile(scalaFile, "object Main\n")
      FileOps.writeFile(markdownFile.toString, "# Documentation\n")
      FileOps.writeFile(ammoniteFile, "println(1)\n")
      FileOps.writeFile(sbtFile, "name := \"demo\"\n")

      assertEquals("object Main\n", FileOps.readFile(scalaFile))
      assertEquals("# Documentation\n", FileOps.readFile(markdownFile.toString))
      assertEquals("object Main\n", FileOps.readFile(scalaFile.toUri.toURL))
      assertEquals("object Main\n", FileOps.readFile(scalaFile.toUri.toURL.toString))

      assertTrue(FileOps.isRegularFile(scalaFile))
      assertTrue(FileOps.isRegularFileNoLinks(scalaFile))
      assertTrue(FileOps.isDirectory(root))
      assertTrue(FileOps.isDirectoryNoLinks(root))
      assertFalse(FileOps.isRegularFile(root))
      assertTrue(FileOps.getAttributes(scalaFile).isRegularFile)
      assertTrue(FileOps.getAttributesNoLinks(scalaFile).isRegularFile)
      assertTrue(FileOps.getLastModifiedMsec(scalaFile) > 0L)
      assertTrue(FileOps.getLastModifiedMsecNoLinks(scalaFile) > 0L)

      assertTrue(FileOps.isMarkdown("README.md"))
      assertFalse(FileOps.isMarkdown("README.markdown"))
      assertTrue(FileOps.isAmmonite("build.sc"))
      assertFalse(FileOps.isAmmonite("Main.scala"))
      assertTrue(FileOps.isSbt("build.sbt"))
      assertFalse(FileOps.isSbt("plugin.scala"))
      assertEquals(".scalafmt.conf", FileOps.defaultConfigFileName)

      val allFiles: Set[String] = FileOps.listFiles(root).map(_.getFileName.toString).toSet
      assertEquals(Set("Main.scala", "README.md", "script.sc", "build.sbt"), allFiles)

      val scalaFiles: Seq[Path] = FileOps.listFiles(
        root,
        (path: Path, attributes: BasicFileAttributes) =>
          attributes.isRegularFile && path.getFileName.toString.endsWith(".scala")
      )
      assertEquals(Seq(scalaFile), scalaFiles)

      assertEquals(root.resolve("src").resolve("Main.scala"), FileOps.getPath(root.toString, "src", "Main.scala"))
      assertEquals(scalaFile, FileOps.getFile(scalaFile.toString))
      assertEquals(scalaFile, FileOps.getFile(Seq(root.toString, "src", "Main.scala")))
    }
  }

  @Test
  def fileOpsDistinguishesSymbolicLinksFromTargets(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      implicit val codec: Codec = Codec.UTF8
      val root: Path = tempDirectory.toAbsolutePath
      val targetDirectory: Path = root.resolve("target-directory")
      val targetFile: Path = targetDirectory.resolve("target.txt")
      val fileLink: Path = root.resolve("file-link.txt")
      val directoryLink: Path = root.resolve("directory-link")
      Files.createDirectories(targetDirectory)
      FileOps.writeFile(targetFile, "linked content\n")
      Files.createSymbolicLink(fileLink, targetFile)
      Files.createSymbolicLink(directoryLink, targetDirectory)

      assertTrue(FileOps.isRegularFile(fileLink))
      assertFalse(FileOps.isRegularFileNoLinks(fileLink))
      assertTrue(FileOps.isDirectory(directoryLink))
      assertFalse(FileOps.isDirectoryNoLinks(directoryLink))
      assertEquals("linked content\n", FileOps.readFile(fileLink))
      assertTrue(FileOps.getAttributes(fileLink).isRegularFile)
      assertTrue(FileOps.getAttributesNoLinks(fileLink).isSymbolicLink)

      val linkedAbsoluteFile: AbsoluteFile = AbsoluteFile(fileLink)
      assertTrue(linkedAbsoluteFile.isRegularFile)
      assertFalse(linkedAbsoluteFile.isRegularFileNoLinks)
    }
  }

  @Test
  def fileOpsFindsAndValidatesConfigurationFiles(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      implicit val codec: Codec = Codec.UTF8
      val root: AbsoluteFile = AbsoluteFile(tempDirectory)
      val defaultConfig: AbsoluteFile = root / FileOps.defaultConfigFileName
      val explicitConfig: Path = Paths.get("project/scalafmt.conf")
      val explicitConfigFile: AbsoluteFile = root / explicitConfig
      explicitConfigFile.parent.mkdirs()

      assertTrue(FileOps.tryGetConfigInDir(root).isEmpty)
      assertTrue(FileOps.getCanonicalConfigFile(root).isEmpty)

      defaultConfig.writeFile("version = 3\n")
      val defaultLookup = FileOps.tryGetConfigInDir(root)
      assertTrue(defaultLookup.isDefined)
      assertEquals(Success(defaultConfig.path), defaultLookup.get)
      assertEquals(Success(defaultConfig.path), FileOps.getCanonicalConfigFile(root).get)

      explicitConfigFile.writeFile("runner.dialect = scala213\n")
      assertEquals(
        Success(explicitConfigFile.path),
        FileOps.getCanonicalConfigFile(root, Some(explicitConfig)).get
      )

      val missing = FileOps.getCanonicalConfigFile(root, Some(Paths.get("missing.conf"))).get
      assertTrue(missing.isFailure)
      assertTrue(missing.failed.get.isInstanceOf[NoSuchFileException])

      val configDirectory: AbsoluteFile = root / "directory.conf"
      configDirectory.mkdir()
      val directoryResult = FileOps.getCanonicalConfigFile(root, Some(Paths.get("directory.conf"))).get
      assertTrue(directoryResult.isFailure)
      assertTrue(directoryResult.failed.get.getMessage.contains("Config not a file"))
    }
  }

  @Test
  def fileMatcherAcceptsExplicitFilesAndDirectoryDescendantsOnly(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      val root: Path = tempDirectory.toAbsolutePath
      val selectedDirectory: Path = root.resolve("selected")
      val selectedFile: Path = root.resolve("explicit.txt")
      val nestedFile: Path = selectedDirectory.resolve("child.scala")
      val similarlyNamedSibling: Path = root.resolve("selected-sibling.scala")
      Files.createDirectories(selectedDirectory)
      Files.write(selectedFile, Array[Byte](1))
      Files.write(nestedFile, Array[Byte](2))
      Files.write(similarlyNamedSibling, Array[Byte](3))

      val matcher: Path => Boolean = FileOps.getFileMatcher(Seq(selectedDirectory, selectedFile))

      assertTrue(matcher(selectedDirectory))
      assertTrue(matcher(nestedFile))
      assertTrue(matcher(selectedFile))
      assertFalse(matcher(similarlyNamedSibling))
      assertFalse(matcher(root.resolve("other.txt")))
    }
  }

  @Test
  def dirBatchPathFinderDiscoversMatchingFilesAndHandlesExplicitPaths(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      implicit val codec: Codec = Codec.UTF8
      val root: AbsoluteFile = AbsoluteFile(tempDirectory)
      val sourceDirectory: AbsoluteFile = root / "src"
      val nestedDirectory: AbsoluteFile = sourceDirectory / "nested"
      nestedDirectory.mkdirs()
      val scalaFile: AbsoluteFile = sourceDirectory / "Main.scala"
      val nestedScalaFile: AbsoluteFile = nestedDirectory / "Nested.scala"
      val markdownFile: AbsoluteFile = root / "README.md"
      scalaFile.writeFile("object Main\n")
      nestedScalaFile.writeFile("object Nested\n")
      markdownFile.writeFile("# Readme\n")

      val finder = new BatchPathFinder.DirFiles(root)(_.getFileName.toString.endsWith(".scala"))

      assertEquals(Set(scalaFile, nestedScalaFile), finder.findFiles().toSet)
      assertEquals(Set(scalaFile, nestedScalaFile), finder.findFiles(sourceDirectory).toSet)
      assertEquals(Seq.empty, finder.findFilesExplicit(Seq.empty))

      val unfilteredExplicitFiles: Seq[AbsoluteFile] = finder.findMatchingFiles(
        false,
        markdownFile,
        sourceDirectory
      )
      assertTrue(unfilteredExplicitFiles.contains(markdownFile))
      assertTrue(unfilteredExplicitFiles.contains(scalaFile))
      assertTrue(unfilteredExplicitFiles.contains(nestedScalaFile))

      val filteredExplicitFiles: Seq[AbsoluteFile] = finder.findMatchingFiles(
        true,
        markdownFile,
        scalaFile,
        sourceDirectory
      )
      assertFalse(filteredExplicitFiles.contains(markdownFile))
      assertTrue(filteredExplicitFiles.contains(scalaFile))
      assertTrue(filteredExplicitFiles.contains(nestedScalaFile))
    }
  }

  @Test
  def gitBackedBatchPathFindersDelegateAndFilterResults(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      val root: AbsoluteFile = AbsoluteFile(tempDirectory)
      val scalaFile: AbsoluteFile = root / "Changed.scala"
      val markdownFile: AbsoluteFile = root / "README.md"
      val directory: AbsoluteFile = root / "src"
      val gitOps = new RecordingGitOps(
        rootOption = Some(root),
        statusResult = Seq(scalaFile, markdownFile),
        diffResult = Seq(scalaFile, markdownFile),
        lsTreeResult = Seq(scalaFile, markdownFile)
      )
      val matchesScala: Path => Boolean = _.getFileName.toString.endsWith(".scala")

      val gitFiles = new BatchPathFinder.GitFiles(gitOps)(matchesScala)
      assertEquals(Seq(scalaFile), gitFiles.findFiles(directory))
      assertEquals(Seq(directory), gitOps.lastLsTreeDirs)

      val dirtyFiles = new BatchPathFinder.GitDirtyFiles(gitOps)(matchesScala)
      assertEquals(Seq(scalaFile), dirtyFiles.findFiles(directory))
      assertEquals(Seq(directory), gitOps.lastStatusDirs)

      val branchFiles = new BatchPathFinder.GitBranchFiles(gitOps, "main")(matchesScala)
      assertEquals(Seq(scalaFile), branchFiles.findFiles(directory))
      assertEquals("main", gitOps.lastDiffBranch)
      assertEquals(Seq(directory), gitOps.lastDiffDirs)
    }
  }

  @Test
  def gitOpsImplDiscoversRepositoryAndReportsTrackedDirtyAndDiffFiles(): Unit = {
    withTempDirectory { tempDirectory: Path =>
      implicit val codec: Codec = Codec.UTF8
      val repository: Path = tempDirectory.toAbsolutePath
      val sourceDirectory: Path = repository.resolve("src")
      val trackedFile: Path = sourceDirectory.resolve("Tracked.scala")
      val unchangedFile: Path = repository.resolve("README.md")
      val untrackedFile: Path = sourceDirectory.resolve("Untracked.scala")
      val baselineBranch: String = "baseline-for-scalafmt-sysops-test"

      runCommand(repository, "git", "init")
      runCommand(repository, "git", "config", "user.email", "test@example.invalid")
      runCommand(repository, "git", "config", "user.name", "Scalafmt Sysops Test")
      runCommand(repository, "git", "config", "commit.gpgsign", "false")
      runCommand(repository, "git", "checkout", "-b", baselineBranch)
      Files.createDirectories(sourceDirectory)
      FileOps.writeFile(trackedFile, "object Tracked\n")
      FileOps.writeFile(unchangedFile, "# Readme\n")
      runCommand(repository, "git", "add", ".")
      runCommand(repository, "git", "commit", "-m", "initial commit")
      runCommand(repository, "git", "checkout", "-b", "feature")
      FileOps.writeFile(trackedFile, "object Tracked { val value = 1 }\n")
      FileOps.writeFile(untrackedFile, "object Untracked\n")

      val gitOps: GitOps = GitOps.FactoryImpl(AbsoluteFile(repository))
      val repositoryRoot: Option[AbsoluteFile] = gitOps.rootDir
      assertTrue(repositoryRoot.isDefined)
      assertEquals(repository, repositoryRoot.get.path)

      val trackedFiles: Set[AbsoluteFile] = gitOps.lsTree().toSet
      assertTrue(trackedFiles.contains(AbsoluteFile(trackedFile)))
      assertTrue(trackedFiles.contains(AbsoluteFile(unchangedFile)))
      assertFalse(trackedFiles.contains(AbsoluteFile(untrackedFile)))

      val dirtyFiles: Set[AbsoluteFile] = gitOps.status().toSet
      assertEquals(Set(AbsoluteFile(trackedFile), AbsoluteFile(untrackedFile)), dirtyFiles)
      assertEquals(dirtyFiles, gitOps.status(AbsoluteFile(sourceDirectory)).toSet)

      val filesChangedFromBaseline: Seq[AbsoluteFile] = gitOps.diff(baselineBranch)
      assertEquals(Seq(AbsoluteFile(trackedFile)), filesChangedFromBaseline)
      assertEquals(filesChangedFromBaseline, gitOps.diff(baselineBranch, AbsoluteFile(sourceDirectory)))
    }
  }

  @Test
  def gitOpsExtensionMethodsResolveRootAndProposedConfigurationFiles(): Unit = {
    import org.scalafmt.sysops.GitOps._

    withTempDirectory { tempDirectory: Path =>
      implicit val codec: Codec = Codec.UTF8
      val workspace: AbsoluteFile = AbsoluteFile(tempDirectory)
      val gitRoot: AbsoluteFile = workspace / "repo"
      val cwd: AbsoluteFile = gitRoot / "module"
      cwd.mkdirs()
      val rootConfig: AbsoluteFile = gitRoot / FileOps.defaultConfigFileName
      rootConfig.writeFile("version = 3\n")
      val localConfig: AbsoluteFile = cwd / FileOps.defaultConfigFileName
      localConfig.writeFile("runner.dialect = scala213\n")

      val gitWithRoot = new RecordingGitOps(rootOption = Some(gitRoot))
      val gitWithoutRoot = new RecordingGitOps(rootOption = None)

      assertEquals(Success(localConfig.path), gitWithRoot.getCanonicalConfigFile(cwd).get)
      Files.delete(localConfig.path)
      assertEquals(Success(rootConfig.path), gitWithRoot.getCanonicalConfigFile(cwd).get)
      assertEquals(Success(rootConfig.path), gitWithRoot.getRootConfigFile.get)
      assertEquals(gitRoot.path.resolve(FileOps.defaultConfigFileName), gitWithRoot.getProposedConfigFile(cwd).path)
      assertEquals(cwd.path.resolve("custom.conf"), gitWithRoot.getProposedConfigFile(cwd, Some(Paths.get("custom.conf"))).path)
      assertEquals(cwd.path.resolve(FileOps.defaultConfigFileName), gitWithoutRoot.getProposedConfigFile(cwd).path)
      assertTrue(gitWithoutRoot.getRootConfigFile.isEmpty)

      val created: GitOps = GitOps.FactoryImpl(workspace)
      assertNotNull(created)
    }
  }

  @Test
  def osSpecificHelpersReflectCurrentPlatformSeparator(): Unit = {
    import org.scalafmt.sysops.OsSpecific.XtensionStringAsFilename

    assertEquals(File.separatorChar == '\\', OsSpecific.isWindows)
    val pattern: String = "src/main/scala/*.scala"
    val expectedPattern: String =
      if (OsSpecific.isWindows) "src\\\\main\\\\scala\\\\*.scala" else pattern

    assertEquals(expectedPattern, OsSpecific.fixSeparatorsInPathPattern(pattern))
    assertEquals(expectedPattern, pattern.asFilename)
  }

  private def withTempDirectory(testBody: Path => Unit): Unit = {
    val directory: Path = Files.createTempDirectory("scalafmt-sysops-test-")
    try testBody(directory)
    finally deleteRecursively(directory)
  }

  private def runCommand(workingDirectory: Path, command: String*): Unit = {
    val process = new ProcessBuilder(command: _*)
      .directory(workingDirectory.toFile)
      .redirectErrorStream(true)
      .start()
    val finished: Boolean = process.waitFor(30, TimeUnit.SECONDS)
    if (!finished) {
      process.destroyForcibly()
      process.waitFor(5, TimeUnit.SECONDS)
    }
    val output: String =
      if (process.isAlive) "" else new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    assertTrue(finished, s"Command timed out: ${command.mkString(" ")}\n$output")
    assertEquals(0, process.exitValue(), s"Command failed: ${command.mkString(" ")}\n$output")
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toSeq.reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }

  private final class RecordingGitOps(
      rootOption: Option[AbsoluteFile],
      statusResult: Seq[AbsoluteFile] = Seq.empty,
      diffResult: Seq[AbsoluteFile] = Seq.empty,
      lsTreeResult: Seq[AbsoluteFile] = Seq.empty
  ) extends GitOps {
    var lastStatusDirs: Seq[AbsoluteFile] = Seq.empty
    var lastDiffBranch: String = ""
    var lastDiffDirs: Seq[AbsoluteFile] = Seq.empty
    var lastLsTreeDirs: Seq[AbsoluteFile] = Seq.empty

    override def status(dir: AbsoluteFile*): Seq[AbsoluteFile] = {
      lastStatusDirs = dir
      statusResult
    }

    override def diff(branch: String, dir: AbsoluteFile*): Seq[AbsoluteFile] = {
      lastDiffBranch = branch
      lastDiffDirs = dir
      diffResult
    }

    override def lsTree(dir: AbsoluteFile*): Seq[AbsoluteFile] = {
      lastLsTreeDirs = dir
      lsTreeResult
    }

    override def rootDir: Option[AbsoluteFile] = rootOption
  }
}
