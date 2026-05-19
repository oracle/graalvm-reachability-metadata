/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.netty.handler.logging.ByteBufFormat;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.impl.launcher.commands.BareCommand;
import io.vertx.core.spi.launcher.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BareCommandTest {

    @Test
    void runConfiguresOptionsFromSystemProperties() throws Exception {
        String eventLoopPoolSizeProperty = BareCommand.VERTX_OPTIONS_PROP_PREFIX + "eventLoopPoolSize";
        String activityLogDataFormatProperty = BareCommand.VERTX_EVENTBUS_PROP_PREFIX + "activityLogDataFormat";
        String previousEventLoopPoolSize = System.getProperty(eventLoopPoolSizeProperty);
        String previousActivityLogDataFormat = System.getProperty(activityLogDataFormatProperty);
        TestBareCommand command = new TestBareCommand();
        ExecutionContext context = new ExecutionContext(
                command,
                new VertxCommandLauncher(Collections.emptyList()),
                null);
        command.setExecutionContext(context);

        System.setProperty(eventLoopPoolSizeProperty, "1");
        System.setProperty(activityLogDataFormatProperty, ByteBufFormat.SIMPLE.name());
        try {
            command.run();
            VertxOptions options = command.configuredOptions();

            assertNotNull(command.vertx());
            assertNotNull(options);
            assertEquals(1, options.getEventLoopPoolSize());
            assertEquals(ByteBufFormat.SIMPLE, options.getEventBusOptions().getActivityLogDataFormat());
        } finally {
            closeVertx(command.vertx());
            restoreProperty(eventLoopPoolSizeProperty, previousEventLoopPoolSize);
            restoreProperty(activityLogDataFormatProperty, previousActivityLogDataFormat);
        }
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    private static void closeVertx(Vertx vertx) throws InterruptedException {
        if (vertx == null) {
            return;
        }
        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicReference<AsyncResult<Void>> closeResult = new AtomicReference<>();

        vertx.close(result -> {
            closeResult.set(result);
            closeLatch.countDown();
        });

        assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
        assertNotNull(closeResult.get());
        assertTrue(closeResult.get().succeeded(), () -> String.valueOf(closeResult.get().cause()));
    }

    private static class TestBareCommand extends BareCommand {
        @Override
        public boolean isClustered() {
            return false;
        }

        VertxOptions configuredOptions() {
            return options;
        }
    }
}
