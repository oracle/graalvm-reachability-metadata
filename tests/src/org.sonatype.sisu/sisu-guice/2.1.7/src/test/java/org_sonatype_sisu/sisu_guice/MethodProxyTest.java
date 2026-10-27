/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.cglib.core.Signature;
import com.google.inject.internal.cglib.proxy.MethodProxy;
import org.junit.jupiter.api.Test;

public class MethodProxyTest {
    @Test
    void findsMethodProxyUsingEnhancedClassFinderMethod() {
        MethodProxy proxy = MethodProxy.create(
                MethodProxyTarget.class,
                MethodProxyEnhancedClassShape.class,
                "(Ljava/lang/String;)Ljava/lang/String;",
                "message",
                "CGLIB$message$0");
        MethodProxyEnhancedClassShape.expectedSignature = proxy.getSignature();
        MethodProxyEnhancedClassShape.proxy = proxy;

        try {
            MethodProxy found = MethodProxy.find(MethodProxyEnhancedClassShape.class, proxy.getSignature());

            assertThat(found).isSameAs(proxy);
            assertThat(found.getSuperName()).isEqualTo("CGLIB$message$0");
        } finally {
            MethodProxyEnhancedClassShape.expectedSignature = null;
            MethodProxyEnhancedClassShape.proxy = null;
        }
    }

    public static class MethodProxyTarget {
        public String message(String name) {
            return "target " + name;
        }
    }

    public static class MethodProxyEnhancedClassShape extends MethodProxyTarget {
        private static Signature expectedSignature;
        private static MethodProxy proxy;

        public static MethodProxy CGLIB$findMethodProxy(Signature signature) {
            if (signature.equals(expectedSignature)) {
                return proxy;
            }
            return null;
        }

        public String CGLIB$message$0(String name) {
            return super.message(name);
        }
    }
}
