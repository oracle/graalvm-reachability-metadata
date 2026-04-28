/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;

import org.apache.tomcat.websocket.PojoClassHolder;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoClassHolderTest {

    @Test
    public void getInstanceCreatesPojoEndpointWithPublicNoArgConstructor() throws Exception {
        ConstructedPojo.constructedCount = 0;
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        PojoClassHolder holder = new PojoClassHolder(ConstructedPojo.class, config);

        Endpoint endpoint = holder.getInstance(null);

        assertThat(endpoint).isInstanceOf(PojoEndpointClient.class);
        assertThat(holder.getClassName()).isEqualTo(ConstructedPojo.class.getName());
        assertThat(ConstructedPojo.constructedCount).isEqualTo(1);
    }

    public static class ConstructedPojo {
        private static int constructedCount;

        public ConstructedPojo() {
            constructedCount++;
        }
    }
}
