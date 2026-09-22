/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_daemon_embeddable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Timer;
import java.util.logging.LogManager;

import kotlin.Pair;
import kotlin.jvm.functions.Function0;
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties;
import org.jetbrains.kotlin.daemon.CompileServiceImplBase;
import org.jetbrains.kotlin.daemon.CompilerSelector;
import org.jetbrains.kotlin.daemon.KotlinCompileDaemonBase;
import org.jetbrains.kotlin.daemon.common.CompilerId;
import org.jetbrains.kotlin.daemon.common.DaemonJVMOptions;
import org.jetbrains.kotlin.daemon.common.DaemonOptions;
import org.jetbrains.kotlin.daemon.common.JavaLanguageVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class KotlinCompileDaemonBaseTest {

    @Test
    void mainImplLoadsImplementationVersionFromManifestBeforeStartingServer(@TempDir Path tempDir) throws Exception {
        Map<CompilerSystemProperties, String> previousValues = snapshotProperties(
                CompilerSystemProperties.COMPILE_DAEMON_LOG_PATH_PROPERTY
        );
        try {
            Path logFile = tempDir.resolve("daemon.log");
            Files.createFile(logFile);
            CompilerSystemProperties.COMPILE_DAEMON_LOG_PATH_PROPERTY.setValue(logFile.toString());

            VersionLoadingDaemon daemon = new VersionLoadingDaemon();

            daemon.startWithoutServer();

            assertThat(daemon.synchronizedSectionReached).isTrue();
        } finally {
            restoreProperties(previousValues);
            LogManager.getLogManager().reset();
        }
    }

    private static Map<CompilerSystemProperties, String> snapshotProperties(CompilerSystemProperties... properties) {
        Map<CompilerSystemProperties, String> previousValues = new EnumMap<>(CompilerSystemProperties.class);
        for (CompilerSystemProperties property : properties) {
            previousValues.put(property, property.getValue());
        }
        return previousValues;
    }

    private static void restoreProperties(Map<CompilerSystemProperties, String> previousValues) {
        for (Map.Entry<CompilerSystemProperties, String> entry : previousValues.entrySet()) {
            if (entry.getValue() == null) {
                entry.getKey().clear();
            } else {
                entry.getKey().setValue(entry.getValue());
            }
        }
    }

    private static final class VersionLoadingDaemon extends KotlinCompileDaemonBase {

        private boolean synchronizedSectionReached;

        private void startWithoutServer() {
            mainImpl(new String[0]);
        }

        @Override
        protected <T> T runSynchronized(Function0<? extends T> block) {
            synchronizedSectionReached = true;
            return null;
        }

        @Override
        protected Pair<CompileServiceImplBase, Integer> getCompileServiceAndPort(
                CompilerSelector compilerSelector,
                CompilerId compilerId,
                JavaLanguageVersion javaLanguageVersion,
                DaemonOptions daemonOptions,
                DaemonJVMOptions daemonJVMOptions,
                Timer timer
        ) {
            throw new AssertionError("Server startup should not be reached");
        }
    }
}
