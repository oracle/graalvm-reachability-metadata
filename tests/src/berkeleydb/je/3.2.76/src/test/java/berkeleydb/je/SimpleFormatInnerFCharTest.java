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
 * Exercises primitive char field persistence through {@code SimpleFormat.FChar}.
 */
public class SimpleFormatInnerFCharTest {

    @Test
    void persistsAndRestoresPrimitiveCharField(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, CharMarkerRecord> byId =
                store.getPrimaryIndex(Long.class, CharMarkerRecord.class);

            byId.put(new CharMarkerRecord(1L, 'A', "latin"));
            byId.put(new CharMarkerRecord(2L, '\u03a9', "omega"));
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
            PrimaryIndex<Long, CharMarkerRecord> byId =
                store.getPrimaryIndex(Long.class, CharMarkerRecord.class);

            CharMarkerRecord latin = byId.get(1L);
            CharMarkerRecord omega = byId.get(2L);

            assertThat(latin).isNotNull();
            assertThat(latin.marker).isEqualTo('A');
            assertThat(latin.label).isEqualTo("latin");
            assertThat(omega).isNotNull();
            assertThat(omega.marker).isEqualTo('\u03a9');
            assertThat(omega.label).isEqualTo("omega");
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
        return new EntityStore(environment, "simpleFormatFCharStore", config);
    }

    @Entity
    private static class CharMarkerRecord {
        @PrimaryKey
        private Long id;

        private char marker;
        private String label;

        private CharMarkerRecord() {
        }

        private CharMarkerRecord(Long id, char marker, String label) {
            this.id = id;
            this.marker = marker;
            this.label = label;
        }
    }
}
