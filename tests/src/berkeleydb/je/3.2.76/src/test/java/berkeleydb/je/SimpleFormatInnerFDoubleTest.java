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
 * Exercises primitive double field persistence through {@code SimpleFormat.FDouble}.
 */
public class SimpleFormatInnerFDoubleTest {

    @Test
    void persistsAndRestoresPrimitiveDoubleField(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, DoubleMeasurementRecord> byId =
                store.getPrimaryIndex(Long.class, DoubleMeasurementRecord.class);

            byId.put(new DoubleMeasurementRecord(1L, 37.125D, "body"));
            byId.put(new DoubleMeasurementRecord(2L, -273.15D, "absoluteZero"));
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
            PrimaryIndex<Long, DoubleMeasurementRecord> byId =
                store.getPrimaryIndex(Long.class, DoubleMeasurementRecord.class);

            DoubleMeasurementRecord body = byId.get(1L);
            DoubleMeasurementRecord absoluteZero = byId.get(2L);

            assertThat(body).isNotNull();
            assertThat(body.value).isEqualTo(37.125D);
            assertThat(body.label).isEqualTo("body");
            assertThat(absoluteZero).isNotNull();
            assertThat(absoluteZero.value).isEqualTo(-273.15D);
            assertThat(absoluteZero.label).isEqualTo("absoluteZero");
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
        return new EntityStore(environment, "simpleFormatFDoubleStore", config);
    }

    @Entity
    private static class DoubleMeasurementRecord {
        @PrimaryKey
        private Long id;

        private double value;
        private String label;

        private DoubleMeasurementRecord() {
        }

        private DoubleMeasurementRecord(Long id, double value, String label) {
            this.id = id;
            this.value = value;
            this.label = label;
        }
    }
}
