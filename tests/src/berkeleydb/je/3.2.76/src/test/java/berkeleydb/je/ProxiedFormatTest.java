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
import com.sleepycat.persist.model.AnnotationModel;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.Persistent;
import com.sleepycat.persist.model.PersistentProxy;
import com.sleepycat.persist.model.PrimaryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Berkeley DB DPL array materialization for user-registered proxy types.
 */
public class ProxiedFormatTest {

    @Test
    void readsArrayOfProxiedValues(@TempDir Path tmp) throws Exception {
        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, LabelBatch> byId =
                store.getPrimaryIndex(Long.class, LabelBatch.class);
            byId.put(new LabelBatch(1L, new LocalizedLabel[] {
                new LocalizedLabel("en", "welcome"),
                new LocalizedLabel("fr", "bienvenue")
            }));
            store.close();
            store = null;

            store = openStore(environment);
            byId = store.getPrimaryIndex(Long.class, LabelBatch.class);
            LabelBatch restored = byId.get(1L);

            assertThat(restored).isNotNull();
            assertThat(restored.labels.length).isEqualTo(2);
            assertThat(restored.labels.getClass()).isEqualTo(LocalizedLabel[].class);
            assertThat(restored.labels[0].language).isEqualTo("en");
            assertThat(restored.labels[0].text).isEqualTo("welcome");
            assertThat(restored.labels[1].language).isEqualTo("fr");
            assertThat(restored.labels[1].text).isEqualTo("bienvenue");
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
        AnnotationModel model = new AnnotationModel();
        model.registerClass(LocalizedLabelProxy.class);

        StoreConfig config = new StoreConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        config.setModel(model);
        return new EntityStore(environment, "proxiedFormatStore", config);
    }

    public static final class LocalizedLabel {
        private final String language;
        private final String text;

        LocalizedLabel(String language, String text) {
            this.language = language;
            this.text = text;
        }
    }

    @Persistent(proxyFor = LocalizedLabel.class)
    public static class LocalizedLabelProxy implements PersistentProxy<LocalizedLabel> {
        private String language;
        private String text;

        public LocalizedLabelProxy() {
        }

        @Override
        public void initializeProxy(LocalizedLabel object) {
            language = object.language;
            text = object.text;
        }

        @Override
        public LocalizedLabel convertProxy() {
            return new LocalizedLabel(language, text);
        }
    }

    @Entity
    public static class LabelBatch {
        @PrimaryKey
        private Long id;

        private LocalizedLabel[] labels;

        public LabelBatch() {
        }

        LabelBatch(Long id, LocalizedLabel[] labels) {
            this.id = id;
            this.labels = labels;
        }
    }
}
