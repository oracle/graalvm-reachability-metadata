/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.FilePermission;
import java.security.PrivilegedAction;

import org.eclipse.jetty.util.security.SecurityUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityUtilsTest {
    @Test
    void performsSecurityOperationsWithoutAnInstalledSecurityManager() {
        assertThat(SecurityUtils.getSecurityManager()).isNull();

        SecurityUtils.checkPermission(new FilePermission("test-resource", "read"));
        String value = SecurityUtils.doPrivileged(new ConstantAction());

        assertThat(value).isEqualTo("privileged value");
    }

    private static final class ConstantAction implements PrivilegedAction<String> {
        @Override
        public String run() {
            return "privileged value";
        }
    }
}
