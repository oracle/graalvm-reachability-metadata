/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.integration.commandline.LiquibaseLauncher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class LiquibaseLauncherTest {

    private static final String COMMAND_LINE_CLASS_NAME = "liquibase.integration.commandline.LiquibaseCommandLine";

    @Test
    void mainLoadsCommandLineFromThreadParentAndForwardsArguments() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        LauncherParentClassLoader parentClassLoader = new LauncherParentClassLoader(originalContextClassLoader);
        LauncherCommand.reset();
        Thread.currentThread().setContextClassLoader(parentClassLoader);

        try {
            LiquibaseLauncher.main(new String[] {"--version"});

            assertThat(parentClassLoader.commandLineRequested).isTrue();
            assertThat(LauncherCommand.arguments).containsExactly("--version");
            assertThat(LauncherCommand.contextClassLoader)
                    .isInstanceOf(URLClassLoader.class)
                    .isNotSameAs(parentClassLoader);
        } finally {
            closeLauncherClassLoader(parentClassLoader);
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            LauncherCommand.reset();
        }
    }

    private static void closeLauncherClassLoader(ClassLoader parentClassLoader) throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof URLClassLoader && contextClassLoader != parentClassLoader) {
            ((URLClassLoader) contextClassLoader).close();
        }
    }

    public static final class LauncherCommand {

        private static String[] arguments;
        private static ClassLoader contextClassLoader;

        public static void main(String[] args) {
            arguments = args.clone();
            contextClassLoader = Thread.currentThread().getContextClassLoader();
        }

        private static void reset() {
            arguments = null;
            contextClassLoader = null;
        }
    }

    private static final class LauncherParentClassLoader extends ClassLoader {

        private boolean commandLineRequested;

        private LauncherParentClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (COMMAND_LINE_CLASS_NAME.equals(name)) {
                commandLineRequested = true;
                return LauncherCommand.class;
            }
            return super.loadClass(name, resolve);
        }
    }
}
