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
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises sequence assignment for a primitive primary key field.
 */
public class ReflectionAccessorInnerPrimitiveAccessTest {

    @Test
    void assignsSequenceWhenPrimitiveLongPrimaryKeyFieldIsZero(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, PrimitiveSequencedRecord> byId =
                store.getPrimaryIndex(Long.class, PrimitiveSequencedRecord.class);
            PrimitiveSequencedRecord record = new PrimitiveSequencedRecord("generated primitive id");

            byId.putNoReturn(record);

            assertThat(record.id).isPositive();
            PrimitiveSequencedRecord stored = byId.get(record.id);
            assertThat(stored).isNotNull();
            assertThat(stored.description).isEqualTo("generated primitive id");
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
        return new EntityStore(environment, "reflectionAccessorPrimitiveAccessStore", config);
    }

    @Entity
    private static class PrimitiveSequencedRecord {
        @PrimaryKey(sequence = "reflectionAccessorPrimitiveAccessIds")
        private long id;

        private String description;

        private PrimitiveSequencedRecord() {
        }

        private PrimitiveSequencedRecord(String description) {
            this.description = description;
        }
    }
}
