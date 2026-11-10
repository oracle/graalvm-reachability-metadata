/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.ha.ReplicationConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class ReplicationConnectionProxyTest {
    @Test
    void readOnlyModeSelectsReplicaAndReturnsToSource() throws Exception {
        try (ReplicationConnection connection = (ReplicationConnection) MysqlTestSupport.connect("replication:", Map.of())) {
            assertThat(connection.isHostSource(MysqlTestSupport.host())).isTrue();
            assertThat(connection.isHostReplica(MysqlTestSupport.replicaHost())).isTrue();
            connection.setReadOnly(true);
            assertThat(connection.isReadOnly()).isTrue();
            MysqlTestSupport.assertQuery(connection, 91);
            connection.setReadOnly(false);
            assertThat(connection.isReadOnly()).isFalse();
            MysqlTestSupport.assertQuery(connection, 92);
        }
    }
}
