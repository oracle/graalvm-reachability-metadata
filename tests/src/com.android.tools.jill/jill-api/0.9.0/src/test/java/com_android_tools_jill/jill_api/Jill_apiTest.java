/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_android_tools_jill.jill_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.android.jill.api.ConfigNotSupportedException;
import com.android.jill.api.JillConfig;
import com.android.jill.api.JillProvider;
import com.android.jill.api.v01.Api01Config;
import com.android.jill.api.v01.Api01TranslationTask;
import com.android.jill.api.v01.ConfigurationException;
import com.android.jill.api.v01.TranslationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Jill_apiTest {
    @TempDir
    Path tempDir;

    @Test
    void providerExposesVersionMetadataAndTypedConfigFactory() throws Exception {
        RecordingJillProvider provider = new RecordingJillProvider();

        assertThat(provider.getTranslatorVersion()).isEqualTo("0.9.0-test-translator");
        assertThat(provider.getTranslatorReleaseName()).isEqualTo("integration-test-release");
        assertThat(provider.getTranslatorReleaseCode()).isEqualTo(9);
        assertThat(provider.getTranslatorSubReleaseCode()).isEqualTo(0);
        assertThat(provider.getTranslatorSubReleaseKind()).isEqualTo(JillProvider.SubReleaseKind.BETA);
        assertThat(provider.getTranslatorBuildId()).isEqualTo("build-0.9.0");
        assertThat(provider.getTranslatorSourceCodeBase()).isEqualTo("https://example.test/android/jill");

        assertThat(provider.isConfigSupported(Api01Config.class)).isTrue();
        assertThat(provider.isConfigSupported(UnsupportedConfig.class)).isFalse();
        assertThat(provider.getSupportedConfigs()).containsExactly(Api01Config.class);

        Api01Config config = provider.createConfig(Api01Config.class);
        assertThat(config).isInstanceOf(RecordingApi01Config.class);
        assertThatThrownBy(() -> provider.createConfig(UnsupportedConfig.class))
                .isInstanceOf(ConfigNotSupportedException.class)
                .hasMessageContaining(UnsupportedConfig.class.getName());
    }

    @Test
    void api01ConfigBuildsRunnableTranslationTaskFromConfiguredFiles() throws Exception {
        Path inputJar = Files.write(tempDir.resolve("input.jar"), new byte[] {0x50, 0x4b, 0x03, 0x04});
        Path outputJack = tempDir.resolve("output.jack");
        RecordingApi01Config config = new RecordingApi01Config();

        config.setVerbose(true);
        config.setDebugInfo(false);
        config.setInputJavaBinaryFile(inputJar.toFile());
        config.setOutputJackFile(outputJack.toFile());

        RecordingTranslationTask task = (RecordingTranslationTask) config.getTask();
        assertThat(task.isVerbose()).isTrue();
        assertThat(task.hasDebugInfo()).isFalse();
        assertThat(task.getInputJavaBinaryFile()).isEqualTo(inputJar.toFile());
        assertThat(task.getOutputJackFile()).isEqualTo(outputJack.toFile());

        task.run();

        assertThat(task.getRunCount()).isEqualTo(1);
        assertThat(Files.readString(outputJack)).isEqualTo("translated input.jar with debug=false and verbose=true");
    }

    @Test
    void api01ConfigRejectsInvalidConfigurationBeforeCreatingTask() throws Exception {
        RecordingApi01Config config = new RecordingApi01Config();

        assertThatThrownBy(() -> config.setInputJavaBinaryFile(null))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Input Java binary file must not be null");
        assertThatThrownBy(() -> config.setOutputJackFile(null))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Output Jack file must not be null");
        assertThatThrownBy(config::getTask)
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Input Java binary file has not been configured");

        config.setInputJavaBinaryFile(tempDir.resolve("input.jar").toFile());
        assertThatThrownBy(config::getTask)
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Output Jack file has not been configured");
    }

    @Test
    void translationTaskReportsTranslationFailuresAndIllegalReuse() throws Exception {
        File missingInput = tempDir.resolve("missing.jar").toFile();
        File outputJack = tempDir.resolve("output.jack").toFile();
        RecordingTranslationTask task = new RecordingTranslationTask(missingInput, outputJack, false, true);

        assertThatThrownBy(task::run)
                .isInstanceOf(TranslationException.class)
                .hasMessageContaining("Input Java binary file does not exist");

        Files.write(missingInput.toPath(), new byte[] {1, 2, 3});
        task.run();

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Translation task instances are single-use");
    }

    @Test
    void providerCanOmitOptionalBuildAndSourceMetadata() {
        JillProvider provider = new ProviderWithoutOptionalMetadata();

        assertThat(provider.getTranslatorBuildId()).isNull();
        assertThat(provider.getTranslatorSourceCodeBase()).isNull();
    }

    @Test
    void exceptionConstructorsExposeMessagesAndCauses() {
        RuntimeException cause = new RuntimeException("root cause");

        assertThat(new ConfigNotSupportedException()).hasMessage(null).hasNoCause();
        assertThat(new ConfigNotSupportedException("missing config")).hasMessage("missing config").hasNoCause();
        assertThat(new ConfigNotSupportedException(cause)).hasCause(cause);
        assertThat(new ConfigNotSupportedException("missing config", cause))
                .hasMessage("missing config")
                .hasCause(cause);

        assertThat(new ConfigurationException("bad config")).hasMessage("bad config").hasNoCause();
        assertThat(new ConfigurationException(cause)).hasCause(cause);
        assertThat(new ConfigurationException("bad config", cause)).hasMessage("bad config").hasCause(cause);

        assertThat(new TranslationException()).hasMessage(null).hasNoCause();
        assertThat(new TranslationException("translation failed")).hasMessage("translation failed").hasNoCause();
        assertThat(new TranslationException(cause)).hasCause(cause);
        assertThat(new TranslationException("translation failed", cause))
                .hasMessage("translation failed")
                .hasCause(cause);
    }

    @Test
    void subReleaseKindEnumSupportsStableApiOrderAndLookup() {
        assertThat(JillProvider.SubReleaseKind.values())
                .containsExactly(
                        JillProvider.SubReleaseKind.ENGINEERING,
                        JillProvider.SubReleaseKind.PRE_ALPHA,
                        JillProvider.SubReleaseKind.ALPHA,
                        JillProvider.SubReleaseKind.BETA,
                        JillProvider.SubReleaseKind.CANDIDATE,
                        JillProvider.SubReleaseKind.RELEASE);
        assertThat(JillProvider.SubReleaseKind.valueOf("ENGINEERING"))
                .isEqualTo(JillProvider.SubReleaseKind.ENGINEERING);
        assertThat(JillProvider.SubReleaseKind.valueOf("PRE_ALPHA"))
                .isEqualTo(JillProvider.SubReleaseKind.PRE_ALPHA);
        assertThat(JillProvider.SubReleaseKind.valueOf("ALPHA")).isEqualTo(JillProvider.SubReleaseKind.ALPHA);
        assertThat(JillProvider.SubReleaseKind.valueOf("BETA")).isEqualTo(JillProvider.SubReleaseKind.BETA);
        assertThat(JillProvider.SubReleaseKind.valueOf("CANDIDATE"))
                .isEqualTo(JillProvider.SubReleaseKind.CANDIDATE);
        assertThat(JillProvider.SubReleaseKind.valueOf("RELEASE")).isEqualTo(JillProvider.SubReleaseKind.RELEASE);
    }

    private static final class ProviderWithoutOptionalMetadata implements JillProvider {
        @Override
        public <T extends JillConfig> T createConfig(Class<T> configType) throws ConfigNotSupportedException {
            throw new ConfigNotSupportedException("No configurations are supported");
        }

        @Override
        public <T extends JillConfig> boolean isConfigSupported(Class<T> configType) {
            return false;
        }

        @Override
        public Collection<Class<? extends JillConfig>> getSupportedConfigs() {
            return Collections.emptySet();
        }

        @Override
        public String getTranslatorVersion() {
            return "test-translator";
        }

        @Override
        public String getTranslatorReleaseName() {
            return "test-release";
        }

        @Override
        public int getTranslatorReleaseCode() {
            return 1;
        }

        @Override
        public int getTranslatorSubReleaseCode() {
            return 0;
        }

        @Override
        public SubReleaseKind getTranslatorSubReleaseKind() {
            return SubReleaseKind.RELEASE;
        }

        @Override
        public String getTranslatorBuildId() {
            return null;
        }

        @Override
        public String getTranslatorSourceCodeBase() {
            return null;
        }
    }

    private static final class RecordingJillProvider implements JillProvider {
        private final Set<Class<? extends JillConfig>> supportedConfigs = new LinkedHashSet<>();

        private RecordingJillProvider() {
            supportedConfigs.add(Api01Config.class);
        }

        @Override
        public <T extends JillConfig> T createConfig(Class<T> configType) throws ConfigNotSupportedException {
            if (!isConfigSupported(configType)) {
                throw new ConfigNotSupportedException("Unsupported Jill configuration: " + configType.getName());
            }
            return configType.cast(new RecordingApi01Config());
        }

        @Override
        public <T extends JillConfig> boolean isConfigSupported(Class<T> configType) {
            return supportedConfigs.contains(configType);
        }

        @Override
        public Collection<Class<? extends JillConfig>> getSupportedConfigs() {
            return Collections.unmodifiableSet(supportedConfigs);
        }

        @Override
        public String getTranslatorVersion() {
            return "0.9.0-test-translator";
        }

        @Override
        public String getTranslatorReleaseName() {
            return "integration-test-release";
        }

        @Override
        public int getTranslatorReleaseCode() {
            return 9;
        }

        @Override
        public int getTranslatorSubReleaseCode() {
            return 0;
        }

        @Override
        public SubReleaseKind getTranslatorSubReleaseKind() {
            return SubReleaseKind.BETA;
        }

        @Override
        public String getTranslatorBuildId() {
            return "build-0.9.0";
        }

        @Override
        public String getTranslatorSourceCodeBase() {
            return "https://example.test/android/jill";
        }
    }

    private interface UnsupportedConfig extends JillConfig {
    }

    private static final class RecordingApi01Config implements Api01Config {
        private File inputJavaBinaryFile;
        private File outputJackFile;
        private boolean verbose;
        private boolean debugInfo;

        @Override
        public void setVerbose(boolean verbose) {
            this.verbose = verbose;
        }

        @Override
        public void setInputJavaBinaryFile(File inputJavaBinaryFile) throws ConfigurationException {
            if (inputJavaBinaryFile == null) {
                throw new ConfigurationException("Input Java binary file must not be null");
            }
            this.inputJavaBinaryFile = inputJavaBinaryFile;
        }

        @Override
        public void setOutputJackFile(File outputJackFile) throws ConfigurationException {
            if (outputJackFile == null) {
                throw new ConfigurationException("Output Jack file must not be null");
            }
            this.outputJackFile = outputJackFile;
        }

        @Override
        public void setDebugInfo(boolean debugInfo) {
            this.debugInfo = debugInfo;
        }

        @Override
        public Api01TranslationTask getTask() throws ConfigurationException {
            if (inputJavaBinaryFile == null) {
                throw new ConfigurationException("Input Java binary file has not been configured");
            }
            if (outputJackFile == null) {
                throw new ConfigurationException("Output Jack file has not been configured");
            }
            return new RecordingTranslationTask(inputJavaBinaryFile, outputJackFile, verbose, debugInfo);
        }
    }

    private static final class RecordingTranslationTask implements Api01TranslationTask {
        private final File inputJavaBinaryFile;
        private final File outputJackFile;
        private final boolean verbose;
        private final boolean debugInfo;
        private int runCount;

        private RecordingTranslationTask(
                File inputJavaBinaryFile, File outputJackFile, boolean verbose, boolean debugInfo) {
            this.inputJavaBinaryFile = inputJavaBinaryFile;
            this.outputJackFile = outputJackFile;
            this.verbose = verbose;
            this.debugInfo = debugInfo;
        }

        @Override
        public void run() throws TranslationException {
            if (runCount > 0) {
                throw new IllegalStateException("Translation task instances are single-use");
            }
            if (!inputJavaBinaryFile.isFile()) {
                throw new TranslationException("Input Java binary file does not exist: " + inputJavaBinaryFile);
            }
            runCount++;
            try {
                Files.writeString(
                        outputJackFile.toPath(),
                        "translated "
                                + inputJavaBinaryFile.getName()
                                + " with debug="
                                + debugInfo
                                + " and verbose="
                                + verbose);
            } catch (IOException exception) {
                throw new TranslationException("Unable to write Jack output", exception);
            }
        }

        private File getInputJavaBinaryFile() {
            return inputJavaBinaryFile;
        }

        private File getOutputJackFile() {
            return outputJackFile;
        }

        private boolean isVerbose() {
            return verbose;
        }

        private boolean hasDebugInfo() {
            return debugInfo;
        }

        private int getRunCount() {
            return runCount;
        }
    }
}
