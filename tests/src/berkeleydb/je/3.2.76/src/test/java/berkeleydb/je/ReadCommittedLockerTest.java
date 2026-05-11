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
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadCommittedLockerTest {

    @Test
    void transactionalReadCommittedCursorReadsCommittedRecord(@TempDir Path tmp) throws Exception {
        Environment env = newEnvironment(tmp);
        Database db = null;
        Transaction txn = null;
        try {
            db = openTransactionalDatabase(env, "readCommitted");
            assertThat(db.put(null, entry("key"), entry("committed-value"))).isEqualTo(OperationStatus.SUCCESS);

            txn = env.beginTransaction(null, readCommittedConfig());

            DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = db.get(txn, entry("key"), data, LockMode.DEFAULT);

            assertThat(status).isEqualTo(OperationStatus.SUCCESS);
            assertThat(value(data)).isEqualTo("committed-value");

            txn.commit();
            txn = null;
        } finally {
            if (txn != null) {
                txn.abort();
            }
            if (db != null) {
                db.close();
            }
            env.close();
        }
    }

    private static Environment newEnvironment(Path dir) throws DatabaseException {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(dir.toFile(), config);
    }

    private static Database openTransactionalDatabase(Environment env, String name) throws DatabaseException {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return env.openDatabase(null, name, config);
    }

    private static TransactionConfig readCommittedConfig() {
        TransactionConfig config = new TransactionConfig();
        config.setReadCommitted(true);
        return config;
    }

    private static DatabaseEntry entry(String value) {
        DatabaseEntry entry = new DatabaseEntry();
        StringBinding.stringToEntry(value, entry);
        return entry;
    }

    private static String value(DatabaseEntry entry) {
        return StringBinding.entryToString(entry);
    }
}
