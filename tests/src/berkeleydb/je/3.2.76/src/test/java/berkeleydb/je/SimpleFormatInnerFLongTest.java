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
 * Exercises primitive long field persistence through {@code SimpleFormat.FLong}.
 */
public class SimpleFormatInnerFLongTest {

    @Test
    void persistsAndRestoresPrimitiveLongField(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, LongCounterRecord> byId =
                store.getPrimaryIndex(Long.class, LongCounterRecord.class);

            byId.put(new LongCounterRecord(1L, 9_876_543_210L, "large"));
            byId.put(new LongCounterRecord(2L, -4_611_686_018_427_387_904L, "negative"));
        } finally {
            if (store != null) {
                store.close();
            }
            environment.close();
        }

        environment = openEnvironment(tmp);
        store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, LongCounterRecord> byId =
                store.getPrimaryIndex(Long.class, LongCounterRecord.class);

            LongCounterRecord large = byId.get(1L);
            LongCounterRecord negative = byId.get(2L);

            assertThat(large).isNotNull();
            assertThat(large.counter).isEqualTo(9_876_543_210L);
            assertThat(large.label).isEqualTo("large");
            assertThat(negative).isNotNull();
            assertThat(negative.counter).isEqualTo(-4_611_686_018_427_387_904L);
            assertThat(negative.label).isEqualTo("negative");
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
        return new EntityStore(environment, "simpleFormatFLongStore", config);
    }

    @Entity
    private static class LongCounterRecord {
        @PrimaryKey
        private Long id;

        private long counter;
        private String label;

        private LongCounterRecord() {
        }

        private LongCounterRecord(Long id, long counter, String label) {
            this.id = id;
            this.counter = counter;
            this.label = label;
        }
    }
}
