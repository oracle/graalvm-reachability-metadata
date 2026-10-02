/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;

import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoServiceEventListener;
import org.apache.sshd.common.util.EventListenerUtils;
import org.junit.jupiter.api.Test;

public class EventListenerUtilsTest {
    @Test
    void dispatchesEventsToAllListeners() throws IOException {
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        IoServiceEventListener proxy = EventListenerUtils.proxyWrapper(
                IoServiceEventListener.class, List.of(first, second));

        proxy.connectionAccepted(null, null, null, null);

        assertThat(first.accepted).isEqualTo(1);
        assertThat(second.accepted).isEqualTo(1);
    }

    public static class RecordingListener implements IoServiceEventListener {
        private int accepted;

        @Override
        public void connectionAccepted(IoAcceptor acceptor, SocketAddress localAddress,
                SocketAddress remoteAddress, SocketAddress serviceAddress) {
            accepted++;
        }
    }
}
