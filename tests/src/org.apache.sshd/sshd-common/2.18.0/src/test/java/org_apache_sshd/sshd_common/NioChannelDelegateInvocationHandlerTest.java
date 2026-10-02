/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.Channel;

import org.apache.sshd.common.util.closeable.NioChannelDelegateInvocationHandler;
import org.junit.jupiter.api.Test;

public class NioChannelDelegateInvocationHandlerTest {
    @Test
    void delegatesChannelStateAndClose() throws IOException {
        ChannelImpl target = new ChannelImpl();
        ChannelImpl delegate = new ChannelImpl();
        ChannelView proxy = NioChannelDelegateInvocationHandler.wrapDelegateChannel(
                target, ChannelView.class, delegate);

        assertThat(proxy.isOpen()).isTrue();
        proxy.close();

        assertThat(target.open).isFalse();
        assertThat(delegate.open).isFalse();
    }

    public interface ChannelView extends Channel {
    }

    public static class ChannelImpl implements ChannelView {
        private boolean open = true;

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
