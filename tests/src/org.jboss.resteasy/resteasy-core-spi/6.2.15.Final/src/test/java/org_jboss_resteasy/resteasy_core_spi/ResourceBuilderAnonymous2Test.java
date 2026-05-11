/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;
import java.util.HashSet;

import jakarta.ws.rs.HeaderParam;

import org.jboss.resteasy.spi.metadata.Parameter;
import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.jboss.resteasy.spi.metadata.ResourceBuilder.ResourceClassBuilder;
import org.jboss.resteasy.spi.metadata.ResourceClass;
import org.jboss.resteasy.spi.metadata.SetterParameter;
import org.junit.jupiter.api.Test;

public class ResourceBuilderAnonymous2Test {
    @Test
    @SuppressWarnings("removal")
    void discoversAnnotatedSettersThroughPrivilegedActionWhenSecurityManagerIsAvailable() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final boolean securityManagerInstalled = installPermissiveSecurityManager(previousSecurityManager);

        try {
            if (Runtime.version().feature() < 24) {
                assertThat(System.getSecurityManager()).isNotNull();
            }

            final ResourceClass resourceClass = new SetterOnlyResourceBuilder()
                    .buildDeclaredSetters(PrivilegedSetterResource.class);

            assertThat(resourceClass.getSetters()).hasSize(1);
            final SetterParameter setterParameter = resourceClass.getSetters()[0];
            assertThat(setterParameter.getParamType()).isEqualTo(Parameter.ParamType.HEADER_PARAM);
            assertThat(setterParameter.getParamName()).isEqualTo("X-Request-Id");
            assertThat(setterParameter.getType()).isEqualTo(String.class);
            assertThat(setterParameter.getSetter().getName()).isEqualTo("setRequestId");
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @SuppressWarnings("removal")
    private static boolean installPermissiveSecurityManager(final SecurityManager previousSecurityManager) {
        if (previousSecurityManager != null) {
            return false;
        }
        try {
            System.setSecurityManager(new PermissiveSecurityManager());
            return true;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    private static final class SetterOnlyResourceBuilder extends ResourceBuilder {
        private ResourceClass buildDeclaredSetters(final Class<?> resourceType) {
            final ResourceClassBuilder builder = new ResourceClassBuilder(resourceType, null);
            processDeclaredSetters(builder, resourceType, new HashSet<>());
            return builder.buildClass();
        }
    }

    public static class PrivilegedSetterResource {
        private String requestId;

        public String getRequestId() {
            return requestId;
        }

        @HeaderParam("X-Request-Id")
        public void setRequestId(final String requestId) {
            this.requestId = requestId;
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(final Permission permission) {
        }

        @Override
        public void checkPermission(final Permission permission, final Object context) {
        }
    }
}
