/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

import org.codehaus.commons.compiler.util.reflect.Proxies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxiesTest {
    @Test
    void createsInterfaceProxyThatDelegatesToConfiguredMethod() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Method runMethod = Runnable.class.getMethod("run");
        Method countDownMethod = CountDownLatch.class.getMethod("countDown");

        Runnable proxy = Proxies.newInstance(latch, runMethod, countDownMethod);
        proxy.run();

        assertThat(latch.getCount()).isZero();
    }
}
