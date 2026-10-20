/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.security.RolesAllowed;

import org.apache.cxf.interceptor.security.SecureAnnotationsInterceptor;
import org.junit.jupiter.api.Test;

public class SecureAnnotationsInterceptorTest {
    @Test
    void discoversClassAndMethodSecurityRoles() {
        InspectableInterceptor interceptor = new InspectableInterceptor();

        Map<String, String> roles = interceptor.rolesFor(SecuredService.class);

        assertThat(roles).containsEntry("read", "reader").containsEntry("write", "admin operator");
    }

    @RolesAllowed("reader")
    public static final class SecuredService {
        public String read() {
            return "read";
        }

        @RolesAllowed({"admin", "operator"})
        public String write() {
            return "write";
        }
    }

    private static final class InspectableInterceptor extends SecureAnnotationsInterceptor {
        Map<String, String> rolesFor(Class<?> serviceClass) {
            Map<String, String> roles = new HashMap<String, String>();
            findRoles(serviceClass, roles);
            return roles;
        }
    }
}
