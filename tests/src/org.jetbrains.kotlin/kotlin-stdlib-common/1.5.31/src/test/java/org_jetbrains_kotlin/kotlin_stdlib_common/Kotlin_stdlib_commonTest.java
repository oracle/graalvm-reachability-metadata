/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Kotlin_stdlib_commonTest {

    @Test
    void metadataCompilerAcceptsBroadStdlibCommonApiSurface(@TempDir Path tempDir) throws Exception {
        assumeFalse(isNativeImageRuntime());

        Path stdlibCommonJar = findClasspathEntry("kotlin-stdlib-common");
        Path sourceFile = writeKotlinSource(tempDir.resolve("broad-api"), "ApiSurface.kt", """
                package sample.api

                import kotlin.DeepRecursiveFunction
                import kotlin.ExperimentalUnsignedTypes
                import kotlin.ExperimentalStdlibApi
                import kotlin.KotlinVersion
                import kotlin.Lazy
                import kotlin.LazyThreadSafetyMode
                import kotlin.Pair
                import kotlin.Result
                import kotlin.Triple
                import kotlin.comparisons.compareBy
                import kotlin.contracts.ExperimentalContracts
                import kotlin.contracts.contract
                import kotlin.coroutines.AbstractCoroutineContextElement
                import kotlin.coroutines.CoroutineContext
                import kotlin.coroutines.EmptyCoroutineContext
                import kotlin.sequences.asSequence
                import kotlin.text.Regex

                class ApiSurface {
                    val versionIsAvailable: Boolean = KotlinVersion.CURRENT.isAtLeast(1, 5)

                    val lazyText: Lazy<String> = lazy(LazyThreadSafetyMode.NONE) {
                        buildString {
                            append("std")
                            append("lib")
                        }
                    }

                    fun pairSummary(values: List<String>): Pair<Int, String> = values.size to values.joinToString("-")

                    fun tripleSummary(range: IntRange): Triple<Int, Int, Int> = Triple(range.first, range.last, range.count())

                    fun regexPipeline(input: String): List<String> =
                        Regex("[A-Za-z]+")
                            .findAll(input)
                            .asSequence()
                            .map { it.value.lowercase() }
                            .distinct()
                            .sorted()
                            .toList()

                    fun compareByLength(values: List<String>): List<String> =
                        values.sortedWith(compareBy<String> { it.length }.thenBy { it })

                    val context: CoroutineContext = EmptyCoroutineContext + NamedElement("surface")

                    @OptIn(ExperimentalStdlibApi::class)
                    fun recursionDepth(limit: Int): Int =
                        DeepRecursiveFunction<Int, Int> { value ->
                            if (value <= 0) {
                                0
                            } else {
                                callRecursive(value - 1) + 1
                            }
                        }(limit)

                    @OptIn(ExperimentalContracts::class)
                    fun <T : Any> requirePresent(value: T?): T {
                        contract {
                            returns() implies (value != null)
                        }
                        return requireNotNull(value)
                    }

                    @OptIn(ExperimentalUnsignedTypes::class)
                    fun unsignedTotal(values: UIntArray): UInt = values.fold(0u) { acc, value -> acc + value }

                    fun runSafely(input: String): Result<Int> = runCatching { input.toInt() }.map { it + 1 }

                    class NamedElement(val name: String) : AbstractCoroutineContextElement(Key) {
                        companion object Key : CoroutineContext.Key<NamedElement>
                    }
                }
                """);
        Path outputDirectory = tempDir.resolve("broad-api-output");

        CompilationResult result = compileCommonModule(
                tempDir,
                "sample_api",
                outputDirectory,
                List.of(sourceFile),
                List.of(stdlibCommonJar)
        );

        assertSuccessfulCompilation(result, outputDirectory);
        assertThat(readOutputEntries(outputDirectory))
                .contains(
                        "META-INF/sample_api.kotlin_module",
                        "sample/api/ApiSurface.kotlin_metadata"
                );
    }

    @Test
    void compiledCommonModuleRemainsConsumableByAnotherModule(@TempDir Path tempDir) throws Exception {
        assumeFalse(isNativeImageRuntime());

        Path stdlibCommonJar = findClasspathEntry("kotlin-stdlib-common");
        Path librarySource = writeKotlinSource(tempDir.resolve("library"), "SampleLibrary.kt", """
                package sample.library

                data class Snapshot(
                    val names: List<String>,
                    val highestScore: Int,
                    val initials: Set<Char>
                )

                fun buildSnapshot(raw: Sequence<String>, scores: Map<String, Int>): Snapshot {
                    val normalized = raw
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                    val highestScore = normalized.map { scores[it] ?: 0 }.maxOrNull() ?: 0
                    val initials = normalized.map { it.first() }.toSet()
                    return Snapshot(normalized, highestScore, initials)
                }

                fun bucketNames(names: List<String>): Map<Char, List<String>> =
                    names.groupBy { it.first() }
                        .mapValues { (_, groupedNames) -> groupedNames.sorted() }

                fun composeName(label: String, count: Int): String =
                    "$label:${'$'}{count.toString().padStart(2, '0')}"
                """);
        Path libraryOutputDirectory = tempDir.resolve("sample-library-output");

        CompilationResult libraryResult = compileCommonModule(
                tempDir,
                "sample_library",
                libraryOutputDirectory,
                List.of(librarySource),
                List.of(stdlibCommonJar)
        );

        assertSuccessfulCompilation(libraryResult, libraryOutputDirectory);
        assertThat(readOutputEntries(libraryOutputDirectory))
                .contains(
                        "META-INF/sample_library.kotlin_module",
                        "sample/library/Snapshot.kotlin_metadata",
                        "sample/library/SampleLibraryKt.kotlin_metadata"
                );

        Path consumerSource = writeKotlinSource(tempDir.resolve("consumer"), "Consumer.kt", """
                package sample.consumer

                import sample.library.Snapshot
                import sample.library.bucketNames
                import sample.library.buildSnapshot
                import sample.library.composeName
                import kotlin.collections.getValue
                import kotlin.sequences.sequenceOf

                class Consumer {
                    fun snapshot(): Snapshot = buildSnapshot(
                        sequenceOf("  Ada", "", "Bea  ", "Ada"),
                        mapOf("Ada" to 7, "Bea" to 9)
                    )

                    fun report(): String {
                        val buckets = bucketNames(listOf("Bea", "Ada", "Bob"))
                        return composeName(buckets.getValue('B').first(), snapshot().highestScore)
                    }
                }
                """);
        Path consumerOutputDirectory = tempDir.resolve("sample-consumer-output");

        CompilationResult consumerResult = compileCommonModule(
                tempDir,
                "sample_consumer",
                consumerOutputDirectory,
                List.of(consumerSource),
                List.of(stdlibCommonJar, libraryOutputDirectory)
        );

        assertSuccessfulCompilation(consumerResult, consumerOutputDirectory);
        assertThat(readOutputEntries(consumerOutputDirectory))
                .contains(
                        "META-INF/sample_consumer.kotlin_module",
                        "sample/consumer/Consumer.kotlin_metadata"
                );
    }

    @Test
    void metadataCompilerAcceptsPropertyDelegationApi(@TempDir Path tempDir) throws Exception {
        assumeFalse(isNativeImageRuntime());

        Path stdlibCommonJar = findClasspathEntry("kotlin-stdlib-common");
        Path sourceFile = writeKotlinSource(tempDir.resolve("delegates"), "DelegatedState.kt", """
                package sample.delegates

                import kotlin.properties.Delegates

                class PipelineState {
                    private val transitions = mutableListOf<String>()

                    var stage: String by Delegates.observable("created") { _, old, new ->
                        if (old != new) {
                            transitions += "$old->$new"
                        }
                    }

                    var retryCount: Int by Delegates.vetoable(0) { _, current, next ->
                        next >= current && next <= 3
                    }

                    fun history(): List<String> = transitions.toList()
                }

                fun transitionSummary(stages: List<String>): String {
                    val state = PipelineState()
                    stages.forEach { state.stage = it }
                    state.retryCount = 1
                    state.retryCount = 3
                    state.retryCount = 2
                    return state.history().joinToString("|") + "#" + state.retryCount
                }
                """);
        Path outputDirectory = tempDir.resolve("delegates-output");

        CompilationResult result = compileCommonModule(
                tempDir,
                "sample_delegates",
                outputDirectory,
                List.of(sourceFile),
                List.of(stdlibCommonJar)
        );

        assertSuccessfulCompilation(result, outputDirectory);
        assertThat(readOutputEntries(outputDirectory))
                .contains(
                        "META-INF/sample_delegates.kotlin_module",
                        "sample/delegates/PipelineState.kotlin_metadata",
                        "sample/delegates/DelegatedStateKt.kotlin_metadata"
                );
    }

    @Test
    void metadataCompilerAcceptsArrayDequeApi(@TempDir Path tempDir) throws Exception {
        assumeFalse(isNativeImageRuntime());

        Path stdlibCommonJar = findClasspathEntry("kotlin-stdlib-common");
        Path sourceFile = writeKotlinSource(tempDir.resolve("array-deque"), "TaskBuffer.kt", """
                package sample.arraydeque

                import kotlin.collections.ArrayDeque

                data class ProcessedBatch(
                    val immediate: List<String>,
                    val deferred: List<String>
                )

                class TaskBuffer {
                    private val queue = ArrayDeque<String>()

                    fun enqueue(messages: Iterable<String>) {
                        messages
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach(queue::addLast)
                    }

                    fun prioritize(message: String) {
                        queue.remove(message)
                        queue.addFirst(message)
                    }

                    fun drain(batchSize: Int): ProcessedBatch {
                        val immediate = mutableListOf<String>()
                        repeat(batchSize.coerceAtMost(queue.size)) {
                            immediate += queue.removeFirst()
                        }
                        return ProcessedBatch(immediate, queue.toList())
                    }
                }

                fun processTasks(tasks: List<String>, urgent: String): ProcessedBatch {
                    val buffer = TaskBuffer()
                    buffer.enqueue(tasks)
                    buffer.prioritize(urgent)
                    return buffer.drain(2)
                }
                """);
        Path outputDirectory = tempDir.resolve("array-deque-output");

        CompilationResult result = compileCommonModule(
                tempDir,
                "sample_array_deque",
                outputDirectory,
                List.of(sourceFile),
                List.of(stdlibCommonJar)
        );

        assertSuccessfulCompilation(result, outputDirectory);
        assertThat(readOutputEntries(outputDirectory))
                .contains(
                        "META-INF/sample_array_deque.kotlin_module",
                        "sample/arraydeque/ProcessedBatch.kotlin_metadata",
                        "sample/arraydeque/TaskBuffer.kotlin_metadata",
                        "sample/arraydeque/TaskBufferKt.kotlin_metadata"
                );
    }

    private static CompilationResult compileCommonModule(
            Path workingDirectory,
            String moduleName,
            Path outputDirectory,
            List<Path> sourceFiles,
            List<Path> classpathEntries) throws Exception {
        String runtimeClasspath = System.getProperty("java.class.path");
        String compilationClasspath = classpathEntries.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));

        List<String> command = new ArrayList<>();
        command.add(compilerJavaExecutable().toString());
        command.add("-cp");
        command.add(runtimeClasspath);
        command.add("org.jetbrains.kotlin.cli.metadata.K2MetadataCompiler");
        command.add("-module-name");
        command.add(moduleName);
        command.add("-classpath");
        command.add(compilationClasspath);
        command.add("-d");
        command.add(outputDirectory.toString());
        for (Path sourceFile : sourceFiles) {
            command.add(sourceFile.toString());
        }

        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new CompilationResult(exitCode, output);
    }

    private static void assertSuccessfulCompilation(CompilationResult result, Path outputDirectory) {
        assertThat(result.exitCode())
                .withFailMessage("Kotlin metadata compilation failed:%n%s", result.output())
                .isZero();
        assertThat(Files.isDirectory(outputDirectory)).isTrue();
        assertThat(result.output()).doesNotContain(" error: ");
    }

    private static Path writeKotlinSource(Path directory, String fileName, String source) throws Exception {
        Files.createDirectories(directory);
        Path sourceFile = directory.resolve(fileName);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        return sourceFile;
    }

    private static Path findClasspathEntry(String artifactPrefix) {
        String runtimeClasspath = System.getProperty("java.class.path", "");
        return Arrays.stream(runtimeClasspath.split(Pattern.quote(File.pathSeparator)))
                .map(Path::of)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.startsWith(artifactPrefix + "-") && fileName.endsWith(".jar");
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing classpath entry for " + artifactPrefix));
    }

    private static Set<String> readOutputEntries(Path outputDirectory) throws Exception {
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> outputDirectory.relativize(path).toString().replace(File.separatorChar, '/'))
                    .collect(Collectors.toSet());
        }
    }

    private static Path compilerJavaExecutable() throws Exception {
        Path currentJavaExecutable = currentJavaExecutable();
        if (Runtime.version().feature() <= 22) {
            return currentJavaExecutable;
        }

        Path javaInstallationsDirectory = Path.of(System.getProperty("java.home")).getParent();
        if (javaInstallationsDirectory == null || !Files.isDirectory(javaInstallationsDirectory)) {
            throw new IllegalStateException("Could not locate a Java installation compatible with Kotlin compilation");
        }

        try (Stream<Path> installations = Files.list(javaInstallationsDirectory)) {
            return installations
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("bin").resolve(isWindows() ? "java.exe" : "java"))
                    .filter(Files::isRegularFile)
                    .map(path -> new JavaExecutableCandidate(path, javaFeatureVersion(path)))
                    .filter(candidate -> candidate.featureVersion() <= 22)
                    .max(Comparator.comparingInt(JavaExecutableCandidate::featureVersion))
                    .map(JavaExecutableCandidate::path)
                    .orElseThrow(() -> new IllegalStateException(
                            "Could not locate a Java installation compatible with kotlin-compiler-embeddable"
                    ));
        }
    }

    private static int javaFeatureVersion(Path javaExecutable) {
        try {
            Process process = new ProcessBuilder(javaExecutable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Unable to inspect Java version for " + javaExecutable + ": " + output);
            }

            Matcher matcher = Pattern.compile("version \"(\\d+)").matcher(output);
            if (!matcher.find()) {
                throw new IllegalStateException("Unable to parse Java feature version from: " + output);
            }
            return Integer.parseInt(matcher.group(1));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to determine Java feature version for " + javaExecutable, exception);
        }
    }

    private static Path currentJavaExecutable() {
        String executableName = isWindows() ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executableName);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.startsWith("Windows");
    }

    private record CompilationResult(int exitCode, String output) {
    }

    private record JavaExecutableCandidate(Path path, int featureVersion) {
    }
}
