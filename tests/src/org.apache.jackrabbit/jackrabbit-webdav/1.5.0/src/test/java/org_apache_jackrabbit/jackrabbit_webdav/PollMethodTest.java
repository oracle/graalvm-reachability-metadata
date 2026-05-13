/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.PollMethod;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.junit.jupiter.api.Test;

public class PollMethodTest {
    @Test
    public void constructorCreatesPollRequestWithSubscriptionHeaders() {
        ExposedPollMethod method = new ExposedPollMethod(
                "http://localhost:8080/repository/default",
                "subscription-token",
                30000L);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_POLL);
        assertThat(method.getPath()).isEqualTo("/repository/default");
        assertThat(method.getRequestHeader(ObservationConstants.HEADER_SUBSCRIPTIONID).getValue())
                .isEqualTo("subscription-token");
        assertThat(method.getRequestHeader(ObservationConstants.HEADER_POLL_TIMEOUT).getValue())
                .isEqualTo("Second-30");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(207)).isFalse();
    }

    private static final class ExposedPollMethod extends PollMethod {
        private ExposedPollMethod(String uri, String subscriptionId, long timeout) {
            super(uri, subscriptionId, timeout);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
