/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.util.ProxyUtils;
import org.junit.jupiter.api.Test;

public class ProxyUtilsTest {
    @Test
    void createsAProxyThatDispatchesToItsHandler() {
        Greeting greeting = ProxyUtils.newProxyInstance(
                Greeting.class, (proxy, method, arguments) -> "hello " + arguments[0]);

        assertThat(greeting.say("sshd")).isEqualTo("hello sshd");
    }

    public interface Greeting {
        String say(String name);
    }
}
