/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.security.CGLIBProxyTypePermission;
import com.thoughtworks.xstream.security.TypePermission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CGLIBProxyTypePermissionTest {
    @Test
    void comparesByConcretePermissionClass() {
        TypePermission permission = CGLIBProxyTypePermission.PROXIES;

        assertThat(permission.equals(new CGLIBProxyTypePermission())).isTrue();
        assertThat(permission.equals(new Object())).isFalse();
        assertThat(permission.equals(null)).isFalse();
        assertThat(permission.hashCode()).isEqualTo(new CGLIBProxyTypePermission().hashCode());
    }
}
