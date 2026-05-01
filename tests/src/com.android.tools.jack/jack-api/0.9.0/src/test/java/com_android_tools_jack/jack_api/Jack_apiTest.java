/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_android_tools_jack.jack_api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.android.jack.api.ConfigNotSupportedException;
import com.android.jack.api.JackConfig;
import com.android.jack.api.JackProvider;
import com.android.jack.api.v01.Api01CompilationTask;
import com.android.jack.api.v01.Api01Config;
import com.android.jack.api.v01.ChainedException;
import com.android.jack.api.v01.ChainedException.ChainedExceptionBuilder;
import com.android.jack.api.v01.CompilationException;
import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v01.DebugInfoLevel;
import com.android.jack.api.v01.JavaSourceVersion;
import com.android.jack.api.v01.MultiDexKind;
import com.android.jack.api.v01.ReporterKind;
import com.android.jack.api.v01.ResourceCollisionPolicy;
import com.android.jack.api.v01.TypeCollisionPolicy;
import com.android.jack.api.v01.UnrecoverableException;
import com.android.jack.api.v01.VerbosityLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Jack_apiTest {

    @Test
    void exposesAllDocumentedEnumConstants() {
        assertThat(DebugInfoLevel.values()).containsExactly(
                DebugInfoLevel.NONE,
                DebugInfoLevel.LINES,
                DebugInfoLevel.FULL);
        assertThat(JavaSourceVersion.values()).containsExactly(
                JavaSourceVersion.JAVA_3,
                JavaSourceVersion.JAVA_4,
                JavaSourceVersion.JAVA_5,
                JavaSourceVersion.JAVA_6,
                JavaSourceVersion.JAVA_7);
        assertThat(MultiDexKind.values()).containsExactly(MultiDexKind.NONE, MultiDexKind.NATIVE, MultiDexKind.LEGACY);
        assertThat(ReporterKind.values()).containsExactly(ReporterKind.DEFAULT, ReporterKind.SDK);
        assertThat(ResourceCollisionPolicy.values()).containsExactly(
                ResourceCollisionPolicy.KEEP_FIRST,
                ResourceCollisionPolicy.FAIL);
        assertThat(TypeCollisionPolicy.values()).containsExactly(
                TypeCollisionPolicy.KEEP_FIRST,
                TypeCollisionPolicy.FAIL);
        assertThat(VerbosityLevel.values()).containsExactly(
                VerbosityLevel.ERROR,
                VerbosityLevel.WARNING,
                VerbosityLevel.INFO,
                VerbosityLevel.DEBUG);
        assertThat(JackProvider.SubReleaseKind.values()).containsExactly(
                JackProvider.SubReleaseKind.ENGINEERING,
                JackProvider.SubReleaseKind.PRE_ALPHA,
                JackProvider.SubReleaseKind.ALPHA,
                JackProvider.SubReleaseKind.BETA,
                JackProvider.SubReleaseKind.CANDIDATE,
                JackProvider.SubReleaseKind.RELEASE);

        assertThat(DebugInfoLevel.valueOf("FULL")).isSameAs(DebugInfoLevel.FULL);
        assertThat(JavaSourceVersion.valueOf("JAVA_7")).isSameAs(JavaSourceVersion.JAVA_7);
        assertThat(MultiDexKind.valueOf("LEGACY")).isSameAs(MultiDexKind.LEGACY);
        assertThat(ReporterKind.valueOf("SDK")).isSameAs(ReporterKind.SDK);
        assertThat(ResourceCollisionPolicy.valueOf("FAIL")).isSameAs(ResourceCollisionPolicy.FAIL);
        assertThat(TypeCollisionPolicy.valueOf("KEEP_FIRST")).isSameAs(TypeCollisionPolicy.KEEP_FIRST);
        assertThat(VerbosityLevel.valueOf("DEBUG")).isSameAs(VerbosityLevel.DEBUG);
        assertThat(JackProvider.SubReleaseKind.valueOf("CANDIDATE")).isSameAs(JackProvider.SubReleaseKind.CANDIDATE);
    }

    @Test
    void exceptionConstructorsExposeMessagesAndCauses() {
        RuntimeException cause = new RuntimeException("root cause");

        ConfigNotSupportedException emptyConfigException = new ConfigNotSupportedException();
        assertThat(emptyConfigException.getMessage()).isNull();
        assertThat(emptyConfigException.getCause()).isNull();
        ConfigNotSupportedException messageConfigException = new ConfigNotSupportedException("unsupported");
        assertThat(messageConfigException.getMessage()).isEqualTo("unsupported");
        ConfigNotSupportedException messageAndCauseConfigException =
                new ConfigNotSupportedException("unsupported", cause);
        assertThat(messageAndCauseConfigException.getMessage()).isEqualTo("unsupported");
        assertThat(messageAndCauseConfigException.getCause()).isSameAs(cause);
        assertThat(new ConfigNotSupportedException(cause).getCause()).isSameAs(cause);

        CompilationException emptyCompilationException = new CompilationException();
        assertThat(emptyCompilationException.getMessage()).isNull();
        assertThat(emptyCompilationException.getCause()).isNull();
        CompilationException messageCompilationException = new CompilationException("compile failed");
        assertThat(messageCompilationException.getMessage()).isEqualTo("compile failed");
        CompilationException messageAndCauseCompilationException = new CompilationException("compile failed", cause);
        assertThat(messageAndCauseCompilationException.getMessage()).isEqualTo("compile failed");
        assertThat(messageAndCauseCompilationException.getCause()).isSameAs(cause);
        assertThat(new CompilationException(cause).getCause()).isSameAs(cause);

        UnrecoverableException emptyUnrecoverableException = new UnrecoverableException();
        assertThat(emptyUnrecoverableException.getMessage()).isNull();
        assertThat(emptyUnrecoverableException.getCause()).isNull();
        UnrecoverableException messageUnrecoverableException = new UnrecoverableException("fatal");
        assertThat(messageUnrecoverableException.getMessage()).isEqualTo("fatal");
        UnrecoverableException messageAndCauseUnrecoverableException = new UnrecoverableException("fatal", cause);
        assertThat(messageAndCauseUnrecoverableException.getMessage()).isEqualTo("fatal");
        assertThat(messageAndCauseUnrecoverableException.getCause()).isSameAs(cause);
        assertThat(new UnrecoverableException(cause).getCause()).isSameAs(cause);

        ConfigurationException configurationException = new ConfigurationException("bad configuration", cause);
        assertThat(configurationException.getMessage()).isEqualTo("bad configuration");
        assertThat(configurationException.getCause()).isSameAs(cause);
        ConfigurationException causeOnlyConfigurationException = new ConfigurationException(cause);
        assertThat(causeOnlyConfigurationException.getMessage()).isEqualTo("root cause");
        assertThat(causeOnlyConfigurationException.getCause()).isSameAs(cause);
    }

    @Test
    void chainedConfigurationExceptionsMaintainOrderAndMutableMessage() throws ConfigurationException {
        ConfigurationException first = new ConfigurationException("first");
        ConfigurationException second = new ConfigurationException("second");
        ConfigurationException third = new ConfigurationException("third");
        ChainedExceptionBuilder<ConfigurationException> builder = new ChainedExceptionBuilder<>();

        builder.throwIfNecessary();
        builder.appendException(first);
        builder.appendException(second);
        builder.appendException(third);

        ConfigurationException head = builder.getException();
        assertThat((Object) head).isSameAs(first);
        assertThat(head.getNextExceptionCount()).isEqualTo(3);
        assertThat((Object) head.getNextException()).isSameAs(second);
        assertThat((Object) second.getNextException()).isSameAs(third);
        assertThat((Object) third.getNextException()).isNull();
        List<String> messages = new ArrayList<>();
        for (ChainedException exception : head) {
            messages.add(exception.getMessage());
        }
        assertThat(messages).containsExactly("first", "second", "third");

        second.setMessage("updated second");
        assertThat(second.getMessage()).isEqualTo("updated second");
        assertThat(second.getLocalizedMessage()).isEqualTo("updated second");
        assertThatExceptionOfType(ConfigurationException.class)
                .isThrownBy(builder::throwIfNecessary)
                .isSameAs(first);
    }

    @Test
    void chainedExceptionBuilderAppendsExistingExceptionChains() {
        ChainedExceptionBuilder<ConfigurationException> firstBuilder = new ChainedExceptionBuilder<>();
        firstBuilder.appendException(new ConfigurationException("parse sources"));
        firstBuilder.appendException(new ConfigurationException("resolve classpath"));
        ConfigurationException firstChain = firstBuilder.getException();

        ChainedExceptionBuilder<ConfigurationException> secondBuilder = new ChainedExceptionBuilder<>();
        secondBuilder.appendException(new ConfigurationException("read resources"));
        secondBuilder.appendException(new ConfigurationException("write outputs"));
        ConfigurationException secondChain = secondBuilder.getException();

        ChainedExceptionBuilder<ConfigurationException> combinedBuilder = new ChainedExceptionBuilder<>();
        combinedBuilder.appendException(firstChain);
        combinedBuilder.appendException(secondChain);

        ConfigurationException combined = combinedBuilder.getException();
        List<String> messages = new ArrayList<>();
        for (ChainedException exception : combined) {
            messages.add(exception.getMessage());
        }

        assertThat(messages).containsExactly(
                "parse sources",
                "resolve classpath",
                "read resources",
                "write outputs");
        assertThat(combined.getNextExceptionCount()).isEqualTo(4);
        ChainedException appendedSecondChain = combined.getNextException().getNextException();
        assertThat((Object) appendedSecondChain).isSameAs(secondChain);
        assertThat((Object) appendedSecondChain.getNextException()).isSameAs(secondChain.getNextException());
    }

    @Test
    void providerContractCreatesSupportedConfigAndRejectsUnsupportedConfig() throws Exception {
        FixedJackProvider provider = new FixedJackProvider();

        assertThat(provider.getCompilerVersion()).isEqualTo("0.9.0-test");
        assertThat(provider.getCompilerReleaseName()).isEqualTo("test-release");
        assertThat(provider.getCompilerReleaseCode()).isEqualTo(9);
        assertThat(provider.getCompilerSubReleaseCode()).isZero();
        assertThat(provider.getCompilerSubReleaseKind()).isSameAs(JackProvider.SubReleaseKind.BETA);
        assertThat(provider.getCompilerBuildId()).isEqualTo("build-0.9.0");
        assertThat(provider.getCompilerSourceCodeBase()).isEqualTo("https://android.googlesource.com/toolchain/jack");
        assertThat(provider.getSupportedConfigs()).containsExactly(Api01Config.class);
        assertThat(provider.isConfigSupported(Api01Config.class)).isTrue();
        assertThat(provider.isConfigSupported(UnsupportedConfig.class)).isFalse();
        assertThat(provider.createConfig(Api01Config.class)).isInstanceOf(FakeApi01Config.class);
        assertThatExceptionOfType(ConfigNotSupportedException.class)
                .isThrownBy(() -> provider.createConfig(UnsupportedConfig.class))
                .withMessageContaining("Unsupported config");
    }

    @Test
    void api01CompilationTaskPropagatesDeclaredFailureModes() {
        CompilationException compilationException = new CompilationException("compile failed");
        ConfigurationException configurationException = new ConfigurationException("bad configuration");
        UnrecoverableException unrecoverableException = new UnrecoverableException("unrecoverable");
        IllegalStateException illegalStateException = new IllegalStateException("task already used");

        Api01CompilationTask compilationFailure = () -> {
            throw compilationException;
        };
        Api01CompilationTask configurationFailure = () -> {
            throw configurationException;
        };
        Api01CompilationTask unrecoverableFailure = () -> {
            throw unrecoverableException;
        };
        Api01CompilationTask illegalStateFailure = () -> {
            throw illegalStateException;
        };

        assertThatExceptionOfType(CompilationException.class)
                .isThrownBy(compilationFailure::run)
                .isSameAs(compilationException);
        assertThatExceptionOfType(ConfigurationException.class)
                .isThrownBy(configurationFailure::run)
                .isSameAs(configurationException);
        assertThatExceptionOfType(UnrecoverableException.class)
                .isThrownBy(unrecoverableFailure::run)
                .isSameAs(unrecoverableException);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(illegalStateFailure::run)
                .isSameAs(illegalStateException);
    }

    @Test
    void api01ConfigAcceptsAllConfigurationInputsAndExposesCompilationTask() throws Exception {
        FakeApi01Config config = new FakeApi01Config();
        ByteArrayOutputStream reporter = new ByteArrayOutputStream();
        Path tempDir = Files.createTempDirectory("jack-api-test");
        File classpathEntry = tempDir.resolve("classes").toFile();
        File importedLibrary = tempDir.resolve("input.jack").toFile();
        File metaDir = tempDir.resolve("meta").toFile();
        File resourceDir = tempDir.resolve("resources").toFile();
        File incrementalDir = tempDir.resolve("incremental").toFile();
        File dexDir = tempDir.resolve("dex").toFile();
        File outputJack = tempDir.resolve("out.jack").toFile();
        File jarJarConfig = tempDir.resolve("jarjar.txt").toFile();
        File proguardConfig = tempDir.resolve("proguard.txt").toFile();
        File processorPath = tempDir.resolve("processor.jar").toFile();
        File sourceEntry = tempDir.resolve("Example.java").toFile();
        File mappingOutput = tempDir.resolve("mapping.txt").toFile();
        Map<String, String> processorOptions = Map.of("option", "value");

        config.setReporter(ReporterKind.SDK, reporter);
        config.setTypeImportCollisionPolicy(TypeCollisionPolicy.FAIL);
        config.setResourceImportCollisionPolicy(ResourceCollisionPolicy.KEEP_FIRST);
        config.setJavaSourceVersion(JavaSourceVersion.JAVA_7);
        config.setObfuscationMappingOutputFile(mappingOutput);
        config.setClasspath(List.of(classpathEntry));
        config.setImportedJackLibraryFiles(List.of(importedLibrary));
        config.setMetaDirs(List.of(metaDir));
        config.setResourceDirs(List.of(resourceDir));
        config.setIncrementalDir(incrementalDir);
        config.setOutputDexDir(dexDir);
        config.setOutputJackFile(outputJack);
        config.setJarJarConfigFiles(List.of(jarJarConfig));
        config.setProguardConfigFiles(List.of(proguardConfig));
        config.setDebugInfoLevel(DebugInfoLevel.FULL);
        config.setMultiDexKind(MultiDexKind.NATIVE);
        config.setVerbosityLevel(VerbosityLevel.INFO);
        config.setProcessorNames(List.of("com.example.Processor"));
        config.setProcessorPath(List.of(processorPath));
        config.setProcessorOptions(processorOptions);
        config.setSourceEntries(List.of(sourceEntry));
        config.setProperty("jack.test.property", "enabled");
        config.getTask().run();

        assertThat(config.reporterKind).isSameAs(ReporterKind.SDK);
        assertThat(config.reporterOutput).isSameAs(reporter);
        assertThat(config.typeCollisionPolicy).isSameAs(TypeCollisionPolicy.FAIL);
        assertThat(config.resourceCollisionPolicy).isSameAs(ResourceCollisionPolicy.KEEP_FIRST);
        assertThat(config.javaSourceVersion).isSameAs(JavaSourceVersion.JAVA_7);
        assertThat(config.obfuscationMappingOutputFile).isEqualTo(mappingOutput);
        assertThat(config.classpath).containsExactly(classpathEntry);
        assertThat(config.importedJackLibraryFiles).containsExactly(importedLibrary);
        assertThat(config.metaDirs).containsExactly(metaDir);
        assertThat(config.resourceDirs).containsExactly(resourceDir);
        assertThat(config.incrementalDir).isEqualTo(incrementalDir);
        assertThat(config.outputDexDir).isEqualTo(dexDir);
        assertThat(config.outputJackFile).isEqualTo(outputJack);
        assertThat(config.jarJarConfigFiles).containsExactly(jarJarConfig);
        assertThat(config.proguardConfigFiles).containsExactly(proguardConfig);
        assertThat(config.debugInfoLevel).isSameAs(DebugInfoLevel.FULL);
        assertThat(config.multiDexKind).isSameAs(MultiDexKind.NATIVE);
        assertThat(config.verbosityLevel).isSameAs(VerbosityLevel.INFO);
        assertThat(config.processorNames).containsExactly("com.example.Processor");
        assertThat(config.processorPath).containsExactly(processorPath);
        assertThat(config.processorOptions).containsEntry("option", "value");
        assertThat(config.sourceEntries).containsExactly(sourceEntry);
        assertThat(config.properties).containsEntry("jack.test.property", "enabled");
        assertThat(config.taskRun).isTrue();
    }

    private static final class FixedJackProvider implements JackProvider {

        @Override
        @SuppressWarnings("unchecked")
        public <T extends JackConfig> T createConfig(Class<T> configType) throws ConfigNotSupportedException {
            if (configType == Api01Config.class) {
                return (T) new FakeApi01Config();
            }
            throw new ConfigNotSupportedException("Unsupported config");
        }

        @Override
        public <T extends JackConfig> boolean isConfigSupported(Class<T> configType) {
            return configType == Api01Config.class;
        }

        @Override
        public Collection<Class<? extends JackConfig>> getSupportedConfigs() {
            return List.of(Api01Config.class);
        }

        @Override
        public String getCompilerVersion() {
            return "0.9.0-test";
        }

        @Override
        public String getCompilerReleaseName() {
            return "test-release";
        }

        @Override
        public int getCompilerReleaseCode() {
            return 9;
        }

        @Override
        public int getCompilerSubReleaseCode() {
            return 0;
        }

        @Override
        public JackProvider.SubReleaseKind getCompilerSubReleaseKind() {
            return JackProvider.SubReleaseKind.BETA;
        }

        @Override
        public String getCompilerBuildId() {
            return "build-0.9.0";
        }

        @Override
        public String getCompilerSourceCodeBase() {
            return "https://android.googlesource.com/toolchain/jack";
        }
    }

    private static final class FakeApi01Config implements Api01Config {

        private ReporterKind reporterKind;
        private OutputStream reporterOutput;
        private TypeCollisionPolicy typeCollisionPolicy;
        private ResourceCollisionPolicy resourceCollisionPolicy;
        private JavaSourceVersion javaSourceVersion;
        private File obfuscationMappingOutputFile;
        private List<File> classpath;
        private List<File> importedJackLibraryFiles;
        private List<File> metaDirs;
        private List<File> resourceDirs;
        private File incrementalDir;
        private File outputDexDir;
        private File outputJackFile;
        private List<File> jarJarConfigFiles;
        private List<File> proguardConfigFiles;
        private DebugInfoLevel debugInfoLevel;
        private MultiDexKind multiDexKind;
        private VerbosityLevel verbosityLevel;
        private List<String> processorNames;
        private List<File> processorPath;
        private Map<String, String> processorOptions;
        private Collection<File> sourceEntries;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private boolean taskRun;

        @Override
        public void setReporter(ReporterKind reporterKind, OutputStream reporterOutput) {
            this.reporterKind = reporterKind;
            this.reporterOutput = reporterOutput;
        }

        @Override
        public void setTypeImportCollisionPolicy(TypeCollisionPolicy typeCollisionPolicy) {
            this.typeCollisionPolicy = typeCollisionPolicy;
        }

        @Override
        public void setResourceImportCollisionPolicy(ResourceCollisionPolicy resourceCollisionPolicy) {
            this.resourceCollisionPolicy = resourceCollisionPolicy;
        }

        @Override
        public void setJavaSourceVersion(JavaSourceVersion javaSourceVersion) {
            this.javaSourceVersion = javaSourceVersion;
        }

        @Override
        public void setObfuscationMappingOutputFile(File obfuscationMappingOutputFile) {
            this.obfuscationMappingOutputFile = obfuscationMappingOutputFile;
        }

        @Override
        public void setClasspath(List<File> classpath) {
            this.classpath = classpath;
        }

        @Override
        public void setImportedJackLibraryFiles(List<File> importedJackLibraryFiles) {
            this.importedJackLibraryFiles = importedJackLibraryFiles;
        }

        @Override
        public void setMetaDirs(List<File> metaDirs) {
            this.metaDirs = metaDirs;
        }

        @Override
        public void setResourceDirs(List<File> resourceDirs) {
            this.resourceDirs = resourceDirs;
        }

        @Override
        public void setIncrementalDir(File incrementalDir) {
            this.incrementalDir = incrementalDir;
        }

        @Override
        public void setOutputDexDir(File outputDexDir) {
            this.outputDexDir = outputDexDir;
        }

        @Override
        public void setOutputJackFile(File outputJackFile) {
            this.outputJackFile = outputJackFile;
        }

        @Override
        public void setJarJarConfigFiles(List<File> jarJarConfigFiles) {
            this.jarJarConfigFiles = jarJarConfigFiles;
        }

        @Override
        public void setProguardConfigFiles(List<File> proguardConfigFiles) {
            this.proguardConfigFiles = proguardConfigFiles;
        }

        @Override
        public void setDebugInfoLevel(DebugInfoLevel debugInfoLevel) {
            this.debugInfoLevel = debugInfoLevel;
        }

        @Override
        public void setMultiDexKind(MultiDexKind multiDexKind) {
            this.multiDexKind = multiDexKind;
        }

        @Override
        public void setVerbosityLevel(VerbosityLevel verbosityLevel) {
            this.verbosityLevel = verbosityLevel;
        }

        @Override
        public void setProcessorNames(List<String> processorNames) {
            this.processorNames = processorNames;
        }

        @Override
        public void setProcessorPath(List<File> processorPath) {
            this.processorPath = processorPath;
        }

        @Override
        public void setProcessorOptions(Map<String, String> processorOptions) {
            this.processorOptions = processorOptions;
        }

        @Override
        public void setSourceEntries(Collection<File> sourceEntries) {
            this.sourceEntries = sourceEntries;
        }

        @Override
        public void setProperty(String name, String value) {
            properties.put(name, value);
        }

        @Override
        public Api01CompilationTask getTask() {
            return () -> taskRun = true;
        }
    }

    private interface UnsupportedConfig extends JackConfig {
    }
}
