/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata_tests.org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractProtocolTest {

    @Test
    void connectorReadsAndUpdatesBindOnInitThroughPublicApi() {
        Connector connector = new Connector();

        Object bindOnInitBeforeUpdate = connector.getProperty("bindOnInit");
        boolean bindOnInitUpdated = connector.setProperty("bindOnInit", "false");
        Object bindOnInitAfterUpdate = connector.getProperty("bindOnInit");

        assertThat(bindOnInitBeforeUpdate).isNull();
        assertThat(bindOnInitUpdated).isTrue();
        assertThat(bindOnInitAfterUpdate).isEqualTo("false");
    }
}
