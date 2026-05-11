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
 * Exercises primitive byte field persistence through {@code SimpleFormat.FByte}.
 */
public class SimpleFormatInnerFByteTest {

    @Test
    void persistsAndRestoresPrimitiveByteField(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, ByteLevelRecord> byId =
                store.getPrimaryIndex(Long.class, ByteLevelRecord.class);

            byId.put(new ByteLevelRecord(1L, (byte) 12, "low"));
            byId.put(new ByteLevelRecord(2L, (byte) -7, "belowZero"));
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
            PrimaryIndex<Long, ByteLevelRecord> byId =
                store.getPrimaryIndex(Long.class, ByteLevelRecord.class);

            ByteLevelRecord low = byId.get(1L);
            ByteLevelRecord belowZero = byId.get(2L);

            assertThat(low).isNotNull();
            assertThat(low.level).isEqualTo((byte) 12);
            assertThat(low.label).isEqualTo("low");
            assertThat(belowZero).isNotNull();
            assertThat(belowZero.level).isEqualTo((byte) -7);
            assertThat(belowZero.label).isEqualTo("belowZero");
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
        return new EntityStore(environment, "simpleFormatFByteStore", config);
    }

    @Entity
    private static class ByteLevelRecord {
        @PrimaryKey
        private Long id;

        private byte level;
        private String label;

        private ByteLevelRecord() {
        }

        private ByteLevelRecord(Long id, byte level, String label) {
            this.id = id;
            this.level = level;
            this.label = label;
        }
    }
}
