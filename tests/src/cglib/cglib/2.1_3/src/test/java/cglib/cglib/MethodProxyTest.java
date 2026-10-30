/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.MethodProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class MethodProxyTest {
    @Test
    void rejectsLookupOnClassWithoutMethodInterceptor() {
        Signature signature = new Signature("toString", "()Ljava/lang/String;");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MethodProxy.find(StringBuilder.class, signature))
                .withMessageContaining("does not use a MethodInterceptor");
    }
}
