/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.io.Serializable;

import org.jboss.interceptor.builder.InterceptionModelBuilder;
import org.jboss.interceptor.proxy.DefaultInvocationContextFactory;
import org.jboss.interceptor.proxy.DirectClassInterceptorInstantiator;
import org.jboss.interceptor.proxy.InterceptorProxyCreatorImpl;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionFactoryUtilsTest {
    @Test
    void createsAnInstanceWithoutCallingItsParameterizedConstructor() {
        ClassMetadata<?> metadata = ReflectiveClassMetadata.of(ConstructorOnlyService.class);
        InterceptorProxyCreatorImpl creator = new InterceptorProxyCreatorImpl(
                new DirectClassInterceptorInstantiator(), new DefaultInvocationContextFactory(),
                InterceptionModelBuilder.<ClassMetadata<?>>newBuilderFor(metadata).build());
        ConstructorOnlyService instance = creator.createProxyInstance(ConstructorOnlyService.class, null);

        assertThat(instance.message()).isEqualTo("not initialized");
        instance.initialize("ready");
        assertThat(instance.message()).isEqualTo("ready");
    }

    public static class ConstructorOnlyService implements Serializable {
        private String message;

        public ConstructorOnlyService(String message) {
            this.message = message;
        }

        public void initialize(String value) {
            message = value;
        }

        public String message() {
            return message == null ? "not initialized" : message;
        }
    }
}
