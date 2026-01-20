/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Berkeley DB Java Edition (berkeleydb:je:3.2.76).
 *
 * Covers:
 * - Environment/database creation (transactional)
 * - CRUD with commit and abort
 * - Cursor iteration and range search
 * - Sorted duplicates
 * - Secondary index and lookups producing multiple primary keys
 */
class JeTest {

    @Test
    void basicCrudWithTransactions(@TempDir Path tmp) throws Exception {
        Environment env = newEnvironment(tmp);
        Database db = null;
        try {
            db = openDatabase(env, "primary", true, false);

            // Create
            OperationStatus put1 = db.put(null, str("k1"), str("v1"));
            assertThat(put1).isEqualTo(OperationStatus.SUCCESS);

            // Read back
            DatabaseEntry out = new DatabaseEntry();
            OperationStatus get1 = db.get(null, str("k1"), out, LockMode.DEFAULT);
            assertThat(get1).isEqualTo(OperationStatus.SUCCESS);
            assertThat(toStr(out)).isEqualTo("v1");

            // Transactional update + commit
            Transaction t1 = env.beginTransaction(null, null);
            OperationStatus put2 = db.put(t1, str("k1"), str("v2"));
            assertThat(put2).isEqualTo(OperationStatus.SUCCESS);
            t1.commit();

            // Verify committed value
            DatabaseEntry out2 = new DatabaseEntry();
            OperationStatus get2 = db.get(null, str("k1"), out2, LockMode.DEFAULT);
            assertThat(get2).isEqualTo(OperationStatus.SUCCESS);
            assertThat(toStr(out2)).isEqualTo("v2");

            // Transactional delete + abort (rollback)
            Transaction t2 = env.beginTransaction(null, null);
            OperationStatus del1 = db.delete(t2, str("k1"));
            assertThat(del1).isEqualTo(OperationStatus.SUCCESS);
            t2.abort();

            // Record should still exist after abort
            DatabaseEntry out3 = new DatabaseEntry();
            OperationStatus get3 = db.get(null, str("k1"), out3, LockMode.DEFAULT);
            assertThat(get3).isEqualTo(OperationStatus.SUCCESS);
            assertThat(toStr(out3)).isEqualTo("v2");

            // Transactional delete + commit
            Transaction t3 = env.beginTransaction(null, null);
            OperationStatus del2 = db.delete(t3, str("k1"));
            assertThat(del2).isEqualTo(OperationStatus.SUCCESS);
            t3.commit();

            // Verify record is gone
            OperationStatus get4 = db.get(null, str("k1"), new DatabaseEntry(), LockMode.DEFAULT);
            assertThat(get4).isEqualTo(OperationStatus.NOTFOUND);
        } finally {
            if (db != null) {
                db.close();
            }
            // Close environment last
            env.close();
        }
    }

    @Test
    void cursorIterationAndRangeSearch(@TempDir Path tmp) throws Exception {
        Environment env = newEnvironment(tmp);
        Database db = null;
        try {
            db = openDatabase(env, "primary", true, false);

            // Insert keys so that lexical order is deterministic
            db.put(null, str("01"), str("a"));
            db.put(null, str("02"), str("b"));
            db.put(null, str("10"), str("c"));

            // Iterate with a forward cursor
            Cursor cursor = db.openCursor(null, null);
            try {
                DatabaseEntry k = new DatabaseEntry();
                DatabaseEntry v = new DatabaseEntry();
                List<String> seenKeys = new ArrayList<>();
                OperationStatus s = cursor.getFirst(k, v, LockMode.DEFAULT);
                while (s == OperationStatus.SUCCESS) {
                    seenKeys.add(toStr(k));
                    s = cursor.getNext(k, v, LockMode.DEFAULT);
                }
                assertThat(seenKeys).containsExactly("01", "02", "10");
            } finally {
                cursor.close();
            }

            // Range search: "05" should position on "10"
            Cursor cursor2 = db.openCursor(null, null);
            try {
                DatabaseEntry k = str("05");
                DatabaseEntry v = new DatabaseEntry();
                OperationStatus s = cursor2.getSearchKeyRange(k, v, LockMode.DEFAULT);
                assertThat(s).isEqualTo(OperationStatus.SUCCESS);
                assertThat(toStr(k)).isEqualTo("10");
            } finally {
                cursor2.close();
            }
        } finally {
            if (db != null) {
                db.close();
            }
            env.close();
        }
    }

    @Test
    void sortedDuplicates(@TempDir Path tmp) throws Exception {
        Environment env = newEnvironment(tmp);
        Database db = null;
        try {
            db = openDatabase(env, "dupDb", true, true);

            DatabaseEntry key = str("k");
            // Insert out of order to verify duplicate sorting and iteration
            db.put(null, key, str("c"));
            db.put(null, key, str("a"));
            db.put(null, key, str("b"));

            Cursor cursor = db.openCursor(null, null);
            try {
                DatabaseEntry k = str("k");
                DatabaseEntry d = new DatabaseEntry();
                List<String> values = new ArrayList<>();

                OperationStatus s = cursor.getSearchKey(k, d, LockMode.DEFAULT);
                assertThat(s).isEqualTo(OperationStatus.SUCCESS);
                values.add(toStr(d));

                while ((s = cursor.getNextDup(k, d, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
                    values.add(toStr(d));
                }

                // Duplicates are sorted by data bytes; expect a, b, c
                assertThat(values).containsExactly("a", "b", "c");
            } finally {
                cursor.close();
            }
        } finally {
            if (db != null) {
                db.close();
            }
            env.close();
        }
    }

    @Test
    void secondaryIndexLookup(@TempDir Path tmp) throws Exception {
        Environment env = newEnvironment(tmp);
        Database primary = null;
        SecondaryDatabase secondary = null;
        try {
            primary = openDatabase(env, "primary", true, false);

            SecondaryConfig sc = new SecondaryConfig();
            sc.setAllowCreate(true);
            sc.setTransactional(true);
            sc.setSortedDuplicates(true);
            sc.setKeyCreator(new FirstLetterKeyCreator());

            secondary = env.openSecondaryDatabase(null, "byFirstLetter", primary, sc);

            // Insert three records; two share the same secondary key 'a'
            primary.put(null, str("p1"), str("apple"));
            primary.put(null, str("p2"), str("apricot"));
            primary.put(null, str("p3"), str("banana"));

            // Lookup by secondary key 'a'
            SecondaryCursor scur = secondary.openSecondaryCursor(null, null);
            try {
                DatabaseEntry secKey = str("a");
                DatabaseEntry primaryKey = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();

                List<String> matchingPrimaryKeys = new ArrayList<>();

                OperationStatus s = scur.getSearchKey(secKey, primaryKey, data, LockMode.DEFAULT);
                if (s == OperationStatus.SUCCESS) {
                    matchingPrimaryKeys.add(toStr(primaryKey));
                    while ((s = scur.getNextDup(secKey, primaryKey, data, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
                        matchingPrimaryKeys.add(toStr(primaryKey));
                    }
                }

                assertThat(matchingPrimaryKeys).containsExactlyInAnyOrder("p1", "p2");
            } finally {
                scur.close();
            }

            // Lookup by secondary key 'b' -> should find only "p3"
            SecondaryCursor scur2 = secondary.openSecondaryCursor(null, null);
            try {
                DatabaseEntry secKey = str("b");
                DatabaseEntry primaryKey = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();

                List<String> matchingPrimaryKeys = new ArrayList<>();

                OperationStatus s = scur2.getSearchKey(secKey, primaryKey, data, LockMode.DEFAULT);
                if (s == OperationStatus.SUCCESS) {
                    matchingPrimaryKeys.add(toStr(primaryKey));
                    while ((s = scur2.getNextDup(secKey, primaryKey, data, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
                        matchingPrimaryKeys.add(toStr(primaryKey));
                    }
                }

                assertThat(matchingPrimaryKeys).containsExactly("p3");
            } finally {
                scur2.close();
            }
        } finally {
            if (secondary != null) {
                secondary.close();
            }
            if (primary != null) {
                primary.close();
            }
            env.close();
        }
    }

    // --- Helpers

    /**
     * Creates a transactional environment in the given directory.
     */
    private static Environment newEnvironment(Path dir) throws DatabaseException {
        EnvironmentConfig cfg = new EnvironmentConfig();
        cfg.setAllowCreate(true);
        cfg.setTransactional(true);
        return new Environment(dir.toFile(), cfg);
    }

    /**
     * Opens a database with the given configuration flags.
     */
    private static Database openDatabase(Environment env, String name, boolean transactional, boolean duplicates) throws DatabaseException {
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.setAllowCreate(true);
        cfg.setTransactional(transactional);
        cfg.setSortedDuplicates(duplicates);
        return env.openDatabase(null, name, cfg);
    }

    /**
     * Utility to convert a String to a DatabaseEntry using tuple bindings.
     */
    private static DatabaseEntry str(String s) {
        DatabaseEntry e = new DatabaseEntry();
        StringBinding.stringToEntry(s, e);
        return e;
    }

    /**
     * Utility to read a String from a DatabaseEntry using tuple bindings.
     */
    private static String toStr(DatabaseEntry e) {
        return StringBinding.entryToString(e);
    }

    /**
     * Secondary key creator that derives the key from the first letter of the data string.
     */
    private static final class FirstLetterKeyCreator implements SecondaryKeyCreator {
        @Override
        public boolean createSecondaryKey(SecondaryDatabase secondary,
                                          DatabaseEntry primaryKey,
                                          DatabaseEntry data,
                                          DatabaseEntry result) throws DatabaseException {
            String value = toStr(data);
            if (value == null || value.isEmpty()) {
                return false;
            }
            StringBinding.stringToEntry(value.substring(0, 1), result);
            return true;
        }
    }
}
