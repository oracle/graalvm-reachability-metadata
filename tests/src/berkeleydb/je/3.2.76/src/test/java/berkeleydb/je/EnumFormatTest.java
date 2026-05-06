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
 * Exercises enum array materialization through Berkeley DB JE's DPL format layer.
 */
public class EnumFormatTest {

    @Test
    void readingEntityWithEnumArrayAllocatesEnumArray(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, EnumArrayRecord> byId = store.getPrimaryIndex(Long.class, EnumArrayRecord.class);
            byId.put(new EnumArrayRecord(1L, new ProcessingState[] {
                ProcessingState.NEW,
                ProcessingState.RUNNING,
                ProcessingState.DONE
            }));

            EnumArrayRecord restored = byId.get(1L);

            assertThat(restored).isNotNull();
            assertThat(restored.states).containsExactly(
                ProcessingState.NEW,
                ProcessingState.RUNNING,
                ProcessingState.DONE);
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
        return new EntityStore(environment, "enumFormatStore", config);
    }

    public enum ProcessingState {
        NEW,
        RUNNING,
        DONE
    }

    @Entity
    public static class EnumArrayRecord {
        @PrimaryKey
        private Long id;

        private ProcessingState[] states;

        public EnumArrayRecord() {
        }

        EnumArrayRecord(Long id, ProcessingState[] states) {
            this.id = id;
            this.states = states;
        }
    }
}
