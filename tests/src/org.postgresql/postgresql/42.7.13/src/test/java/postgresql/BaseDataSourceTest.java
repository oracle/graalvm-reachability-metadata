/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseDataSourceTest {

    @Test
    void initializeFromCopiesConnectionAndDriverPropertiesThroughBaseSerialization() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[] {"primary.example.test", "standby.example.test"});
        source.setPortNumbers(new int[] {5432, 15432});
        source.setDatabaseName("sampledb");
        source.setUser("sample-user");
        source.setPassword("sample-password");
        source.setApplicationName("metadata-coverage");
        source.setConnectTimeout(7);
        source.setSsl(true);

        PGSimpleDataSource target = new PGSimpleDataSource();
        target.initializeFrom(source);

        assertThat(target.getServerNames()).containsExactly("primary.example.test", "standby.example.test");
        assertThat(target.getPortNumbers()).containsExactly(5432, 15432);
        assertThat(target.getDatabaseName()).isEqualTo("sampledb");
        assertThat(target.getUser()).isEqualTo("sample-user");
        assertThat(target.getPassword()).isEqualTo("sample-password");
        assertThat(target.getApplicationName()).isEqualTo("metadata-coverage");
        assertThat(target.getConnectTimeout()).isEqualTo(7);
        assertThat(target.getSsl()).isTrue();
    }
}
