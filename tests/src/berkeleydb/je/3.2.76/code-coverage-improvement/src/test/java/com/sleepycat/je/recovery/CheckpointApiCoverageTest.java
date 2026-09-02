/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sleepycat.je.recovery;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.DatabaseImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckpointApiCoverageTest {

    @Test
    void forcedCheckpointFlushesADeepAndUpdatedTree(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        environmentConfig.setTransactional(true);
        environmentConfig.setConfigParam("je.log.fileMax", "1000000");
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            databaseConfig.setTransactional(true);
            database = environment.openDatabase(null, "deep-checkpoint", databaseConfig);
            for (int i = 0; i < 500; i++) {
                database.put(null, new DatabaseEntry(new byte[] {(byte) (i >>> 8), (byte) i}),
                        new DatabaseEntry(new byte[500]));
            }
            for (int i = 0; i < 500; i += 3) {
                database.put(null, new DatabaseEntry(new byte[] {(byte) (i >>> 8), (byte) i}),
                        new DatabaseEntry(new byte[700]));
            }
            com.sleepycat.je.CheckpointConfig checkpointConfig =
                    new com.sleepycat.je.CheckpointConfig();
            checkpointConfig.setForce(true);
            environment.checkpoint(checkpointConfig);
            com.sleepycat.je.PreloadConfig preloadConfig =
                    new com.sleepycat.je.PreloadConfig();
            preloadConfig.setLoadLNs(true);
            database.preload(preloadConfig);
            assertThat(database.count()).isEqualTo(500);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void checkpointReferenceDescribesItsDatabaseAndNode(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        Database database = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            database = environment.openDatabase(null, "checkpoint", databaseConfig);
            DatabaseImpl databaseImpl = DbInternal.dbGetDatabaseImpl(database);
            Checkpointer.CheckpointReference reference = new Checkpointer.CheckpointReference(
                    databaseImpl, 73L, false, true, new byte[] {1}, null);

            assertThat(reference.toString()).contains("db=" + databaseImpl.getId(), "nodeId=73");
            assertThat(reference).isEqualTo(new Checkpointer.CheckpointReference(
                    databaseImpl, 73L, true, false, null, new byte[] {2}));
            assertThat(reference.hashCode()).isEqualTo(73);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }
}
