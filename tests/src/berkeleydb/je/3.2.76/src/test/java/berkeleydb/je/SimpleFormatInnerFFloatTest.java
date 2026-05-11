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
 * Exercises primitive float field persistence through {@code SimpleFormat.FFloat}.
 */
public class SimpleFormatInnerFFloatTest {

    @Test
    void persistsAndRestoresPrimitiveFloatField(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, FloatMeasurementRecord> byId =
                store.getPrimaryIndex(Long.class, FloatMeasurementRecord.class);

            byId.put(new FloatMeasurementRecord(1L, 98.6F, "normal"));
            byId.put(new FloatMeasurementRecord(2L, -40.25F, "freezing"));
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
            PrimaryIndex<Long, FloatMeasurementRecord> byId =
                store.getPrimaryIndex(Long.class, FloatMeasurementRecord.class);

            FloatMeasurementRecord normal = byId.get(1L);
            FloatMeasurementRecord freezing = byId.get(2L);

            assertThat(normal).isNotNull();
            assertThat(normal.value).isEqualTo(98.6F);
            assertThat(normal.label).isEqualTo("normal");
            assertThat(freezing).isNotNull();
            assertThat(freezing.value).isEqualTo(-40.25F);
            assertThat(freezing.label).isEqualTo("freezing");
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
        return new EntityStore(environment, "simpleFormatFFloatStore", config);
    }

    @Entity
    private static class FloatMeasurementRecord {
        @PrimaryKey
        private Long id;

        private float value;
        private String label;

        private FloatMeasurementRecord() {
        }

        private FloatMeasurementRecord(Long id, float value, String label) {
            this.id = id;
            this.value = value;
            this.label = label;
        }
    }
}
