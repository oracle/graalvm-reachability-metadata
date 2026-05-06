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
 * Exercises Berkeley DB DPL materialization of primitive array components.
 */
public class PrimitiveArrayFormatTest {

    @Test
    void readsTwoDimensionalPrimitiveIntArray(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, IntMatrixRecord> byId =
                store.getPrimaryIndex(Long.class, IntMatrixRecord.class);
            byId.put(new IntMatrixRecord(1L, new int[][] {
                {1, 2, 3},
                {4, 5}
            }));

            IntMatrixRecord restored = byId.get(1L);

            assertThat(restored).isNotNull();
            assertThat(restored.values.getClass()).isEqualTo(int[][].class);
            assertThat(restored.values.length).isEqualTo(2);
            assertThat(restored.values[0]).containsExactly(1, 2, 3);
            assertThat(restored.values[1]).containsExactly(4, 5);
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
        return new EntityStore(environment, "primitiveArrayFormatStore", config);
    }

    @Entity
    public static class IntMatrixRecord {
        @PrimaryKey
        private Long id;

        private int[][] values;

        public IntMatrixRecord() {
        }

        IntMatrixRecord(Long id, int[][] values) {
            this.id = id;
            this.values = values;
        }
    }
}
