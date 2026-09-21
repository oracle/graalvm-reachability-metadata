/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import org.apache.catalina.filters.CsrfPreventionFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CsrfPreventionFilterBaseTest {

    @Test
    void initializesConfiguredRandomSourceAndGeneratesNonce() throws Exception {
        TestCsrfFilter filter = new TestCsrfFilter();
        filter.setRandomClass(ZeroSecureRandom.class.getName());

        filter.init(filterConfig("csrf"));

        assertThat(filter.nonce()).isEqualTo("00000000000000000000000000000000");
    }

    private static FilterConfig filterConfig(String name) {
        return new FilterConfig() {
            @Override
            public String getFilterName() {
                return name;
            }

            @Override
            public ServletContext getServletContext() {
                return null;
            }

            @Override
            public String getInitParameter(String parameterName) {
                return null;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.emptyEnumeration();
            }
        };
    }

    public static final class TestCsrfFilter extends CsrfPreventionFilter {
        public TestCsrfFilter() {
        }

        public String nonce() {
            return generateNonce(null);
        }
    }

    public static final class ZeroSecureRandom extends SecureRandom {
        public ZeroSecureRandom() {
        }

        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = 0;
            }
        }
    }
}
