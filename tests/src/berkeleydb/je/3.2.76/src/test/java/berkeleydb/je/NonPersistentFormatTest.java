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
 * Exercises Berkeley DB DPL allocation of arrays whose component type is not persistent.
 */
public class NonPersistentFormatTest {

    @Test
    void objectArrayReadUsesNonPersistentComponentFormat(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, ObjectArrayRecord> byId =
                store.getPrimaryIndex(Long.class, ObjectArrayRecord.class);
            byId.put(new ObjectArrayRecord(1L, new Object[] {"alpha", "beta", null}));

            ObjectArrayRecord restored = byId.get(1L);

            assertThat(restored).isNotNull();
            assertThat(restored.values.getClass()).isEqualTo(Object[].class);
            assertThat(restored.values).containsExactly("alpha", "beta", null);
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
        return new EntityStore(environment, "nonPersistentFormatStore", config);
    }

    @Entity
    public static class ObjectArrayRecord {
        @PrimaryKey
        private Long id;

        private Object[] values;

        public ObjectArrayRecord() {
        }

        ObjectArrayRecord(Long id, Object[] values) {
            this.id = id;
            this.values = values;
        }
    }
}
