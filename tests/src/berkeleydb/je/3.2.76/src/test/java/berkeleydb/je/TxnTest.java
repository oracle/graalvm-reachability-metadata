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
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.txn.Txn;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSource;

import static org.assertj.core.api.Assertions.assertThat;

public class TxnTest {

    private static final String TXN_CLASS_NAME = "com.sleepycat.je.txn.Txn";

    @Test
    void initializesTxnInIsolatedClassLoader() throws Exception {
        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(Txn.class)
        }, TxnTest.class.getClassLoader())) {
            Class<?> isolatedTxnClass = Class.forName(TXN_CLASS_NAME, true, classLoader);

            assertThat(isolatedTxnClass.getName()).isEqualTo(TXN_CLASS_NAME);
            if (isNativeImageRuntime()) {
                assertThat(isolatedTxnClass.getClassLoader())
                        .isIn(classLoader, ClassLoader.getSystemClassLoader());
            } else {
                assertThat(isolatedTxnClass.getClassLoader()).isSameAs(classLoader);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void directTxnConstructorCreatesUsableTransaction(@TempDir Path tmp) throws Exception {
        Environment environment = openTransactionalEnvironment(tmp);
        Txn txn = null;
        try {
            EnvironmentImpl environmentImpl = DbInternal.envGetEnvironmentImpl(environment);
            TransactionConfig config = new TransactionConfig();
            config.setReadCommitted(true);
            txn = new Txn(environmentImpl, config);

            assertThat(txn.isTransactional()).isTrue();
            assertThat(txn.isReadCommittedIsolation()).isTrue();
            assertThat(txn.isSerializableIsolation()).isFalse();
            assertThat(txn.getPrepared()).isFalse();
            assertThat(txn.getOnlyAbortable()).isFalse();
            assertThat(txn.collectStats(new LockStats()).getNTotalLocks()).isZero();

            txn.setSuspended(true);
            assertThat(txn.isSuspended()).isTrue();
            txn.setSuspended(false);
            assertThat(txn.isSuspended()).isFalse();

            txn.commit(Txn.TXN_NOSYNC);
            txn = null;
        } finally {
            if (txn != null) {
                txn.abort(false);
            }
            environment.close();
        }
    }

    @Test
    void beginTransactionCreatesTxnAndAppliesCommitAndAbort(@TempDir Path tmp) throws Exception {
        Environment environment = openTransactionalEnvironment(tmp);
        Database database = null;
        try {
            database = openTransactionalDatabase(environment, "txn-db");

            TransactionConfig config = new TransactionConfig();
            config.setReadCommitted(true);
            Transaction commitTransaction = environment.beginTransaction(null, config);
            commitTransaction.setName("commit-transaction");
            assertThat(commitTransaction.toString()).contains("commit-transaction");

            OperationStatus putStatus = database.put(commitTransaction, entry("key"), entry("committed"));
            assertThat(putStatus).isEqualTo(OperationStatus.SUCCESS);
            commitTransaction.commitNoSync();

            DatabaseEntry committedValue = new DatabaseEntry();
            assertThat(database.get(null, entry("key"), committedValue, LockMode.DEFAULT))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(value(committedValue)).isEqualTo("committed");

            Transaction abortTransaction = environment.beginTransaction(null, null);
            assertThat(database.put(abortTransaction, entry("key"), entry("aborted")))
                    .isEqualTo(OperationStatus.SUCCESS);
            abortTransaction.abort();

            DatabaseEntry afterAbortValue = new DatabaseEntry();
            assertThat(database.get(null, entry("key"), afterAbortValue, LockMode.DEFAULT))
                    .isEqualTo(OperationStatus.SUCCESS);
            assertThat(value(afterAbortValue)).isEqualTo("committed");
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    private static Environment openTransactionalEnvironment(Path directory) throws DatabaseException {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(directory.toFile(), config);
    }

    private static Database openTransactionalDatabase(Environment environment, String name) throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return environment.openDatabase(null, name, config);
    }

    private static DatabaseEntry entry(String value) {
        DatabaseEntry entry = new DatabaseEntry();
        StringBinding.stringToEntry(value, entry);
        return entry;
    }

    private static String value(DatabaseEntry entry) {
        return StringBinding.entryToString(entry);
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {

        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (name.startsWith("com.sleepycat.je.")) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
