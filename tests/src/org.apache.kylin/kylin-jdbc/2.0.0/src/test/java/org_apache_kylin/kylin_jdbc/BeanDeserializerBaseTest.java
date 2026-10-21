/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDeserializerBaseTest {
    @Test
    void resolvesTheConstructorForANonStaticInnerBeanProperty() throws Exception {
        Dashboard dashboard = new ObjectMapper()
                .readValue("{\"tile\":{\"caption\":\"Quarterly sales\"}}", Dashboard.class);

        assertThat(dashboard.tile.caption).isEqualTo("Quarterly sales");
    }

    public static class Dashboard {
        public Tile tile;

        public class Tile {
            public String caption;
        }
    }
}
