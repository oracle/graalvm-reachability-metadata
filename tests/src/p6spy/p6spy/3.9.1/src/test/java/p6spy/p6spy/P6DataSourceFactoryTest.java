/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package p6spy.p6spy;

import static org.assertj.core.api.Assertions.assertThat;

import com.p6spy.engine.spy.P6DataSource;
import com.p6spy.engine.spy.P6DataSourceFactory;
import javax.naming.Reference;
import org.junit.jupiter.api.Test;

public class P6DataSourceFactoryTest {
    @Test
    void recreatesDataSourceFromJndiReference() throws Exception {
        P6DataSource original = new P6DataSource();
        original.setRealDataSource("jdbc/example");
        Reference reference = original.getReference();

        Object restored = new P6DataSourceFactory().getObjectInstance(reference, null, null, null);

        assertThat(restored).isInstanceOf(P6DataSource.class);
        assertThat(((P6DataSource) restored).getRealDataSource()).isEqualTo("jdbc/example");
    }
}
