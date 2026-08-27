/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.reflect.MethodDelegate;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MethodDelegateInnerGeneratorTest {

    @Test
    void delegatesInterfaceMethodToTargetInstance() {
        try {
            GreetingTarget target = new GreetingTarget();
            GreetingDelegate delegate =
                    (GreetingDelegate) MethodDelegate.create(target, "greet", GreetingDelegate.class);

            assertThat(delegate.greet("Ada")).isEqualTo("hello Ada");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public interface GreetingDelegate {
        String greet(String name);
    }

    public static class GreetingTarget {
        public String greet(String name) {
            return "hello " + name;
        }
    }
}
