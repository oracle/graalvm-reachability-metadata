/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class UrlPathHelperTest {

    @Test
    void websphereRequestAttributeRemovesTrailingServletPathSlash() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("com.ibm.websphere.servlet.uri_non_decoded", "/app/orders/");
        request.setServletPath("/orders/");

        String servletPath = new UrlPathHelper().getServletPath(request);

        assertThat(servletPath).isEqualTo("/orders");
    }
}
