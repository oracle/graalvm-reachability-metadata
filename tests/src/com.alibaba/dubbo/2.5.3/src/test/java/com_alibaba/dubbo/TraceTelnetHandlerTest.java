/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.rpc.protocol.dubbo.filter.TraceFilter;
import com.alibaba.dubbo.rpc.protocol.dubbo.telnet.TraceTelnetHandler;
import org.junit.jupiter.api.Test;

public class TraceTelnetHandlerTest {
    @Test
    void registersTracingForAnExportedServiceMethod() {
        try (TelnetTestSupport.ExportedService ignored = TelnetTestSupport.exportEchoService()) {
            TelnetTestSupport.RecordingChannel channel =
                    new TelnetTestSupport.RecordingChannel(0);

            String response = new TraceTelnetHandler().telnet(channel, "Echo echo 1");

            try {
                assertThat(response).isNull();
            } finally {
                TraceFilter.removeTracer(TelnetTestSupport.Echo.class, "echo", channel);
            }
        }
    }
}
