/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.DatabaseEventListener;
import org.h2.api.JavaObjectSerializer;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionRemoteTest {
    @Test
    void connectsToTcpServerWithDynamicListenerAndSerializerSettings() throws Exception {
        Server server = Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
        try {
            String url = "jdbc:h2:tcp://localhost:" + server.getPort()
                    + "/mem:sessionRemote;DB_CLOSE_DELAY=-1;AUTO_RECONNECT=TRUE;DATABASE_EVENT_LISTENER='"
                    + RecordingDatabaseEventListener.class.getName() + "';JAVA_OBJECT_SERIALIZER='"
                    + Utf8JavaObjectSerializer.class.getName() + "'";
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT CURRENT_TIMESTAMP")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(RecordingDatabaseEventListener.instances).isGreaterThan(0);
            }
        } finally {
            server.stop();
        }
    }

    public static final class RecordingDatabaseEventListener implements DatabaseEventListener {
        private static int instances;

        public RecordingDatabaseEventListener() {
            instances++;
        }
    }

    public static final class Utf8JavaObjectSerializer implements JavaObjectSerializer {
        public Utf8JavaObjectSerializer() {
        }

        @Override
        public byte[] serialize(Object obj) {
            return String.valueOf(obj).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object deserialize(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
