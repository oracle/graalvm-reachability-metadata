/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.security.annotation.RunAs;
import jakarta.inject.Singleton;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for the {@link RunAs} AOP binding and role alias. */
@Timeout(55)
public class RunAsInterceptionTest {
    @Test
    void runAsTriggersInterceptionAndExposesValueAsRoles() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ImpersonatingService service = context.getBean(ImpersonatingService.class);

            assertThat(service.perform()).isEqualTo("batch-user:[ROLE_BATCH]:business-result");
        }
    }

    @Singleton
    public static class ImpersonatingService {
        @RunAs(value = "ROLE_BATCH", name = "batch-user", appendRoles = false)
        public String perform() {
            return "business-result";
        }
    }

    @Singleton
    @InterceptorBean(RunAs.class)
    public static class CapturingRunAsInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> invocation) {
            AnnotationMetadata metadata = invocation.getAnnotationMetadata();
            String name = metadata.stringValue(RunAs.class, "name").orElseThrow();
            String[] roles = metadata.stringValues(RunAs.class, "roles");

            return name + ":" + Arrays.toString(roles) + ":" + invocation.proceed();
        }
    }
}
