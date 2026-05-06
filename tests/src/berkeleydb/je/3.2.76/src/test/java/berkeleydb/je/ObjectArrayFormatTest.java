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
 * Exercises Berkeley DB DPL materialization of multidimensional object arrays.
 */
public class ObjectArrayFormatTest {

    @Test
    void readsTwoDimensionalStringArray(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, StringMatrixRecord> byId =
                store.getPrimaryIndex(Long.class, StringMatrixRecord.class);
            byId.put(new StringMatrixRecord(1L, new String[][] {
                {"alpha", "beta"},
                {"gamma"}
            }));

            StringMatrixRecord restored = byId.get(1L);

            assertThat(restored).isNotNull();
            assertThat(restored.values.getClass()).isEqualTo(String[][].class);
            assertThat(restored.values.length).isEqualTo(2);
            assertThat(restored.values[0]).containsExactly("alpha", "beta");
            assertThat(restored.values[1]).containsExactly("gamma");
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
        return new EntityStore(environment, "objectArrayFormatStore", config);
    }

    @Entity
    public static class StringMatrixRecord {
        @PrimaryKey
        private Long id;

        private String[][] values;

        public StringMatrixRecord() {
        }

        StringMatrixRecord(Long id, String[][] values) {
            this.id = id;
            this.values = values;
        }
    }
}
