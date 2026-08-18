/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_waffle.waffle_jna;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.junit.jupiter.api.Test;

import waffle.servlet.NegotiateSecurityFilter;
import waffle.windows.auth.PrincipalFormat;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

public class NegotiateSecurityFilterTest {
    @Test
    void initializesConfiguredAuthProvider() throws ServletException {
        final NegotiateSecurityFilter filter = new NegotiateSecurityFilter();
        final Map<String, String> parameters = Map.of(
                "authProvider", WindowsAuthProviderImpl.class.getName(),
                "principalFormat", "sid",
                "roleFormat", "both");

        filter.init(new MapBackedFilterConfig(parameters));

        assertThat(filter.getPrincipalFormat()).isEqualTo(PrincipalFormat.SID);
        assertThat(filter.getRoleFormat()).isEqualTo(PrincipalFormat.BOTH);
    }

    private static final class MapBackedFilterConfig implements FilterConfig {
        private final Map<String, String> parameters;

        private MapBackedFilterConfig(final Map<String, String> parameters) {
            this.parameters = parameters;
        }

        @Override
        public String getFilterName() {
            return "negotiateSecurityFilter";
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(final String name) {
            return this.parameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(this.parameters.keySet());
        }
    }
}
