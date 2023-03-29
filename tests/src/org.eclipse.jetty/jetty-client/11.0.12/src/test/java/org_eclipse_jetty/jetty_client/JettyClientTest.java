/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JettyClientTest {

    private static final boolean DISABLE_TEST = true;
    @Test
    void test() throws Exception {
        if (DISABLE_TEST) {
            return;
        }
        HttpClient client = new HttpClient();
        client.start();
        try {
            ContentResponse response = client.GET("https://httpbin.org/get");
            assertThat(response.getStatus()).isEqualTo(200);
        } finally {
            client.stop();
        }
    }
}
