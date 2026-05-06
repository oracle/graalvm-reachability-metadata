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
 * Exercises primitive boolean field persistence through {@code SimpleFormat.FBool}.
 */
public class SimpleFormatInnerFBoolTest {

    @Test
    void persistsAndRestoresPrimitiveBooleanField(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, BooleanFlagRecord> byId =
                store.getPrimaryIndex(Long.class, BooleanFlagRecord.class);

            byId.put(new BooleanFlagRecord(1L, true, "enabled"));
            byId.put(new BooleanFlagRecord(2L, false, "disabled"));
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
            PrimaryIndex<Long, BooleanFlagRecord> byId =
                store.getPrimaryIndex(Long.class, BooleanFlagRecord.class);

            BooleanFlagRecord enabled = byId.get(1L);
            BooleanFlagRecord disabled = byId.get(2L);

            assertThat(enabled).isNotNull();
            assertThat(enabled.enabled).isTrue();
            assertThat(enabled.label).isEqualTo("enabled");
            assertThat(disabled).isNotNull();
            assertThat(disabled.enabled).isFalse();
            assertThat(disabled.label).isEqualTo("disabled");
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
        return new EntityStore(environment, "simpleFormatFBoolStore", config);
    }

    @Entity
    private static class BooleanFlagRecord {
        @PrimaryKey
        private Long id;

        private boolean enabled;
        private String label;

        private BooleanFlagRecord() {
        }

        private BooleanFlagRecord(Long id, boolean enabled, String label) {
            this.id = id;
            this.enabled = enabled;
            this.label = label;
        }
    }
}
