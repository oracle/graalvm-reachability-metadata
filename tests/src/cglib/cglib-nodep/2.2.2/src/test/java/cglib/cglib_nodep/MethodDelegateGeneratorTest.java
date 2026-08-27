/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import net.sf.cglib.reflect.MethodDelegate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodDelegateGeneratorTest {
    @Test
    void delegatesToInstanceMethod() {
        MethodDelegate delegate = MethodDelegate.create(new GreetingTarget(), "greet", Greeting.class);

        assertThat(((Greeting) delegate).greet("CGLIB")).isEqualTo("Hello, CGLIB");
    }

    public interface Greeting {
        String greet(String name);
    }

    public static final class GreetingTarget {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }
}
