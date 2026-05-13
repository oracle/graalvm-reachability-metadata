/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.UnSubscribeMethod;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.junit.jupiter.api.Test;

public class UnSubscribeMethodTest {
    @Test
    public void constructorCreatesUnsubscribeRequestWithSubscriptionHeader() {
        ExposedUnSubscribeMethod method = new ExposedUnSubscribeMethod(
                "http://localhost:8080/repository/default",
                "subscription-token");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_UNSUBSCRIBE);
        assertThat(method.getPath()).isEqualTo("/repository/default");
        assertThat(method.getRequestHeader(ObservationConstants.HEADER_SUBSCRIPTIONID).getValue())
                .isEqualTo("subscription-token");
        assertThat(method.getRequestEntity()).isNull();
        assertThat(method.isSuccessfulStatus(204)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
    }

    private static final class ExposedUnSubscribeMethod extends UnSubscribeMethod {
        private ExposedUnSubscribeMethod(String uri, String subscriptionId) {
            super(uri, subscriptionId);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
