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
import com.sleepycat.persist.impl.Enhanced;
import com.sleepycat.persist.impl.EnhancedAccessor;
import com.sleepycat.persist.impl.EntityInput;
import com.sleepycat.persist.impl.EntityOutput;
import com.sleepycat.persist.impl.Format;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.Persistent;
import com.sleepycat.persist.model.PrimaryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises DPL bytecode-enhanced access paths represented by {@link Enhanced}.
 */
public class EnhancedAccessorTest {

    @Test
    void openingPrimaryIndexForEnhancedEntityResolvesPrimaryKeyField(@TempDir Path tmp) throws Exception {
        EnhancedAccessor.registerClass(EnhancedRecord.class.getName(), new EnhancedRecord());

        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);

            PrimaryIndex<Long, EnhancedRecord> byId = store.getPrimaryIndex(Long.class, EnhancedRecord.class);

            assertThat(byId).isNotNull();
        } finally {
            if (store != null) {
                store.close();
            }
            environment.close();
        }
    }

    @Test
    void readingAbstractEnhancedComponentArrayCreatesArrayReflectively(@TempDir Path tmp) throws Exception {
        EnhancedAccessor.registerClass(AbstractComponent.class.getName(), null);

        Environment environment = openEnvironment(tmp);
        EntityStore store = null;
        try {
            store = openStore(environment);
            PrimaryIndex<Long, ComponentContainer> byId = store.getPrimaryIndex(Long.class, ComponentContainer.class);
            byId.put(new ComponentContainer(1L, new AbstractComponent[0]));

            ComponentContainer restored = byId.get(1L);

            assertThat(restored).isNotNull();
            assertThat(restored.components).isNotNull();
            assertThat(restored.components).isEmpty();
            assertThat(restored.components.getClass().getComponentType()).isEqualTo(AbstractComponent.class);
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
        return new EntityStore(environment, "enhancedAccessorStore", config);
    }

    @Entity
    private static class EnhancedRecord implements Enhanced {
        @PrimaryKey
        private Long id;

        private String label;

        private EnhancedRecord() {
        }

        @Override
        public Object bdbNewInstance() {
            return new EnhancedRecord();
        }

        @Override
        public Object bdbNewArray(int len) {
            return new EnhancedRecord[len];
        }

        @Override
        public boolean bdbIsPriKeyFieldNullOrZero() {
            return id == null;
        }

        @Override
        public void bdbWritePriKeyField(EntityOutput output, Format format) {
            output.writeKeyObject(id, format);
        }

        @Override
        public void bdbReadPriKeyField(EntityInput input, Format format) {
            id = (Long) input.readKeyObject(format);
        }

        @Override
        public void bdbWriteSecKeyFields(EntityOutput output) {
            output.registerPriKeyObject(id);
        }

        @Override
        public void bdbReadSecKeyFields(EntityInput input, int startField, int endField, int superLevel) {
            input.registerPriKeyObject(id);
        }

        @Override
        public void bdbWriteNonKeyFields(EntityOutput output) {
            output.writeObject(label, null);
        }

        @Override
        public void bdbReadNonKeyFields(EntityInput input, int startField, int endField, int superLevel) {
            if (superLevel <= 0) {
                label = (String) input.readObject();
            }
        }

        @Override
        public Object bdbGetField(Object o, int field, int superLevel, boolean isSecField) {
            if (superLevel <= 0 && !isSecField && field == 0) {
                return label;
            }
            return null;
        }

        @Override
        public void bdbSetField(Object o, int field, int superLevel, boolean isSecField, Object value) {
            if (superLevel <= 0 && !isSecField && field == 0) {
                label = (String) value;
            }
        }
    }

    @Entity
    private static class ComponentContainer {
        @PrimaryKey
        private Long id;

        private AbstractComponent[] components;

        private ComponentContainer() {
        }

        private ComponentContainer(Long id, AbstractComponent[] components) {
            this.id = id;
            this.components = components;
        }
    }

    @Persistent
    private abstract static class AbstractComponent implements Enhanced {
    }
}
