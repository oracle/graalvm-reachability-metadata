/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.GenericFilter;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.Test;

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
