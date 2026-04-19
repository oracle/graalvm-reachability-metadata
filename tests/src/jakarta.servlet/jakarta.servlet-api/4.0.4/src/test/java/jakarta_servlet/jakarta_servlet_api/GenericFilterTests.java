/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet.jakarta_servlet_api;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.GenericFilter;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.Test;

class GenericFilterTests {
    @Test
    void getFilterNameBeforeInitializationUsesLocalizedBundleMessage() {
        CustomGenericFilter filter = new CustomGenericFilter();

        assertThatIllegalStateException()
                .isThrownBy(filter::getFilterName)
                .withMessage("ServletConfig has not been initialized");
    }

    static final class CustomGenericFilter extends GenericFilter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
        }
    }
}
