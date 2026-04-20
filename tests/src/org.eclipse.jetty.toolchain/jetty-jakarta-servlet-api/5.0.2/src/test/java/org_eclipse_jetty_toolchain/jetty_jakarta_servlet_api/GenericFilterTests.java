/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_toolchain.jetty_jakarta_servlet_api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GenericFilterTests {
    @Test
    void getFilterNameBeforeInitializationUsesBundleBackedMessage() {
        TestGenericFilter filter = new TestGenericFilter();

        IllegalStateException exception = assertThrows(IllegalStateException.class, filter::getFilterName);

        assertThat(exception).hasMessage("ServletConfig has not been initialized");
    }

    private static final class TestGenericFilter extends GenericFilter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
        }
    }
}
