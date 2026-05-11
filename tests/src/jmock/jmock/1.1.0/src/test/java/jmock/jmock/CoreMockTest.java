/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import org.jmock.core.CoreMock;
import org.jmock.core.DynamicMockError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CoreMockTest {
    @Test
    void constructorCreatesProxyThatDispatchesThroughCoreMock() {
        CoreMock mock = new CoreMock(Runnable.class, "mockRunnable");

        Object proxy = mock.proxy();

        assertThat(proxy).isInstanceOf(Runnable.class);
        assertThat(proxy.toString()).isEqualTo("mockRunnable");
        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.equals(new Object())).isFalse();
        assertThatThrownBy(() -> ((Runnable) proxy).run())
                .isInstanceOf(DynamicMockError.class)
                .hasMessageContaining("unexpected invocation");
    }
}
