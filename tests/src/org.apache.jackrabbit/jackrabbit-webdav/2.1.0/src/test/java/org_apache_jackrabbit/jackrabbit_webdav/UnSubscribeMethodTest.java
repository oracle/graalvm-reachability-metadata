/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.commons.httpclient.Header;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.UnSubscribeMethod;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnSubscribeMethodTest {
    @Test
    void createsUnsubscribeRequestWithSubscriptionHeader() {
        UnSubscribeMethod method = new UnSubscribeMethod("/repository/default", "subscription-123");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_UNSUBSCRIBE);
        assertThat(method.getPath()).isEqualTo("/repository/default");

        Header subscriptionHeader = method.getRequestHeader(ObservationConstants.HEADER_SUBSCRIPTIONID);
        assertThat(subscriptionHeader).isNotNull();
        assertThat(subscriptionHeader.getValue()).isEqualTo("subscription-123");
    }
}
