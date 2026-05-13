/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.SubscribeMethod;
import org.apache.jackrabbit.webdav.observation.DefaultEventType;
import org.apache.jackrabbit.webdav.observation.EventType;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.observation.SubscriptionInfo;
import org.junit.jupiter.api.Test;

public class SubscribeMethodTest {
    @Test
    public void constructorCreatesSubscribeRequestWithHeadersAndBody() throws Exception {
        EventType eventType = DefaultEventType.create("nodeadded", ObservationConstants.NAMESPACE);
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(new EventType[] {eventType}, true, 45000L);

        ExposedSubscribeMethod method = new ExposedSubscribeMethod(
                "http://localhost:8080/repository/default",
                subscriptionInfo,
                "subscription-token");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_SUBSCRIBE);
        assertThat(method.getPath()).isEqualTo("/repository/default");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();
        assertThat(method.getRequestHeader(ObservationConstants.HEADER_SUBSCRIPTIONID).getValue())
                .isEqualTo("<subscription-token>");
        assertThat(method.getRequestHeader(DavConstants.HEADER_TIMEOUT).getValue()).isEqualTo("Second-45");
        assertThat(method.getRequestHeader(DavConstants.HEADER_DEPTH).getValue())
                .isEqualTo(DavConstants.DEPTH_INFINITY_S);

        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        requestEntity.writeRequest(body);
        String xml = body.toString(StandardCharsets.UTF_8.name());

        assertThat(xml).contains("subscriptioninfo");
        assertThat(xml).contains("eventtype");
        assertThat(xml).contains("nodeadded");
    }

    private static final class ExposedSubscribeMethod extends SubscribeMethod {
        private ExposedSubscribeMethod(String uri, SubscriptionInfo subscriptionInfo,
                String subscriptionId) throws Exception {
            super(uri, subscriptionInfo, subscriptionId);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
