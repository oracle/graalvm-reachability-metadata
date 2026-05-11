/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;

import jakarta.ws.rs.QueryParam;

import org.jboss.resteasy.spi.metadata.FieldParameter;
import org.jboss.resteasy.spi.metadata.Parameter;
import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.jboss.resteasy.spi.metadata.ResourceBuilder.ResourceClassBuilder;
import org.jboss.resteasy.spi.metadata.ResourceClass;
import org.junit.jupiter.api.Test;

public class ResourceBuilderAnonymous1Test {
    @Test
    @SuppressWarnings("removal")
    void discoversAnnotatedFieldsThroughPrivilegedActionWhenSecurityManagerIsAvailable() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final boolean securityManagerInstalled = installPermissiveSecurityManager(previousSecurityManager);

        try {
            if (Runtime.version().feature() < 24) {
                assertThat(System.getSecurityManager()).isNotNull();
            }

            final ResourceClass resourceClass = new FieldOnlyResourceBuilder()
                    .buildDeclaredFields(PrivilegedFieldResource.class);

            assertThat(resourceClass.getFields()).hasSize(1);
            final FieldParameter fieldParameter = resourceClass.getFields()[0];
            assertThat(fieldParameter.getParamType()).isEqualTo(Parameter.ParamType.QUERY_PARAM);
            assertThat(fieldParameter.getParamName()).isEqualTo("name");
            assertThat(fieldParameter.getType()).isEqualTo(String.class);
            assertThat(fieldParameter.getField().getName()).isEqualTo("name");
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

    private static final class FieldOnlyResourceBuilder extends ResourceBuilder {
        private ResourceClass buildDeclaredFields(final Class<?> resourceType) {
            final ResourceClassBuilder builder = new ResourceClassBuilder(resourceType, null);
            processDeclaredFields(builder, resourceType);
            return builder.buildClass();
        }
    }

    public static class PrivilegedFieldResource {
        @QueryParam("name")
        private String name;
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
