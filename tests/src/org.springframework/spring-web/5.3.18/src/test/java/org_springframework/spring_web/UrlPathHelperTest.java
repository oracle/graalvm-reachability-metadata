/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import com.ibm.ws.webcontainer.WebContainer;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class UrlPathHelperTest {
    @Test
    void removesTrailingServletPathSlashWhenWebSphereNonCompliantModeIsDetected() {
        WebContainer.reset();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/app/");
        request.setAttribute("com.ibm.websphere.servlet.uri_non_decoded", "/app/");

        String servletPath = new UrlPathHelper().getServletPath(request);

        assertThat(servletPath).isEqualTo("/app");
        assertThat(WebContainer.getPropertiesInvocationCount()).isEqualTo(1);
    }
}
