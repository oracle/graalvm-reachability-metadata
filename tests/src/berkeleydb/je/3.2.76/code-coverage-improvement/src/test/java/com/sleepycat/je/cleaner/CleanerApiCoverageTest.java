/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sleepycat.je.cleaner;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CleanerApiCoverageTest {

    @Test
    void checkpointStateSnapshotsCleanerCollections() {
        FileSelector.CheckpointStartCleanerState state =
                new FileSelector.CheckpointStartCleanerState(Set.of(7L), Set.of(9L),
                        Arrays.asList(11L, 11L));

        assertThat(state.isEmpty()).isFalse();
        assertThat(state.getCleanedFiles()).containsExactly(7L);
        assertThat(state.getFullyProcessedFiles()).containsExactly(9L);
        assertThat(state.getDeferredWriteDbs()).containsExactly(11L);
        assertThat(state.getDeferredWriteDbsSize()).isEqualTo(1);
    }

    @Test
    void maintenanceApisCleanEvictCompressAndCheckpointResidentData(@TempDir Path home)
            throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setConfigParam("je.log.fileMax", "1000000");
        config.setConfigParam("je.cleaner.minUtilization", "90");
        config.setConfigParam("je.cleaner.minFileUtilization", "50");
        config.setConfigParam("je.cleaner.minAge", "1");
        config.setConfigParam("je.cleaner.fetchObsoleteSize", "true");
        config.setConfigParam("je.cleaner.bytesInterval", "1000000");
        config.setConfigParam("je.checkpointer.bytesInterval", "1000000");
        config.setConfigParam("je.compressor.purgeRoot", "true");
        Environment environment = new Environment(home.toFile(), config);
        Database database = null;
        Database duplicates = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = environment.openDatabase(null, "maintenance", databaseConfig);
            for (int i = 0; i < 700; i++) {
                database.put(null, new DatabaseEntry(keyFor(i)),
                        new DatabaseEntry(new byte[2500]));
            }
            for (int i = 0; i < 700; i += 2) {
                database.delete(null, new DatabaseEntry(keyFor(i)));
            }

            DatabaseConfig duplicateConfig = new DatabaseConfig();
            duplicateConfig.setAllowCreate(true);
            duplicateConfig.setSortedDuplicates(true);
            duplicates = environment.openDatabase(null, "duplicate-maintenance", duplicateConfig);
            for (int i = 0; i < 40; i++) {
                duplicates.put(null, new DatabaseEntry(new byte[] {1}),
                        new DatabaseEntry(new byte[] {(byte) i}));
            }
            duplicates.delete(null, new DatabaseEntry(new byte[] {1}));

            environment.checkpoint(new com.sleepycat.je.CheckpointConfig());
            environment.cleanLog();
            environment.compress();
            environment.evictMemory();
            assertThat(database.count()).isEqualTo(350);
        } finally {
            if (duplicates != null) {
                duplicates.close();
            }
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static byte[] keyFor(int value) {
        return new byte[] {(byte) (value >>> 8), (byte) value};
    }

    @Test
    void verifyUtilsAcceptsAQuiescentDatabase(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = environment.openDatabase(null, "verify", databaseConfig);
            database.put(null, new DatabaseEntry(new byte[] {1}),
                    new DatabaseEntry(new byte[] {2}));

            new VerifyUtils();
            VerifyUtils.checkLsns(database);
            assertThat(database.count()).isEqualTo(1);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }
}
