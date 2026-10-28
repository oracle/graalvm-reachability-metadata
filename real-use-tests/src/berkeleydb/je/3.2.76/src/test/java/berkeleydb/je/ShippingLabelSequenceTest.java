/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JE sequences to allocate persistent shipping-label numbers across packing stations.
@Timeout(60)
public class ShippingLabelSequenceTest {
    @TempDir
    Path home;

    @Test
    public void reservesNonOverlappingLabelBatchesAndContinuesAfterRestart() throws DatabaseException {
        // Independent station handles share one durable sequence, not an in-memory counter.
        try (LabelAllocator packingDesk = new LabelAllocator(home, true);
                LabelAllocator selfServiceStation = new LabelAllocator(home, false)) {
            assertEquals(List.of("SHIP-1000", "SHIP-1001", "SHIP-1002"), packingDesk.allocateLabels(3));
            assertEquals(List.of("SHIP-1003", "SHIP-1004"), selfServiceStation.allocateLabels(2));
            assertEquals(List.of("SHIP-1005"), packingDesk.allocateLabels(1));
        }

        // The configured initial value must not reset an already persisted sequence.
        try (LabelAllocator restartedDesk = new LabelAllocator(home, false)) {
            assertEquals(List.of("SHIP-1006", "SHIP-1007"), restartedDesk.allocateLabels(2));
            assertEquals(List.of("SHIP-1008"), restartedDesk.allocateLabels(1));
        }
    }

    private static final class LabelAllocator implements AutoCloseable {
        private final Environment environment;
        private final Database database;
        private final Sequence sequence;

        private LabelAllocator(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setTransactional(true);
            environmentConfig.setLockTimeout(10_000_000L);
            environmentConfig.setTxnTimeout(30_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            databaseConfig.setTransactional(true);
            try {
                database = environment.openDatabase(null, "label-counters", databaseConfig);
                try {
                    SequenceConfig sequenceConfig = new SequenceConfig();
                    sequenceConfig.setAllowCreate(create);
                    sequenceConfig.setInitialValue(1000L);
                    sequenceConfig.setRange(1L, 999_999_999L);
                    sequenceConfig.setWrap(false);
                    // Do not reserve unused cached numbers when a station shuts down.
                    sequenceConfig.setCacheSize(0);
                    DatabaseEntry key = new DatabaseEntry();
                    StringBinding.stringToEntry("shipping-label", key);
                    sequence = database.openSequence(null, key, sequenceConfig);
                } catch (DatabaseException | RuntimeException | Error failure) {
                    database.close();
                    throw failure;
                }
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private List<String> allocateLabels(int parcelCount) throws DatabaseException {
            if (parcelCount <= 0) {
                throw new IllegalArgumentException("A shipment must contain at least one parcel");
            }
            // A single sequence operation reserves the entire batch for this station.
            long firstNumber = sequence.get(null, parcelCount);
            List<String> labels = new ArrayList<>();
            for (int parcel = 0; parcel < parcelCount; parcel++) {
                labels.add("SHIP-" + (firstNumber + parcel));
            }
            return labels;
        }

        @Override
        public void close() throws DatabaseException {
            try {
                sequence.close();
            } finally {
                try {
                    database.close();
                } finally {
                    environment.close();
                }
            }
        }
    }
}
