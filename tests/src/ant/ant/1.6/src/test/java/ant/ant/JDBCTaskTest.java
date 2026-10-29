/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.apache.tools.ant.taskdefs.SQLExec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JDBCTaskTest {
    @TempDir
    File directory;

    @Test
    void loadsJdbcDriverAndExecutesSql() throws Exception {
        File output = new File(directory, "rows.txt");
        SQLExec sql = new SQLExec();
        sql.setProject(TestSupport.project(directory));
        sql.setDriver("org.h2.Driver");
        sql.setUrl("jdbc:h2:mem:ant_sql");
        sql.setUserid("sa");
        sql.setPassword("");
        sql.setPrint(true);
        sql.setOutput(output);
        sql.addText("""
                CREATE TABLE messages (body VARCHAR(100));
                INSERT INTO messages VALUES ('Ant JDBC result');
                SELECT body FROM messages;
                """);
        sql.execute();
        assertTrue(Files.readString(output.toPath()).contains("Ant JDBC result"));
    }
}
