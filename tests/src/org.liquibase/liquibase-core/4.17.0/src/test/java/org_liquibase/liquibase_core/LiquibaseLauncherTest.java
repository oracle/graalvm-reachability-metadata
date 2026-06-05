/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.integration.commandline.LiquibaseLauncher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

public class LiquibaseLauncherTest {

    @Test
    void delegatesToCommandLineMainThroughLauncherClassLoader() throws Exception {
        final ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        final PrintStream previousOutput = System.out;
        final PrintStream previousError = System.err;
        final String configuredLiquibaseHome = System.getenv("LIQUIBASE_HOME");
        if (configuredLiquibaseHome == null || configuredLiquibaseHome.isBlank()) {
            assertThatThrownBy(() -> LiquibaseLauncher.main(new String[] {"--version"}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unable to find LIQUIBASE_HOME environment variable");
        } else {
            final Path liquibaseHome = Path.of(configuredLiquibaseHome);
            Files.createDirectories(liquibaseHome.resolve("internal/lib"));

            try {
                final FailingPrintStream failingStream = new FailingPrintStream();
                System.setOut(failingStream);
                System.setErr(failingStream);
                LiquibaseLauncher.main(new String[] {"--version"});
                fail("LiquibaseCommandLine.main should write the requested version before exiting");
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            } catch (Throwable throwable) {
                final Error error = findCause(throwable, Error.class);
                if (error != null) {
                    if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                        throw error;
                    }
                } else {
                    assertThat(findCause(throwable, OutputBlockedException.class)).isNotNull();
                }
            } finally {
                System.setOut(previousOutput);
                System.setErr(previousError);
                Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            }
        }
    }

    private static <T extends Throwable> T findCause(final Throwable throwable, final Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class FailingPrintStream extends PrintStream {
        private FailingPrintStream() {
            super(OutputStream.nullOutputStream());
        }

        @Override
        public void write(final int value) {
            throw new OutputBlockedException();
        }

        @Override
        public void write(final byte[] buffer, final int offset, final int length) {
            throw new OutputBlockedException();
        }

        @Override
        public void println(final String value) {
            throw new OutputBlockedException();
        }
    }

    private static final class OutputBlockedException extends RuntimeException {
    }
}
