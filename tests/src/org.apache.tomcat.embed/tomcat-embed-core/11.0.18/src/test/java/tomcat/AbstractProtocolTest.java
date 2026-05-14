/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractProtocolTest {

    @Test
    void connectorReadsGenericProtocolPropertiesThroughPublicApi() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setPort(0);

        Object name = connector.getProperty("name");
        Object absentLowLevelProperty = connector.getProperty("forgeMissingLowLevelProperty");

        assertThat(name).isInstanceOf(String.class);
        assertThat((String) name).contains("http-nio");
        assertThat(absentLowLevelProperty).isNull();
    }
}
