/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.proxy.Proxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ProxyTest {
    @Test
    void distinguishesOrdinaryObjectsFromCglibProxies() {
        StringBuilder ordinary = new StringBuilder("value");

        assertThat(Proxy.isProxyClass(ordinary.getClass())).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> Proxy.getInvocationHandler(ordinary));
    }
}
