/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises JE serial bindings and the stored class catalog to retain a mail outbox across restarts.
@Timeout(60)
public class SerializedMailOutboxTest {
    @TempDir
    Path home;

    @Test
    public void reloadsQueuedMessagesAndPersistsRetryProgress() throws DatabaseException {
        try (MailOutbox outbox = new MailOutbox(home, true)) {
            outbox.save("receipt-100", new Mail("ada@example.test", "Order receipt", "Paid: 4200 cents", 0));
            outbox.save("dispatch-100", new Mail("ada@example.test", "Dispatch notice", "Tracking: BOX-100", 0));
            assertMail(outbox.find("receipt-100"), "ada@example.test", "Order receipt", "Paid: 4200 cents", 0);
            assertMail(outbox.find("dispatch-100"), "ada@example.test", "Dispatch notice", "Tracking: BOX-100", 0);
            assertNull(outbox.find("unknown-message"));
        }

        // A new binding must resolve saved class descriptors from the persistent catalog.
        try (MailOutbox outbox = new MailOutbox(home, false)) {
            Mail receipt = outbox.find("receipt-100");
            assertMail(receipt, "ada@example.test", "Order receipt", "Paid: 4200 cents", 0);
            receipt.attempts++;
            outbox.save("receipt-100", receipt);

            // A confirmed delivery is removed; the undelivered receipt remains queued for retry.
            assertMail(outbox.find("dispatch-100"), "ada@example.test", "Dispatch notice", "Tracking: BOX-100", 0);
            outbox.delivered("dispatch-100");
            assertNull(outbox.find("dispatch-100"));
            assertMail(outbox.find("receipt-100"), "ada@example.test", "Order receipt", "Paid: 4200 cents", 1);
        }

        try (MailOutbox outbox = new MailOutbox(home, false)) {
            assertNull(outbox.find("dispatch-100"));
            assertMail(outbox.find("receipt-100"), "ada@example.test", "Order receipt", "Paid: 4200 cents", 1);
            outbox.save("receipt-101", new Mail("ben@example.test", "Order receipt", "Paid: 1800 cents", 0));
            assertMail(outbox.find("receipt-101"), "ben@example.test", "Order receipt", "Paid: 1800 cents", 0);
            outbox.delivered("receipt-100");
        }

        try (MailOutbox outbox = new MailOutbox(home, false)) {
            assertNull(outbox.find("receipt-100"));
            assertNull(outbox.find("dispatch-100"));
            assertMail(outbox.find("receipt-101"), "ben@example.test", "Order receipt", "Paid: 1800 cents", 0);
        }
    }

    private static void assertMail(Mail mail, String recipient, String subject, String body, int attempts) {
        assertNotNull(mail);
        assertEquals(recipient, mail.recipient);
        assertEquals(subject, mail.subject);
        assertEquals(body, mail.body);
        assertEquals(attempts, mail.attempts);
    }

    public static final class Mail implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String recipient;
        private final String subject;
        private final String body;
        private int attempts;

        private Mail(String recipient, String subject, String body, int attempts) {
            this.recipient = recipient;
            this.subject = subject;
            this.body = body;
            this.attempts = attempts;
        }
    }

    private static final class MailOutbox implements AutoCloseable {
        private final Environment environment;
        private final StoredClassCatalog catalog;
        private final Database messages;
        private final SerialBinding binding;

        private MailOutbox(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setLockTimeout(10_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            try {
                Database catalogDatabase = environment.openDatabase(null, "mail-class-catalog", databaseConfig);
                try {
                    catalog = new StoredClassCatalog(catalogDatabase);
                } catch (DatabaseException | RuntimeException | Error failure) {
                    catalogDatabase.close();
                    throw failure;
                }
                try {
                    binding = new SerialBinding(catalog, Mail.class);
                    messages = environment.openDatabase(null, "mail-outbox", databaseConfig);
                } catch (DatabaseException | RuntimeException | Error failure) {
                    catalog.close();
                    throw failure;
                }
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void save(String id, Mail mail) throws DatabaseException {
            DatabaseEntry data = new DatabaseEntry();
            binding.objectToEntry(mail, data);
            assertEquals(OperationStatus.SUCCESS, messages.put(null, key(id), data));
        }

        private Mail find(String id) throws DatabaseException {
            DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = messages.get(null, key(id), data, LockMode.DEFAULT);
            if (status == OperationStatus.NOTFOUND) {
                return null;
            }
            assertEquals(OperationStatus.SUCCESS, status);
            return (Mail) binding.entryToObject(data);
        }

        private void delivered(String id) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, messages.delete(null, key(id)));
        }

        private static DatabaseEntry key(String id) {
            DatabaseEntry entry = new DatabaseEntry();
            StringBinding.stringToEntry(id, entry);
            return entry;
        }

        @Override
        public void close() throws DatabaseException {
            try {
                messages.close();
            } finally {
                try {
                    // StoredClassCatalog owns and closes its database handle.
                    catalog.close();
                } finally {
                    environment.close();
                }
            }
        }
    }
}
