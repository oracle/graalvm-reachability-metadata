/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ScriptCommandTest {
    @Test
    void scriptsSchemaObjectsInDeterministicOrder() throws Exception {
        String url = "jdbc:h2:mem:script-command-" + UUID.randomUUID();

        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DOMAIN z_text AS VARCHAR(20)");
            statement.execute("CREATE DOMAIN a_text AS VARCHAR(20)");
            statement.execute("CREATE CONSTANT z_constant VALUE 2");
            statement.execute("CREATE CONSTANT a_constant VALUE 1");
            statement.execute("CREATE SEQUENCE z_sequence START WITH 2");
            statement.execute("CREATE SEQUENCE a_sequence START WITH 1");
            statement.execute("CREATE TABLE script_sample (id INTEGER PRIMARY KEY, label a_text DEFAULT a_constant)");

            List<String> scriptLines = readScriptLines(statement);

            assertThat(indexOfScriptLine(scriptLines, "CREATE DOMAIN", "A_TEXT"))
                    .isLessThan(indexOfScriptLine(scriptLines, "CREATE DOMAIN", "Z_TEXT"));
            assertThat(indexOfScriptLine(scriptLines, "CREATE CONSTANT", "A_CONSTANT"))
                    .isLessThan(indexOfScriptLine(scriptLines, "CREATE CONSTANT", "Z_CONSTANT"));
            assertThat(indexOfScriptLine(scriptLines, "CREATE SEQUENCE", "A_SEQUENCE"))
                    .isLessThan(indexOfScriptLine(scriptLines, "CREATE SEQUENCE", "Z_SEQUENCE"));
            assertThat(scriptLines).anySatisfy(line -> assertThat(line).contains("SCRIPT_SAMPLE"));
        }
    }

    private static List<String> readScriptLines(Statement statement) throws Exception {
        List<String> scriptLines = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery("SCRIPT NODATA NOPASSWORDS NOSETTINGS NOVERSION")) {
            while (resultSet.next()) {
                scriptLines.add(resultSet.getString(1));
            }
        }
        return scriptLines;
    }

    private static int indexOfScriptLine(List<String> scriptLines, String requiredPrefix, String requiredToken) {
        for (int i = 0; i < scriptLines.size(); i++) {
            String line = scriptLines.get(i);
            if (line.startsWith(requiredPrefix) && line.contains(requiredToken)) {
                return i;
            }
        }
        throw new AssertionError("Missing script line containing " + requiredToken + " in " + scriptLines);
    }
}
