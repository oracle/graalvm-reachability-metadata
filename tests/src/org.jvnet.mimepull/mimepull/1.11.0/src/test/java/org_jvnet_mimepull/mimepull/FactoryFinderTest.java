/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jvnet_mimepull.mimepull;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.jvnet.mimepull.CleanUpExecutorFactory;
import org.jvnet.mimepull.MIMEConfig;
import org.jvnet.mimepull.MIMEMessage;
import org.junit.jupiter.api.Test;

public class FactoryFinderTest extends CleanUpExecutorFactory {

    private static final ScheduledExecutorService CLEANUP_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor();

    @Test
    void storesAndReadsLargeAttachmentWithConfiguredCleanupFactory() throws IOException {
        String previousFactory = System.getProperty(CleanUpExecutorFactory.class.getName());
        System.setProperty(CleanUpExecutorFactory.class.getName(), getClass().getName());
        String payload = "x".repeat(16_384);
        String message = "--boundary\r\n"
                + "Content-ID: <attachment>\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + payload
                + "\r\n"
                + "--boundary--\r\n";

        try {
            MIMEConfig config = new MIMEConfig();
            config.setMemoryThreshold(0);
            try (MIMEMessage mimeMessage = new MIMEMessage(
                    new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)),
                    "boundary", config)) {
                InputStream content = mimeMessage.getPart("attachment").read();
                assertArrayEquals(payload.getBytes(StandardCharsets.UTF_8), content.readAllBytes());
                content.close();
            }
        } finally {
            CLEANUP_EXECUTOR.shutdownNow();
            if (previousFactory == null) {
                System.clearProperty(CleanUpExecutorFactory.class.getName());
            } else {
                System.setProperty(CleanUpExecutorFactory.class.getName(), previousFactory);
            }
        }
    }

    @Override
    public ScheduledExecutorService getScheduledExecutorService() {
        return CLEANUP_EXECUTOR;
    }
}
