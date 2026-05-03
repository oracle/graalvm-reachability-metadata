/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.utils.ReflectionMethodInvoker;

public class ReflectionMethodInvokerTest {
    @Test
    void invokeFindsTargetMethodAndReturnsResult() throws Exception {
        ReflectionMethodInvoker<String, Integer> invoker = new ReflectionMethodInvoker<>(
                String.class,
                Integer.class,
                "indexOf",
                String.class,
                int.class);

        assertThat(invoker.isInitialized()).isFalse();

        Integer index = invoker.invoke("abracadabra", "ra", 3);

        assertThat(index).isEqualTo(9);
        assertThat(invoker.isInitialized()).isTrue();
    }
}
