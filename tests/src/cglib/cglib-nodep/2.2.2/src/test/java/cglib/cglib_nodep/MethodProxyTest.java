/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.MethodProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodProxyTest {
    @Test
    void findsMethodProxyFromEnhancedClass() {
        MethodProxy proxy = MethodProxy.create(
                MethodProxyTarget.class,
                MethodProxyEnhancedClass.class,
                "(Ljava/lang/String;)Ljava/lang/String;",
                "message",
                "CGLIB$message$0"
        );
        MethodProxyEnhancedClass.expectedSignature = proxy.getSignature();
        MethodProxyEnhancedClass.proxy = proxy;

        try {
            MethodProxy found = MethodProxy.find(MethodProxyEnhancedClass.class, proxy.getSignature());

            assertThat(found).isSameAs(proxy);
            assertThat(found.getSuperName()).isEqualTo("CGLIB$message$0");
        } finally {
            MethodProxyEnhancedClass.expectedSignature = null;
            MethodProxyEnhancedClass.proxy = null;
        }
    }

    public static class MethodProxyTarget {
        public String message(String name) {
            return "target " + name;
        }
    }

    public static class MethodProxyEnhancedClass extends MethodProxyTarget {
        private static Signature expectedSignature;
        private static MethodProxy proxy;

        public static MethodProxy CGLIB$findMethodProxy(Signature signature) {
            return signature.equals(expectedSignature) ? proxy : null;
        }

        public String CGLIB$message$0(String name) {
            return super.message(name);
        }
    }
}
