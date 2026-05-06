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
 * Exercises primitive short field persistence through {@code SimpleFormat.FShort}.
 */
public class SimpleFormatInnerFShortTest {

    @Test
    void persistsAndRestoresPrimitiveShortField(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, ShortQuotaRecord> byId =
                store.getPrimaryIndex(Long.class, ShortQuotaRecord.class);

            byId.put(new ShortQuotaRecord(1L, (short) 32_000, "nearMaximum"));
            byId.put(new ShortQuotaRecord(2L, (short) -12_345, "negative"));
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
            PrimaryIndex<Long, ShortQuotaRecord> byId =
                store.getPrimaryIndex(Long.class, ShortQuotaRecord.class);

            ShortQuotaRecord nearMaximum = byId.get(1L);
            ShortQuotaRecord negative = byId.get(2L);

            assertThat(nearMaximum).isNotNull();
            assertThat(nearMaximum.quota).isEqualTo((short) 32_000);
            assertThat(nearMaximum.label).isEqualTo("nearMaximum");
            assertThat(negative).isNotNull();
            assertThat(negative.quota).isEqualTo((short) -12_345);
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
        return new EntityStore(environment, "simpleFormatFShortStore", config);
    }

    @Entity
    private static class ShortQuotaRecord {
        @PrimaryKey
        private Long id;

        private short quota;
        private String label;

        private ShortQuotaRecord() {
        }

        private ShortQuotaRecord(Long id, short quota, String label) {
            this.id = id;
            this.quota = quota;
            this.label = label;
        }
    }
}
