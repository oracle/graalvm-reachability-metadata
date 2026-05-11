/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_locator;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.hk2.api.ProxyCtl;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.internal.ProxyUtilities;
import org.jvnet.hk2.internal.ServiceLocatorImpl;

public class ProxyUtilitiesAnonymous3Test {
    @Test
    void generateProxyForInterfaceUsesJdkProxyCreationPath() {
        final ServiceLocatorImpl locator = new ServiceLocatorImpl("proxy-utilities-anonymous-3-test", null);
        try {
            final MarkerContract proxy = new ProxyUtilities().generateProxy(
                    MarkerContract.class,
                    locator,
                    null,
                    null,
                    null);

            assertThat(proxy).isNotNull();
            assertThat(MarkerContract.class.isInstance(proxy)).isTrue();
            assertThat(ProxyCtl.class.isInstance(proxy)).isTrue();
            assertThat(proxy.getClass().getClassLoader()).isNotNull();
        } finally {
            locator.shutdown();
        }
    }

    public interface MarkerContract {
    }
}
