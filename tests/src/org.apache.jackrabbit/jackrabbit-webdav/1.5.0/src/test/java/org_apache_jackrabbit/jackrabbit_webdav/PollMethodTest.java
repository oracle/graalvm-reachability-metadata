/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.commons.httpclient.Header;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.PollMethod;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PollMethodTest {
    @Test
    void createsPollRequestWithSubscriptionAndTimeoutHeaders() {
        PollMethod method = new PollMethod("/repository/default", "subscription-123", 15_000L);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_POLL);
        assertThat(method.getPath()).isEqualTo("/repository/default");

        Header subscriptionHeader = method.getRequestHeader(ObservationConstants.HEADER_SUBSCRIPTIONID);
        assertThat(subscriptionHeader).isNotNull();
        assertThat(subscriptionHeader.getValue()).isEqualTo("subscription-123");

        Header timeoutHeader = method.getRequestHeader(ObservationConstants.HEADER_POLL_TIMEOUT);
        assertThat(timeoutHeader).isNotNull();
        assertThat(timeoutHeader.getValue()).isEqualTo("Second-15");
    }
}
