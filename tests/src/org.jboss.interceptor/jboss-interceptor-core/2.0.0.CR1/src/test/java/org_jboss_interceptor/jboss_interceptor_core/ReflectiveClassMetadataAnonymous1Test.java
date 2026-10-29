/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import org.jboss.interceptor.reader.InterceptorMetadataUtils;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorMetadata;
import org.jboss.interceptor.spi.metadata.MethodMetadata;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.LifecycleService;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveClassMetadataAnonymous1Test {
    @Test
    void discoversTheAnnotatedLifecycleMethodAmongOrdinaryMethods() {
        ClassMetadata<?> metadata = ReflectiveClassMetadata.of(LifecycleService.class);

        assertThat(metadata.getDeclaredMethods()).extracting(method -> method.getJavaMethod().getName())
                .contains("passivate", "passivations");
        InterceptorMetadata<?> interceptors = InterceptorMetadataUtils.readMetadataForTargetClass(metadata);
        assertThat(interceptors.isEligible(InterceptionType.PRE_PASSIVATE)).isTrue();
        assertThat(interceptors.isEligible(InterceptionType.AROUND_INVOKE)).isFalse();
        assertThat(interceptors.getInterceptorMethods(InterceptionType.PRE_PASSIVATE))
                .extracting(MethodMetadata::getJavaMethod).extracting(method -> method.getName())
                .containsExactly("passivate");
    }
}
