/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_compiler_api;

import org.codehaus.plexus.compiler.AbstractCompiler;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerMessage;
import org.codehaus.plexus.compiler.CompilerNotImplementedException;
import org.codehaus.plexus.compiler.CompilerOutputStyle;
import org.codehaus.plexus.compiler.CompilerResult;
import org.codehaus.plexus.compiler.util.StreamPumper;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SimpleSourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.StaleSourceScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SingleTargetSourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Plexus_compiler_apiTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilerConfigurationStoresCompilerOptionsAndUsesSafeDefaults() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        File workingDirectory = temporaryDirectory.resolve("work").toFile();
        File buildDirectory = temporaryDirectory.resolve("build").toFile();
        File generatedSourcesDirectory = temporaryDirectory.resolve("generated-sources").toFile();
        List<String> processorPathEntries = new ArrayList<>(Arrays.asList("processor-a.jar", "processor-b.jar"));

        configuration.setOutputLocation("target/classes");
        configuration.setDebug(true);
        configuration.setDebugLevel("lines,vars,source");
        configuration.setShowWarnings(false);
        configuration.setShowDeprecation(true);
        configuration.setFailOnWarning(true);
        configuration.setSourceVersion("8");
        configuration.setTargetVersion("11");
        configuration.setReleaseVersion("17");
        configuration.setSourceEncoding(StandardCharsets.UTF_8.name());
        configuration.setModuleVersion("module-version");
        configuration.setFork(true);
        configuration.setOptimize(true);
        configuration.setMeminitial("64m");
        configuration.setMaxmem("256m");
        configuration.setExecutable("javac");
        configuration.setWorkingDirectory(workingDirectory);
        configuration.setCompilerVersion("modern");
        configuration.setVerbose(true);
        configuration.setParameters(true);
        configuration.setBuildDirectory(buildDirectory);
        configuration.setOutputFileName("artifact.jar");
        configuration.setGeneratedSourcesDirectory(generatedSourcesDirectory);
        configuration.setProc("only");
        configuration.setAnnotationProcessors(new String[] {
                "com.example.FirstProcessor",
                "com.example.SecondProcessor"
        });
        configuration.setProcessorPathEntries(processorPathEntries);
        configuration.setCompilerReuseStrategy(CompilerConfiguration.CompilerReuseStrategy.AlwaysNew);
        configuration.setForceJavacCompilerUse(true);

        assertThat(configuration.getOutputLocation()).isEqualTo("target/classes");
        assertThat(configuration.isDebug()).isTrue();
        assertThat(configuration.getDebugLevel()).isEqualTo("lines,vars,source");
        assertThat(configuration.isShowWarnings()).isFalse();
        assertThat(configuration.isShowDeprecation()).isTrue();
        assertThat(configuration.isFailOnWarning()).isTrue();
        assertThat(configuration.getSourceVersion()).isEqualTo("8");
        assertThat(configuration.getTargetVersion()).isEqualTo("11");
        assertThat(configuration.getReleaseVersion()).isEqualTo("17");
        assertThat(configuration.getSourceEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(configuration.getModuleVersion()).isEqualTo("module-version");
        assertThat(configuration.isFork()).isTrue();
        assertThat(configuration.isOptimize()).isTrue();
        assertThat(configuration.getMeminitial()).isEqualTo("64m");
        assertThat(configuration.getMaxmem()).isEqualTo("256m");
        assertThat(configuration.getExecutable()).isEqualTo("javac");
        assertThat(configuration.getWorkingDirectory()).isEqualTo(workingDirectory);
        assertThat(configuration.getCompilerVersion()).isEqualTo("modern");
        assertThat(configuration.isVerbose()).isTrue();
        assertThat(configuration.isParameters()).isTrue();
        assertThat(configuration.getBuildDirectory()).isEqualTo(buildDirectory);
        assertThat(configuration.getOutputFileName()).isEqualTo("artifact.jar");
        assertThat(configuration.getGeneratedSourcesDirectory()).isEqualTo(generatedSourcesDirectory);
        assertThat(configuration.getProc()).isEqualTo("only");
        assertThat(configuration.getAnnotationProcessors())
                .containsExactly("com.example.FirstProcessor", "com.example.SecondProcessor");
        assertThat(configuration.getProcessorPathEntries()).containsExactly("processor-a.jar", "processor-b.jar");
        assertThat(configuration.getCompilerReuseStrategy())
                .isEqualTo(CompilerConfiguration.CompilerReuseStrategy.AlwaysNew);
        assertThat(configuration.isForceJavacCompilerUse()).isTrue();
    }

    @Test
    void compilerConfigurationDefensivelyCopiesCollectionOptions() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        List<String> classpathEntries = new ArrayList<>(Arrays.asList("classes", "dependency.jar"));
        List<String> modulepathEntries = new ArrayList<>(Collections.singletonList("module.jar"));
        List<String> sourceLocations = new ArrayList<>(Collections.singletonList("src/main/java"));
        Set<File> sourceFiles = Collections.singleton(temporaryDirectory.resolve("Example.java").toFile());
        Set<String> includes = Collections.singleton("**/*.java");
        Set<String> excludes = Collections.singleton("**/module-info.java");

        configuration.setClasspathEntries(classpathEntries);
        configuration.setModulepathEntries(modulepathEntries);
        configuration.setSourceLocations(sourceLocations);
        configuration.setSourceFiles(sourceFiles);
        configuration.setIncludes(includes);
        configuration.setExcludes(excludes);
        classpathEntries.add("late-classpath-entry.jar");
        modulepathEntries.add("late-modulepath-entry.jar");
        sourceLocations.add("late-source-root");

        assertThat(configuration.getClasspathEntries()).containsExactly("classes", "dependency.jar");
        assertThat(configuration.getModulepathEntries()).containsExactly("module.jar");
        assertThat(configuration.getSourceLocations()).containsExactly("src/main/java");
        assertThat(configuration.getSourceFiles()).containsExactlyElementsOf(sourceFiles);
        assertThat(configuration.getIncludes()).containsExactly("**/*.java");
        assertThat(configuration.getExcludes()).containsExactly("**/module-info.java");
        assertThatThrownBy(() -> configuration.getClasspathEntries().add("blocked.jar"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> configuration.getModulepathEntries().add("blocked.jar"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> configuration.getSourceLocations().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> configuration.getIncludes().add("**/*.groovy"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> configuration.getExcludes().add("**/Generated.java"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void compilerConfigurationHandlesAddersNullCollectionsAndDuplicateCustomArguments() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addClasspathEntry("classes");
        configuration.addModulepathEntry("modules");
        configuration.addSourceLocation("src/main/java");
        configuration.addInclude("**/*.java");
        configuration.addExclude("**/Ignored.java");
        configuration.addProcessorPathEntry("processor.jar");
        configuration.addCompilerCustomArgument("-Xlint", "unchecked");
        configuration.addCompilerCustomArgument("-Xplugin", "first");
        configuration.addCompilerCustomArgument("-Xplugin", "second");

        Map<String, String> customArguments = configuration.getCustomCompilerArgumentsAsMap();
        Collection<Map.Entry<String, String>> customArgumentEntries = configuration.getCustomCompilerArgumentsEntries();

        assertThat(configuration.getClasspathEntries()).containsExactly("classes");
        assertThat(configuration.getModulepathEntries()).containsExactly("modules");
        assertThat(configuration.getSourceLocations()).containsExactly("src/main/java");
        assertThat(configuration.getIncludes()).containsExactly("**/*.java");
        assertThat(configuration.getExcludes()).containsExactly("**/Ignored.java");
        assertThat(configuration.getProcessorPathEntries()).containsExactly("processor.jar");
        assertThat(customArguments).containsEntry("-Xlint", "unchecked").containsEntry("-Xplugin", "second");
        assertThat(customArgumentEntries).extracting(Map.Entry::getKey)
                .containsExactly("-Xlint", "-Xplugin", "-Xplugin");
        assertThat(customArgumentEntries).extracting(Map.Entry::getValue)
                .containsExactly("unchecked", "first", "second");

        LinkedHashMap<String, String> replacementArguments = new LinkedHashMap<>();
        replacementArguments.put("--enable-preview", "");
        replacementArguments.put("-Akey", "value");
        configuration.setCustomCompilerArgumentsAsMap(replacementArguments);
        configuration.setClasspathEntries(null);
        configuration.setModulepathEntries(null);
        configuration.setSourceLocations(null);
        configuration.setSourceFiles(null);
        configuration.setIncludes(null);
        configuration.setExcludes(null);

        assertThat(configuration.getCustomCompilerArgumentsAsMap())
                .containsExactlyEntriesOf(replacementArguments);
        assertThat(configuration.getClasspathEntries()).isEmpty();
        assertThat(configuration.getModulepathEntries()).isEmpty();
        assertThat(configuration.getSourceLocations()).isEmpty();
        assertThat(configuration.getSourceFiles()).isEmpty();
        assertThat(configuration.getIncludes()).isEmpty();
        assertThat(configuration.getExcludes()).isEmpty();
    }

    @Test
    void compilerReuseStrategiesExposeStableNames() {
        assertThat(CompilerConfiguration.CompilerReuseStrategy.ReuseSame.getStrategy()).isEqualTo("reuseSame");
        assertThat(CompilerConfiguration.CompilerReuseStrategy.AlwaysNew.getStrategy()).isEqualTo("alwaysNew");
        assertThat(CompilerConfiguration.CompilerReuseStrategy.ReuseCreated.getStrategy()).isEqualTo("reuseCreated");
        assertThat(CompilerConfiguration.CompilerReuseStrategy.ReuseCreated)
                .hasToString("CompilerReuseStrategy:reuseCreated");
        assertThat(new CompilerConfiguration().getCompilerReuseStrategy())
                .isEqualTo(CompilerConfiguration.CompilerReuseStrategy.ReuseCreated);
    }

    @Test
    void compilerMessagesNormalizeJavacPrefixesAndFormatLocations() {
        CompilerMessage warning = new CompilerMessage(
                "src/main/java/App.java",
                CompilerMessage.Kind.WARNING,
                12,
                7,
                12,
                20,
                "warning: unchecked conversion");
        CompilerMessage mandatoryWarning = new CompilerMessage(
                "warning: required warning",
                CompilerMessage.Kind.MANDATORY_WARNING);
        CompilerMessage note = new CompilerMessage("Note: recompiling module", CompilerMessage.Kind.NOTE);
        CompilerMessage error = new CompilerMessage(
                "src/main/java/Broken.java",
                CompilerMessage.Kind.ERROR,
                3,
                0,
                3,
                0,
                "error: cannot find symbol");
        CompilerMessage fileOnly = new CompilerMessage(
                "pom.xml",
                CompilerMessage.Kind.OTHER,
                0,
                0,
                0,
                0,
                "not a Java source");

        assertThat(warning.isError()).isFalse();
        assertThat(warning.getKind()).isEqualTo(CompilerMessage.Kind.WARNING);
        assertThat(warning.getFile()).isEqualTo("src/main/java/App.java");
        assertThat(warning.getStartLine()).isEqualTo(12);
        assertThat(warning.getStartColumn()).isEqualTo(7);
        assertThat(warning.getEndLine()).isEqualTo(12);
        assertThat(warning.getEndColumn()).isEqualTo(20);
        assertThat(warning.getMessage()).isEqualTo("unchecked conversion");
        assertThat(warning).hasToString("src/main/java/App.java:[12,7] unchecked conversion");
        assertThat(mandatoryWarning.getMessage()).isEqualTo("required warning");
        assertThat(note.getMessage()).isEqualTo("recompiling module");
        assertThat(error.isError()).isTrue();
        assertThat(error.getMessage()).isEqualTo("error: cannot find symbol");
        assertThat(error).hasToString("src/main/java/Broken.java:[3] error: cannot find symbol");
        assertThat(fileOnly).hasToString("pom.xml: not a Java source");
        assertThat(new CompilerMessage("plain message", CompilerMessage.Kind.OTHER)).hasToString("plain message");
    }

    @Test
    void compilerExceptionsPreserveMessagesAndCauses() {
        IllegalStateException cause = new IllegalStateException("root cause");

        CompilerException compilerException = new CompilerException("compile failed", cause);
        CompilerNotImplementedException notImplementedException = new CompilerNotImplementedException(
                "missing compiler",
                cause);
        InclusionScanException scanException = new InclusionScanException("scan failed", cause);

        assertThat(new CompilerException("compile failed")).hasMessage("compile failed").hasNoCause();
        assertThat(compilerException).hasMessage("compile failed").hasCause(cause);
        assertThat(new CompilerNotImplementedException("missing compiler"))
                .hasMessage("missing compiler")
                .hasNoCause();
        assertThat(notImplementedException).hasMessage("missing compiler").hasCause(cause);
        assertThat(new InclusionScanException("scan failed")).hasMessage("scan failed").hasNoCause();
        assertThat(scanException).hasMessage("scan failed").hasCause(cause);
    }

    @Test
    void compilerResultSupportsLazyMessagesAndFluentMutation() {
        CompilerResult defaultResult = new CompilerResult();
        CompilerMessage warning = new CompilerMessage("warning text", CompilerMessage.Kind.WARNING);
        List<CompilerMessage> messages = new ArrayList<>(Collections.singletonList(warning));

        CompilerResult configuredResult = new CompilerResult(false, messages)
                .success(true)
                .compilerMessages(Collections.singletonList(
                        new CompilerMessage("note text", CompilerMessage.Kind.NOTE)));

        assertThat(defaultResult.isSuccess()).isTrue();
        assertThat(defaultResult.getCompilerMessages()).isEmpty();
        defaultResult.getCompilerMessages().add(warning);
        assertThat(defaultResult.getCompilerMessages()).containsExactly(warning);
        assertThat(configuredResult.isSuccess()).isTrue();
        assertThat(configuredResult.getCompilerMessages())
                .extracting(CompilerMessage::getMessage)
                .containsExactly("note text");
    }

    @Test
    void compilerOutputStylesAndAbstractCompilerReportSupportedOutputModes() throws Exception {
        TestCompiler perInputCompiler = new TestCompiler(
                CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE,
                ".java",
                ".class",
                null);
        TestCompiler singleOutputCompiler = new TestCompiler(
                CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES,
                ".grammar",
                null,
                "generated-parser.java");
        CompilerConfiguration configuration = new CompilerConfiguration();

        assertThat(CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE)
                .hasToString("one-output-file-per-input-file");
        assertThat(CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES).hasToString("one-output-file");
        assertThat(CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE)
                .isEqualTo(CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE)
                .isNotEqualTo(CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES)
                .isNotEqualTo("one-output-file-per-input-file");
        assertThat(perInputCompiler.getCompilerOutputStyle())
                .isEqualTo(CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE);
        assertThat(perInputCompiler.getInputFileEnding(configuration)).isEqualTo(".java");
        assertThat(perInputCompiler.getOutputFileEnding(configuration)).isEqualTo(".class");
        assertThatThrownBy(() -> perInputCompiler.getOutputFile(configuration))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("This compiler implementation doesn't have one output file for all files.");
        assertThat(singleOutputCompiler.getCompilerOutputStyle())
                .isEqualTo(CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES);
        assertThat(singleOutputCompiler.getInputFileEnding(configuration)).isEqualTo(".grammar");
        assertThat(singleOutputCompiler.getOutputFile(configuration)).isEqualTo("generated-parser.java");
        assertThatThrownBy(() -> singleOutputCompiler.getOutputFileEnding(configuration))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("This compiler implementation doesn't have one output file per input file.");
        assertThat(singleOutputCompiler.canUpdateTarget(configuration)).isTrue();
    }

    @Test
    void abstractCompilerProvidesCommandLinePathFormattingAndDefaultNotImplementedResult() throws Exception {
        TestCompiler compiler = new TestCompiler(
                CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE,
                ".java",
                ".class",
                null);
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setExecutable("javac");
        configuration.setOutputLocation("target/classes");

        assertThat(AbstractCompiler.getPathString(Arrays.asList("first", "second")))
                .isEqualTo("first" + File.pathSeparator + "second" + File.pathSeparator);
        assertThat(compiler.createCommandLine(configuration)).containsExactly("javac", "-d", "target/classes");
        assertThatThrownBy(() -> compiler.performCompile(configuration))
                .isInstanceOf(CompilerNotImplementedException.class)
                .hasMessage("The performCompile method has not been implemented.");
    }

    @Test
    void suffixMappingsMapOnlyMatchingSourcesToExpectedTargets() throws Exception {
        File targetDirectory = temporaryDirectory.resolve("target").toFile();
        Set<String> targetSuffixes = new LinkedHashSet<>(Arrays.asList(".class", ".h"));
        SuffixMapping multiTargetMapping = new SuffixMapping(".java", targetSuffixes);
        SuffixMapping singleTargetMapping = new SuffixMapping(".java", ".class");
        SingleTargetSourceMapping singleOutputMapping = new SingleTargetSourceMapping(
                ".grammar",
                "GeneratedParser.java");

        assertThat(multiTargetMapping.getTargetFiles(targetDirectory, "org/example/App.java"))
                .containsExactlyInAnyOrder(
                        new File(targetDirectory, "org/example/App.class"),
                        new File(targetDirectory, "org/example/App.h"));
        assertThat(multiTargetMapping.getTargetFiles(targetDirectory, "org/example/readme.txt")).isEmpty();
        assertThat(singleTargetMapping.getTargetFiles(targetDirectory, "org/example/App.java"))
                .containsExactly(new File(targetDirectory, "org/example/App.class"));
        assertThat(singleOutputMapping.getTargetFiles(targetDirectory, "src/parser.grammar"))
                .containsExactly(new File(targetDirectory, "GeneratedParser.java"));
        assertThat(singleOutputMapping.getTargetFiles(targetDirectory, "src/parser.txt")).isEmpty();
    }

    @Test
    void simpleSourceScannerUsesIncludesExcludesAndRequiresAtLeastOneMapping() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path targetDirectory = temporaryDirectory.resolve("target");
        Path includedSource = sourceDirectory.resolve("org/example/App.java");
        Path excludedSource = sourceDirectory.resolve("org/example/Generated.java");
        Path ignoredText = sourceDirectory.resolve("org/example/readme.txt");
        Files.createDirectories(includedSource.getParent());
        Files.createDirectories(targetDirectory);
        Files.writeString(includedSource, "class App {}", StandardCharsets.UTF_8);
        Files.writeString(excludedSource, "class Generated {}", StandardCharsets.UTF_8);
        Files.writeString(ignoredText, "ignored", StandardCharsets.UTF_8);
        SimpleSourceInclusionScanner scanner = new SimpleSourceInclusionScanner(
                Collections.singleton("**/*.java"),
                Collections.singleton("**/Generated.java"));

        assertThat(scanner.getIncludedSources(sourceDirectory.toFile(), targetDirectory.toFile())).isEmpty();

        scanner.addSourceMapping(new SuffixMapping(".java", ".class"));

        assertThat(scanner.getIncludedSources(sourceDirectory.toFile(), targetDirectory.toFile()))
                .containsExactly(includedSource.toFile());
    }

    @Test
    void simpleSourceScannerHonorsDefaultDirectoryExcludes() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("default-exclude-sources");
        Path targetDirectory = temporaryDirectory.resolve("default-exclude-target");
        Path includedSource = sourceDirectory.resolve("org/example/App.java");
        Path ignoredMetadataSource = sourceDirectory.resolve(".git/objects/Ignored.java");
        Files.createDirectories(includedSource.getParent());
        Files.createDirectories(ignoredMetadataSource.getParent());
        Files.createDirectories(targetDirectory);
        Files.writeString(includedSource, "class App {}", StandardCharsets.UTF_8);
        Files.writeString(ignoredMetadataSource, "class Ignored {}", StandardCharsets.UTF_8);
        SimpleSourceInclusionScanner scanner = new SimpleSourceInclusionScanner(
                Collections.singleton("**/*.java"),
                Collections.emptySet());
        scanner.addSourceMapping(new SuffixMapping(".java", ".class"));

        assertThat(scanner.getIncludedSources(sourceDirectory.toFile(), targetDirectory.toFile()))
                .containsExactly(includedSource.toFile());
    }

    @Test
    void staleSourceScannerIncludesSourcesWithoutTargetsOrWithOutdatedTargets() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("stale-sources");
        Path targetDirectory = temporaryDirectory.resolve("stale-target");
        Path staleSource = sourceDirectory.resolve("org/example/Stale.java");
        Path freshSource = sourceDirectory.resolve("org/example/Fresh.java");
        Path missingTargetSource = sourceDirectory.resolve("org/example/MissingTarget.java");
        Path excludedSource = sourceDirectory.resolve("org/example/Excluded.java");
        Path staleTarget = targetDirectory.resolve("org/example/Stale.class");
        Path freshTarget = targetDirectory.resolve("org/example/Fresh.class");
        Files.createDirectories(staleSource.getParent());
        Files.createDirectories(staleTarget.getParent());
        Files.writeString(staleSource, "class Stale {}", StandardCharsets.UTF_8);
        Files.writeString(freshSource, "class Fresh {}", StandardCharsets.UTF_8);
        Files.writeString(missingTargetSource, "class MissingTarget {}", StandardCharsets.UTF_8);
        Files.writeString(excludedSource, "class Excluded {}", StandardCharsets.UTF_8);
        Files.writeString(staleTarget, "old bytecode", StandardCharsets.UTF_8);
        Files.writeString(freshTarget, "new bytecode", StandardCharsets.UTF_8);
        assertThat(staleSource.toFile().setLastModified(2_000L)).isTrue();
        assertThat(freshSource.toFile().setLastModified(2_000L)).isTrue();
        assertThat(missingTargetSource.toFile().setLastModified(2_000L)).isTrue();
        assertThat(staleTarget.toFile().setLastModified(1_000L)).isTrue();
        assertThat(freshTarget.toFile().setLastModified(5_000L)).isTrue();
        StaleSourceScanner scanner = new StaleSourceScanner(
                0,
                Collections.singleton("**/*.java"),
                Collections.singleton("**/Excluded.java"));

        assertThat(scanner.getIncludedSources(sourceDirectory.toFile(), targetDirectory.toFile())).isEmpty();

        scanner.addSourceMapping(new SuffixMapping(".java", ".class"));

        assertThat(scanner.getIncludedSources(sourceDirectory.toFile(), targetDirectory.toFile()))
                .containsExactlyInAnyOrder(staleSource.toFile(), missingTargetSource.toFile());
    }

    @Test
    void staleSourceScannerHonorsLastModifiedGracePeriod() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("grace-period-sources");
        Path targetDirectory = temporaryDirectory.resolve("grace-period-target");
        Path withinGraceSource = sourceDirectory.resolve("org/example/WithinGrace.java");
        Path outsideGraceSource = sourceDirectory.resolve("org/example/OutsideGrace.java");
        Path withinGraceTarget = targetDirectory.resolve("org/example/WithinGrace.class");
        Path outsideGraceTarget = targetDirectory.resolve("org/example/OutsideGrace.class");
        Files.createDirectories(withinGraceSource.getParent());
        Files.createDirectories(withinGraceTarget.getParent());
        Files.writeString(withinGraceSource, "class WithinGrace {}", StandardCharsets.UTF_8);
        Files.writeString(outsideGraceSource, "class OutsideGrace {}", StandardCharsets.UTF_8);
        Files.writeString(withinGraceTarget, "recent bytecode", StandardCharsets.UTF_8);
        Files.writeString(outsideGraceTarget, "older bytecode", StandardCharsets.UTF_8);
        assertThat(withinGraceSource.toFile().setLastModified(10_000L)).isTrue();
        assertThat(outsideGraceSource.toFile().setLastModified(10_000L)).isTrue();
        assertThat(withinGraceTarget.toFile().setLastModified(9_000L)).isTrue();
        assertThat(outsideGraceTarget.toFile().setLastModified(7_000L)).isTrue();
        StaleSourceScanner scanner = new StaleSourceScanner(2_000L);
        scanner.addSourceMapping(new SuffixMapping(".java", ".class"));

        assertThat(scanner.getIncludedSources(sourceDirectory.toFile(), targetDirectory.toFile()))
                .containsExactly(outsideGraceSource.toFile());
    }

    @Test
    void streamPumperCopiesFiniteBufferedInputAndStops() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamPumper streamPumper = new StreamPumper(
                new BufferedInputStream(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))),
                output);

        streamPumper.start();
        streamPumper.join(1_000L);

        assertThat(streamPumper.isAlive()).isFalse();
        assertThat(output.toString(StandardCharsets.UTF_8.name())).isEqualTo("hello");
    }

    private static final class TestCompiler extends AbstractCompiler {
        private TestCompiler(
                CompilerOutputStyle compilerOutputStyle,
                String inputFileEnding,
                String outputFileEnding,
                String outputFile) {
            super(compilerOutputStyle, inputFileEnding, outputFileEnding, outputFile);
        }

        @Override
        public String[] createCommandLine(CompilerConfiguration config) throws CompilerException {
            return new String[] {config.getExecutable(), "-d", config.getOutputLocation() };
        }
    }
}
