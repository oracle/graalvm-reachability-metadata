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
 * Exercises sequence assignment for a nullable object primary key.
 */
public class ReflectionAccessorInnerFieldAccessTest {

    @Test
    void assignsSequenceWhenObjectPrimaryKeyFieldIsNull(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, SequencedRecord> byId =
                store.getPrimaryIndex(Long.class, SequencedRecord.class);
            SequencedRecord record = new SequencedRecord("first generated id");

            byId.putNoReturn(record);

            assertThat(record.id).isNotNull();
            assertThat(record.id).isPositive();
            SequencedRecord stored = byId.get(record.id);
            assertThat(stored).isNotNull();
            assertThat(stored.description).isEqualTo("first generated id");
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
        return new EntityStore(environment, "reflectionAccessorFieldAccessStore", config);
    }

    @Entity
    private static class SequencedRecord {
        @PrimaryKey(sequence = "reflectionAccessorFieldAccessIds")
        private Long id;

        private String description;

        private SequencedRecord() {
        }

        private SequencedRecord(String description) {
            this.description = description;
        }
    }
}
