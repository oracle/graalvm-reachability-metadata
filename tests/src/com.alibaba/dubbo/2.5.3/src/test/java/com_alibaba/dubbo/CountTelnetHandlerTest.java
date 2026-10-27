/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.rpc.protocol.dubbo.telnet.CountTelnetHandler;
import org.junit.jupiter.api.Test;

public class CountTelnetHandlerTest {
    @Test
    void countsAllAndNamedServiceMethods() throws Exception {
        try (TelnetTestSupport.ExportedService ignored = TelnetTestSupport.exportEchoService()) {
            TelnetTestSupport.RecordingChannel channel =
                    new TelnetTestSupport.RecordingChannel(4);
            CountTelnetHandler handler = new CountTelnetHandler();

            assertThat(handler.telnet(channel, "Echo 1")).isEmpty();
            assertThat(handler.telnet(channel, "Echo echo 1")).isEmpty();

            assertThat(channel.awaitSends()).isTrue();
            assertThat(channel.getMessages().toString()).contains("echo").contains("telnet> ");
        }
    }
}
