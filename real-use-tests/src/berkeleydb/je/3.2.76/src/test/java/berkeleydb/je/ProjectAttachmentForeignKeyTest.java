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
import com.sleepycat.je.ForeignKeyDeleteAction;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises JE foreign-key constraints to reject orphan attachments and cascade project deletion.
@Timeout(60)
public class ProjectAttachmentForeignKeyTest {
    @TempDir
    Path home;

    @Test
    public void enforcesProjectOwnershipAndDeletesOnlyAttachmentsStillOwnedByDeletedProject()
            throws DatabaseException {
        try (ProjectFiles files = new ProjectFiles(home, true)) {
            files.createProject("alpha", "Website redesign");
            files.createProject("beta", "Customer portal");
            files.attach("wireframe.svg", "alpha");
            files.attach("requirements.txt", "alpha");
            files.attach("portal.png", "beta");

            assertThrows(DatabaseException.class, () -> files.attach("orphan.txt", "missing"));
            assertNull(files.ownerOf("orphan.txt"));
            assertEquals("alpha", files.ownerOf("wireframe.svg"));
            assertEquals("beta", files.ownerOf("portal.png"));
        }

        // Reopening must reestablish the association before the application accepts writes.
        try (ProjectFiles files = new ProjectFiles(home, false)) {
            assertEquals("alpha", files.ownerOf("requirements.txt"));
            files.attach("requirements.txt", "beta");
            files.deleteProject("alpha");

            assertNull(files.projectTitle("alpha"));
            assertNull(files.ownerOf("wireframe.svg"));
            assertEquals("beta", files.ownerOf("requirements.txt"));
            assertEquals("beta", files.ownerOf("portal.png"));
            assertEquals("Customer portal", files.projectTitle("beta"));
            assertThrows(DatabaseException.class, () -> files.attach("late-upload.txt", "alpha"));
            assertNull(files.ownerOf("late-upload.txt"));
        }

        try (ProjectFiles files = new ProjectFiles(home, false)) {
            assertNull(files.projectTitle("alpha"));
            assertNull(files.ownerOf("wireframe.svg"));
            assertEquals("beta", files.ownerOf("requirements.txt"));
            assertEquals("beta", files.ownerOf("portal.png"));
            files.deleteProject("beta");
            assertNull(files.ownerOf("requirements.txt"));
            assertNull(files.ownerOf("portal.png"));
        }
    }

    private static final class ProjectFiles implements AutoCloseable {
        private final Environment environment;
        private final Database projects;
        private final Database attachments;
        private final SecondaryDatabase attachmentsByProject;

        private ProjectFiles(Path home, boolean create) throws DatabaseException {
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
                projects = environment.openDatabase(null, "projects", databaseConfig);
                try {
                    attachments = environment.openDatabase(null, "attachments", databaseConfig);
                    try {
                        SecondaryConfig ownershipConfig = new SecondaryConfig();
                        ownershipConfig.setAllowCreate(create);
                        ownershipConfig.setTransactional(true);
                        ownershipConfig.setSortedDuplicates(true);
                        ownershipConfig.setKeyCreator((secondary, key, data, projectKey) -> {
                            StringBinding.stringToEntry(StringBinding.entryToString(data), projectKey);
                            return true;
                        });
                        ownershipConfig.setForeignKeyDatabase(projects);
                        ownershipConfig.setForeignKeyDeleteAction(ForeignKeyDeleteAction.CASCADE);
                        attachmentsByProject = environment.openSecondaryDatabase(
                                null, "attachments-by-project", attachments, ownershipConfig);
                    } catch (DatabaseException | RuntimeException | Error failure) {
                        attachments.close();
                        throw failure;
                    }
                } catch (DatabaseException | RuntimeException | Error failure) {
                    projects.close();
                    throw failure;
                }
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void createProject(String project, String title) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, projects.putNoOverwrite(null, entry(project), entry(title)));
        }

        private void attach(String filename, String project) throws DatabaseException {
            Transaction transaction = environment.beginTransaction(null, null);
            try {
                assertEquals(OperationStatus.SUCCESS, attachments.put(transaction, entry(filename), entry(project)));
                transaction.commitSync();
                transaction = null;
            } finally {
                if (transaction != null) {
                    transaction.abort();
                }
            }
        }

        private void deleteProject(String project) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, projects.delete(null, entry(project)));
        }

        private String ownerOf(String filename) throws DatabaseException {
            return read(attachments, filename);
        }

        private String projectTitle(String project) throws DatabaseException {
            return read(projects, project);
        }

        private static String read(Database database, String key) throws DatabaseException {
            DatabaseEntry value = new DatabaseEntry();
            OperationStatus status = database.get(null, entry(key), value, LockMode.DEFAULT);
            if (status == OperationStatus.NOTFOUND) {
                return null;
            }
            assertEquals(OperationStatus.SUCCESS, status);
            return StringBinding.entryToString(value);
        }

        private static DatabaseEntry entry(String text) {
            DatabaseEntry entry = new DatabaseEntry();
            StringBinding.stringToEntry(text, entry);
            return entry;
        }

        @Override
        public void close() throws DatabaseException {
            try {
                attachmentsByProject.close();
            } finally {
                try {
                    attachments.close();
                } finally {
                    try {
                        projects.close();
                    } finally {
                        environment.close();
                    }
                }
            }
        }
    }
}
