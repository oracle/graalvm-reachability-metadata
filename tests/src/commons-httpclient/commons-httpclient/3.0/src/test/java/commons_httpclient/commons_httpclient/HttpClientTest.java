/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class HttpClientTest {
    private static final String HTTP_CLIENT_CLASS_NAME = "org.apache.commons.httpclient.HttpClient";

    @Test
    @Order(1)
    void classForNameInitializesHttpClient() throws Exception {
        Class<?> clientClass = Class.forName(HTTP_CLIENT_CLASS_NAME);

        assertThat(clientClass.getName()).isEqualTo(HTTP_CLIENT_CLASS_NAME);
    }

    @Test
    @Order(2)
    void constructorInstantiatesConfiguredConnectionManagerClass() {
        HttpClientParams params = new HttpClientParams();
        params.setConnectionManagerClass(SimpleHttpConnectionManager.class);

        HttpClient client = new HttpClient(params);

        assertThat(client.getParams()).isSameAs(params);
        assertThat(client.getHttpConnectionManager()).isInstanceOf(SimpleHttpConnectionManager.class);
    }
}
