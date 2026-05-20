/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DigesterFactoryTest {

    @Test
    void createsDigesterWithBuiltInSchemaResources() {
        assertThat(DigesterFactory.newDigester(false, false, null, true)).isNotNull();
    }
}
