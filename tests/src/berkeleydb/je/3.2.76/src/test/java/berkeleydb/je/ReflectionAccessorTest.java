/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.KeyField;
import com.sleepycat.persist.model.Persistent;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Berkeley DB DPL reflection access for private persistent classes.
 */
public class ReflectionAccessorTest {

    @Test
    void persistsAndReadsCompositeKeyEntityWithNestedObjectArray(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<RecordKey, CustomerRecord> byId =
                store.getPrimaryIndex(RecordKey.class, CustomerRecord.class);
            SecondaryIndex<String, RecordKey, CustomerRecord> byCategory =
                store.getSecondaryIndex(byId, String.class, "category");

            RecordKey key = new RecordKey("customer", 42);
            CustomerRecord record = new CustomerRecord(
                key,
                "retail",
                "Ada Lovelace",
                new Address("London", "St James Square"),
                new Address[] {
                    new Address("London", "St James Square"),
                    new Address("Oxford", "High Street")
                });

            byId.put(record);

            CustomerRecord byPrimary = byId.get(key);
            assertThat(byPrimary).isNotNull();
            assertThat(byPrimary.key).isEqualTo(key);
            assertThat(byPrimary.name).isEqualTo("Ada Lovelace");
            assertThat(byPrimary.billingAddress.city).isEqualTo("London");
            assertThat(byPrimary.deliveryAddresses).hasSize(2);
            assertThat(byPrimary.deliveryAddresses[1].street).isEqualTo("High Street");

            CustomerRecord bySecondary = byCategory.get("retail");
            assertThat(bySecondary).isNotNull();
            assertThat(bySecondary.key).isEqualTo(key);
        } finally {
            if (store != null) {
                store.close();
            }
            environment.close();
        }
    }

    private static Environment openEnvironment(Path directory) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(directory.toFile(), config);
    }

    private static EntityStore openStore(Environment environment) throws Exception {
        StoreConfig config = new StoreConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new EntityStore(environment, "reflectionAccessorStore", config);
    }

    @Entity
    private static class CustomerRecord {
        @PrimaryKey
        private RecordKey key;

        @SecondaryKey(relate = MANY_TO_ONE)
        private String category;

        private String name;
        private Address billingAddress;
        private Address[] deliveryAddresses;

        private CustomerRecord() {
        }

        private CustomerRecord(RecordKey key,
                               String category,
                               String name,
                               Address billingAddress,
                               Address[] deliveryAddresses) {
            this.key = key;
            this.category = category;
            this.name = name;
            this.billingAddress = billingAddress;
            this.deliveryAddresses = deliveryAddresses;
        }
    }

    @Persistent
    private static class RecordKey {
        @KeyField(1)
        private String type;

        @KeyField(2)
        private int id;

        private RecordKey() {
        }

        private RecordKey(String type, int id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RecordKey)) {
                return false;
            }
            RecordKey other = (RecordKey) obj;
            return id == other.id && type.equals(other.type);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + id;
            return result;
        }
    }

    @Persistent
    private static class Address {
        private String city;
        private String street;

        private Address() {
        }

        private Address(String city, String street) {
            this.city = city;
            this.street = street;
        }
    }
}
