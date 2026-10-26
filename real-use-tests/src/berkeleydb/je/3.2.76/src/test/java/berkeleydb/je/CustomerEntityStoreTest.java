/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JE's Direct Persistence Layer to maintain a durable customer directory of annotated entities.
@Timeout(60)
public class CustomerEntityStoreTest {
    @TempDir
    Path home;

    @Test
    public void registersCustomersChangesContactDetailsAndReopensDirectory() throws DatabaseException {
        try (CustomerDirectory directory = new CustomerDirectory(home, true)) {
            assertTrue(directory.customers.putNoOverwrite(new Customer(
                    "customer-10", "Ada North", "ada@example.test", 120)));
            assertTrue(directory.customers.putNoOverwrite(new Customer(
                    "customer-20", "Ben West", "ben@example.test", 50)));

            // Retrying a registration must not overwrite the established customer account.
            assertFalse(directory.customers.putNoOverwrite(new Customer(
                    "customer-10", "Duplicate", "duplicate@example.test", 0)));
            assertCustomer(directory.customers.get("customer-10"),
                    "customer-10", "Ada North", "ada@example.test", 120);
            assertCustomer(directory.byEmail.get("ada@example.test"),
                    "customer-10", "Ada North", "ada@example.test", 120);

            Customer ada = directory.customers.get("customer-10");
            ada.email = "ada.north@example.test";
            ada.rewardPoints += 30;
            directory.customers.put(ada);
            assertNull(directory.byEmail.get("ada@example.test"));
            assertCustomer(directory.byEmail.get("ada.north@example.test"),
                    "customer-10", "Ada North", "ada.north@example.test", 150);
            assertCustomer(directory.byEmail.get("ben@example.test"),
                    "customer-20", "Ben West", "ben@example.test", 50);

            assertTrue(directory.customers.delete("customer-20"));
            assertNull(directory.byEmail.get("ben@example.test"));
            assertEquals(1L, directory.customers.count());
        }

        // A new store handle reconstructs the entities and their annotation-defined indexes.
        try (CustomerDirectory directory = new CustomerDirectory(home, false)) {
            assertEquals(1L, directory.customers.count());
            assertCustomer(directory.customers.get("customer-10"),
                    "customer-10", "Ada North", "ada.north@example.test", 150);
            assertCustomer(directory.byEmail.get("ada.north@example.test"),
                    "customer-10", "Ada North", "ada.north@example.test", 150);
            assertNull(directory.byEmail.get("ada@example.test"));
            assertNull(directory.customers.get("customer-20"));
            assertNull(directory.byEmail.get("ben@example.test"));
            assertNull(directory.byEmail.get("duplicate@example.test"));
        }
    }

    private static void assertCustomer(Customer customer, String id, String name, String email, int points) {
        assertNotNull(customer);
        assertEquals(id, customer.id);
        assertEquals(name, customer.name);
        assertEquals(email, customer.email);
        assertEquals(points, customer.rewardPoints);
    }

    @Entity
    public static class Customer {
        @PrimaryKey
        private String id;

        private String name;

        @SecondaryKey(relate = Relationship.ONE_TO_ONE)
        private String email;

        private int rewardPoints;

        public Customer() {
            // Required by the Direct Persistence Layer when reading stored entities.
        }

        private Customer(String id, String name, String email, int rewardPoints) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.rewardPoints = rewardPoints;
        }
    }

    private static final class CustomerDirectory implements AutoCloseable {
        private final Environment environment;
        private final EntityStore store;
        private final PrimaryIndex<String, Customer> customers;
        private final SecondaryIndex<String, String, Customer> byEmail;

        private CustomerDirectory(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setTransactional(true);
            environmentConfig.setLockTimeout(10_000_000L);
            environmentConfig.setTxnTimeout(30_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);

            StoreConfig storeConfig = new StoreConfig();
            storeConfig.setAllowCreate(create);
            storeConfig.setTransactional(true);
            try {
                store = new EntityStore(environment, "customer-directory", storeConfig);
                try {
                    customers = store.getPrimaryIndex(String.class, Customer.class);
                    byEmail = store.getSecondaryIndex(customers, String.class, "email");
                } catch (DatabaseException | RuntimeException | Error failure) {
                    store.close();
                    throw failure;
                }
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        @Override
        public void close() throws DatabaseException {
            try {
                store.close();
            } finally {
                environment.close();
            }
        }
    }
}
