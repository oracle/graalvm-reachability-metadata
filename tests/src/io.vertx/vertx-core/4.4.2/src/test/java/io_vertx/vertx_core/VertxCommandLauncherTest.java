/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.spi.launcher.Command;
import io.vertx.core.spi.launcher.CommandFactory;
import io.vertx.core.spi.launcher.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxCommandLauncherTest {

    @Test
    void dispatchReadsLauncherAttributesFromMatchingManifest() {
        VertxCommandLauncher launcher = new VertxCommandLauncher(Collections.emptyList());
        ManifestBackedCommandFactory factory = new ManifestBackedCommandFactory();
        launcher.register(factory);

        launcher.dispatch(this, new String[] {"manifest-backed"});

        ManifestBackedCommand command = factory.command();
        assertNotNull(command);
        assertTrue(command.wasRun());
        ExecutionContext context = command.context();
        assertNotNull(context);
        assertSame(this, context.main());
        assertEquals(VertxCommandLauncherTest.class.getName(), context.get("Main-Class"));
        assertEquals("test-verticle-factory", context.get("Default-Verticle-Factory"));
    }

    private static class ManifestBackedCommandFactory implements CommandFactory<ManifestBackedCommand> {
        private final AtomicReference<ManifestBackedCommand> command = new AtomicReference<>();

        @Override
        public ManifestBackedCommand create(CommandLine evaluated) {
            ManifestBackedCommand next = new ManifestBackedCommand();
            command.set(next);
            return next;
        }

        @Override
        public CLI define() {
            return CLI.create("manifest-backed");
        }

        ManifestBackedCommand command() {
            return command.get();
        }
    }

    private static class ManifestBackedCommand implements Command {
        private ExecutionContext context;
        private boolean run;

        @Override
        public void setUp(ExecutionContext context) throws CLIException {
            this.context = context;
        }

        @Override
        public void run() throws CLIException {
            run = true;
        }

        @Override
        public void tearDown() throws CLIException {
        }

        ExecutionContext context() {
            return context;
        }

        boolean wasRun() {
            return run;
        }
    }
}
