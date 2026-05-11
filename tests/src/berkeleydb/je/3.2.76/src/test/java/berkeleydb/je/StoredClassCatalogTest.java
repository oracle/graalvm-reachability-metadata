/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises persisted class descriptor storage and reload paths in {@link StoredClassCatalog}.
 */
public class StoredClassCatalogTest {

    @Test
    void storesAndReloadsClassDescriptorFormats(@TempDir Path temporaryDirectory) throws Exception {
        Environment environment = newEnvironment(temporaryDirectory);
        try {
            ObjectStreamClass payloadFormat = ObjectStreamClass.lookup(CatalogPayload.class);
            byte[] classId = storeClassDescriptor(environment, payloadFormat);

            StoredClassCatalog reloadedCatalog = new StoredClassCatalog(openCatalogDatabase(environment));
            try {
                ObjectStreamClass storedFormat = reloadedCatalog.getClassFormat(classId);
                assertThat(storedFormat.getName()).isEqualTo(CatalogPayload.class.getName());

                byte[] reloadedClassId = reloadedCatalog.getClassID(payloadFormat);
                assertThat(reloadedClassId).containsExactly(classId);
            } finally {
                reloadedCatalog.close();
            }
        } finally {
            environment.close();
        }
    }

    private static byte[] storeClassDescriptor(Environment environment,
                                               ObjectStreamClass payloadFormat) throws Exception {
        StoredClassCatalog catalog = new StoredClassCatalog(openCatalogDatabase(environment));
        try {
            byte[] classId = catalog.getClassID(payloadFormat);
            assertThat(classId).isNotEmpty();
            return classId;
        } finally {
            catalog.close();
        }
    }

    private static Environment newEnvironment(Path directory) throws DatabaseException {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        return new Environment(directory.toFile(), config);
    }

    private static Database openCatalogDatabase(Environment environment) throws DatabaseException {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        return environment.openDatabase(null, "classCatalog", config);
    }

    private static final class CatalogPayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        private CatalogPayload(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
