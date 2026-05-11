/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sdk_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.interceptor.ClasspathInterceptorChainFactory;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.internal.interceptor.HttpChecksumValidationInterceptor;

public class ClasspathInterceptorChainFactoryTest {
    @Test
    void getInterceptorsLoadsExecutionInterceptorsDeclaredInClasspathResource() {
        ClasspathInterceptorChainFactory factory = new ClasspathInterceptorChainFactory();

        List<ExecutionInterceptor> interceptors = factory.getInterceptors("sdk-core-test/execution.interceptors");

        assertThat(interceptors)
                .hasSize(1)
                .first()
                .isInstanceOf(HttpChecksumValidationInterceptor.class);
    }
}
