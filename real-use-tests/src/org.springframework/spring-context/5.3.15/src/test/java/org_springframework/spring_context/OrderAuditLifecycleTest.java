/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Spring managed lifecycle phases to close order intake before flushing its buffered audit trail.
public class OrderAuditLifecycleTest {

    @Test
    @Timeout(60)
    public void startsTheAuditSinkFirstAndFlushesItAfterIntakeStops(@TempDir Path directory) throws IOException {
        Path auditFile = directory.resolve("orders.log");
        BufferedAuditSink audit = new BufferedAuditSink(auditFile);
        OrderIntake intake = new OrderIntake(audit);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            // Registration order is not the required startup order; lifecycle phases decide it.
            context.registerBean("orderIntake", OrderIntake.class, () -> intake);
            context.registerBean("auditSink", BufferedAuditSink.class, () -> audit);
            context.refresh();

            assertEquals("Accepted order-42", intake.accept("order-42"));
            assertEquals("Accepted order-43", intake.accept("order-43"));
            assertFalse(Files.exists(auditFile));

            context.stop();

            assertEquals(List.of(
                    "audit opened",
                    "intake opened",
                    "accepted order-42",
                    "accepted order-43",
                    "intake closed",
                    "audit closed"), Files.readAllLines(auditFile, StandardCharsets.UTF_8));
            IllegalStateException rejection = assertThrows(IllegalStateException.class,
                    () -> intake.accept("order-44"));
            assertEquals("Order intake is closed", rejection.getMessage());
        }

        // Closing an already stopped context must not flush a second copy of the records.
        assertEquals(6, Files.readAllLines(auditFile, StandardCharsets.UTF_8).size());
    }

    public static final class BufferedAuditSink implements SmartLifecycle {
        private final Path destination;
        private final List<String> pending = new ArrayList<>();
        private boolean running;

        public BufferedAuditSink(Path destination) {
            this.destination = destination;
        }

        public void record(String entry) {
            if (!running) {
                throw new IllegalStateException("Audit sink is closed");
            }
            pending.add(entry);
        }

        @Override
        public void start() {
            running = true;
            record("audit opened");
        }

        @Override
        public void stop() {
            record("audit closed");
            try {
                Files.write(destination, pending, StandardCharsets.UTF_8);
                pending.clear();
                running = false;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return 0;
        }
    }

    public static final class OrderIntake implements SmartLifecycle {
        private final BufferedAuditSink audit;
        private boolean running;

        public OrderIntake(BufferedAuditSink audit) {
            this.audit = audit;
        }

        public String accept(String orderId) {
            if (!running) {
                throw new IllegalStateException("Order intake is closed");
            }
            audit.record("accepted " + orderId);
            return "Accepted " + orderId;
        }

        @Override
        public void start() {
            audit.record("intake opened");
            running = true;
        }

        @Override
        public void stop() {
            running = false;
            audit.record("intake closed");
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return 100;
        }
    }
}
