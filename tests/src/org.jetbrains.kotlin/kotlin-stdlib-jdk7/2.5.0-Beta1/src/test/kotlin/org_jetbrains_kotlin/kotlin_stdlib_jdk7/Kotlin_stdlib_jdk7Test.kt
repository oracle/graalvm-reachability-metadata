/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package org_jetbrains_kotlin.kotlin_stdlib_jdk7

import java.nio.file.Files
import java.nio.file.Path as JdkPath
import java.util.Comparator
import kotlin.io.path.Path
import kotlin.io.path.appendText
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.relativeToOrSelf
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlin_stdlib_jdk7Test {
    @Test
    public fun pathPropertiesExposeNamesExtensionsAndInvariantSeparators() {
        val reportPath: JdkPath = Path("workspace", "reports", "metadata-v1.json")
        val hiddenPath: JdkPath = Path("workspace", ".metadata")
        val extensionlessPath: JdkPath = Path("workspace", "README")

        assertThat(reportPath.name).isEqualTo("metadata-v1.json")
        assertThat(reportPath.nameWithoutExtension).isEqualTo("metadata-v1")
        assertThat(reportPath.extension).isEqualTo("json")
        assertThat(reportPath.invariantSeparatorsPathString).isEqualTo("workspace/reports/metadata-v1.json")

        assertThat(hiddenPath.name).isEqualTo(".metadata")
        assertThat(hiddenPath.nameWithoutExtension).isEmpty()
        assertThat(hiddenPath.extension).isEqualTo("metadata")

        assertThat(extensionlessPath.nameWithoutExtension).isEqualTo("README")
        assertThat(extensionlessPath.extension).isEmpty()
    }

    @Test
    public fun relativePathOperationsHandleNestedPathsAndMismatchedRoots() {
        withTemporaryDirectory { directory: JdkPath ->
            val baseDirectory: JdkPath = directory.toAbsolutePath()
            val metadataFile: JdkPath = baseDirectory.resolve("metadata").resolve("reachability.json")
            val relativeMetadataFile: JdkPath = Path("metadata", "reachability.json")
            val relativeBaseDirectory: JdkPath = Path("relative-base")

            assertThat(metadataFile.relativeTo(baseDirectory)).isEqualTo(relativeMetadataFile)
            assertThat(metadataFile.relativeToOrSelf(baseDirectory)).isEqualTo(relativeMetadataFile)
            assertThat(metadataFile.relativeTo(baseDirectory).invariantSeparatorsPathString)
                .isEqualTo("metadata/reachability.json")

            assertThat(metadataFile.relativeToOrNull(relativeBaseDirectory)).isNull()
            assertThat(metadataFile.relativeToOrSelf(relativeBaseDirectory)).isEqualTo(metadataFile)
        }
    }

    @Test
    public fun pathReadWriteAppendAndSizeOperateOnUtf8Files() {
        withTemporaryDirectory { directory: JdkPath ->
            val metadataFile: JdkPath = directory.resolve("unicode-metadata.txt")

            metadataFile.writeText("library=org.jetbrains.kotlin\nstatus=initial", Charsets.UTF_8)
            metadataFile.appendText("\nsummary=JDK 7 path support ✓", Charsets.UTF_8)

            assertThat(metadataFile.exists()).isTrue()
            assertThat(metadataFile.isRegularFile()).isTrue()
            assertThat(metadataFile.readText(Charsets.UTF_8)).isEqualTo(
                "library=org.jetbrains.kotlin\nstatus=initial\nsummary=JDK 7 path support ✓"
            )
            assertThat(metadataFile.fileSize()).isEqualTo(Files.size(metadataFile))
        }
    }

    @Test
    public fun directoryListingAndDeletionFilterEntriesByGlob() {
        withTemporaryDirectory { directory: JdkPath ->
            val alphaFile: JdkPath = directory.resolve("alpha.txt")
            val betaFile: JdkPath = directory.resolve("beta.txt")
            val jsonFile: JdkPath = directory.resolve("metadata.json")

            alphaFile.writeText("alpha", Charsets.UTF_8)
            betaFile.writeText("beta", Charsets.UTF_8)
            jsonFile.writeText("{}", Charsets.UTF_8)

            val textFileNames: List<String> = directory.listDirectoryEntries("*.txt")
                .map { path: JdkPath -> path.name }
                .sorted()

            assertThat(textFileNames).containsExactly("alpha.txt", "beta.txt")
            assertThat(jsonFile.exists()).isTrue()
            assertThat(alphaFile.deleteIfExists()).isTrue()
            assertThat(alphaFile.deleteIfExists()).isFalse()
        }
    }

    @Test
    public fun copyToCopiesFileContentsAndCanReplaceExistingTarget() {
        withTemporaryDirectory { directory: JdkPath ->
            val sourceFile: JdkPath = directory.resolve("source.txt")
            val targetFile: JdkPath = directory.resolve("target.txt")

            sourceFile.writeText("status=initial", Charsets.UTF_8)
            val copiedFile: JdkPath = sourceFile.copyTo(targetFile)

            assertThat(copiedFile).isEqualTo(targetFile)
            assertThat(targetFile.readText(Charsets.UTF_8)).isEqualTo("status=initial")

            sourceFile.writeText("status=updated", Charsets.UTF_8)
            val replacedFile: JdkPath = sourceFile.copyTo(targetFile, overwrite = true)

            assertThat(replacedFile).isEqualTo(targetFile)
            assertThat(targetFile.readText(Charsets.UTF_8)).isEqualTo("status=updated")
        }
    }

    @Test
    public fun tempFactoriesCreateFilesAndDirectoriesUnderRequestedParent() {
        withTemporaryDirectory { parentDirectory: JdkPath ->
            val generatedDirectory: JdkPath = createTempDirectory(parentDirectory, "generated-dir-")
            val generatedFile: JdkPath = createTempFile(generatedDirectory, "metadata-", ".json")

            generatedFile.writeText("{\"library\":\"kotlin-stdlib-jdk7\"}", Charsets.UTF_8)

            assertThat(generatedDirectory.parent).isEqualTo(parentDirectory)
            assertThat(generatedDirectory.name).startsWith("generated-dir-")
            assertThat(generatedDirectory.exists()).isTrue()
            assertThat(generatedDirectory.isDirectory()).isTrue()

            assertThat(generatedFile.parent).isEqualTo(generatedDirectory)
            assertThat(generatedFile.name).startsWith("metadata-").endsWith(".json")
            assertThat(generatedFile.isRegularFile()).isTrue()
            assertThat(generatedFile.readText(Charsets.UTF_8)).isEqualTo("{\"library\":\"kotlin-stdlib-jdk7\"}")
        }
    }

    @Test
    public fun moveToMovesFileContentsAndCanReplaceExistingTarget() {
        withTemporaryDirectory { directory: JdkPath ->
            val sourceFile: JdkPath = directory.resolve("source.txt")
            val targetFile: JdkPath = directory.resolve("target.txt")

            sourceFile.writeText("status=ready", Charsets.UTF_8)
            val movedFile: JdkPath = sourceFile.moveTo(targetFile)

            assertThat(movedFile).isEqualTo(targetFile)
            assertThat(sourceFile.exists()).isFalse()
            assertThat(targetFile.readText(Charsets.UTF_8)).isEqualTo("status=ready")

            val replacementFile: JdkPath = directory.resolve("replacement.txt")
            replacementFile.writeText("status=replacement", Charsets.UTF_8)
            val replacedFile: JdkPath = replacementFile.moveTo(targetFile, overwrite = true)

            assertThat(replacedFile).isEqualTo(targetFile)
            assertThat(replacementFile.exists()).isFalse()
            assertThat(targetFile.readText(Charsets.UTF_8)).isEqualTo("status=replacement")
        }
    }

    private fun withTemporaryDirectory(block: (JdkPath) -> Unit) {
        val directory: JdkPath = Files.createTempDirectory("kotlin-stdlib-jdk7-test-")
        try {
            block(directory)
        } finally {
            deleteRecursively(directory)
        }
    }

    private fun deleteRecursively(root: JdkPath) {
        if (Files.notExists(root)) {
            return
        }
        val paths = Files.walk(root)
        try {
            paths.sorted(Comparator.reverseOrder()).forEach { path: JdkPath ->
                Files.deleteIfExists(path)
            }
        } finally {
            paths.close()
        }
    }
}
