/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.util.closeable.AutoCloseableDelegateInvocationHandler;
import org.junit.jupiter.api.Test;

public class AutoCloseableDelegateInvocationHandlerTest {
    @Test
    void forwardsOperationsAndClosesBothObjects() throws Exception {
        CloseableServiceImpl target = new CloseableServiceImpl("target");
        CloseableServiceImpl delegate = new CloseableServiceImpl("delegate");

        try (CloseableService proxy = AutoCloseableDelegateInvocationHandler.wrapDelegateCloseable(
                target, CloseableService.class, delegate)) {
            assertThat(proxy.value()).isEqualTo("target");
        }

        assertThat(target.closed).isTrue();
        assertThat(delegate.closed).isTrue();
    }

    public interface CloseableService extends AutoCloseable {
        String value();

        @Override
        void close();
    }

    public static class CloseableServiceImpl implements CloseableService {
        private final String value;
        private boolean closed;

        CloseableServiceImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
