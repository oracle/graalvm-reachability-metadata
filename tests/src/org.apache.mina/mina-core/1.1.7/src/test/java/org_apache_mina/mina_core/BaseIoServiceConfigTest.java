/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.ThreadModel;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BaseIoServiceConfigTest {
    @Test
    void clonesConfiguredAcceptorSettings() {
        SocketAcceptorConfig original = new SocketAcceptorConfig();
        original.setThreadModel(ThreadModel.MANUAL);
        original.setBacklog(64);
        original.setReuseAddress(true);
        IoFilterAdapter audit = new IoFilterAdapter();
        original.getFilterChain().addLast("audit", audit);

        SocketAcceptorConfig copy = (SocketAcceptorConfig) original.clone();

        assertNotSame(original, copy);
        assertEquals(64, copy.getBacklog());
        assertEquals(original.isReuseAddress(), copy.isReuseAddress());
        assertSame(ThreadModel.MANUAL, copy.getThreadModel());
        assertSame(audit, copy.getFilterChain().get("audit"));
        copy.setBacklog(128);
        assertEquals(64, original.getBacklog());
        assertEquals(128, copy.getBacklog());
    }
}
