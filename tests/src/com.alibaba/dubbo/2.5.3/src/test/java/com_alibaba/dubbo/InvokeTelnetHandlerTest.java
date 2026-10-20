/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.rpc.protocol.dubbo.telnet.InvokeTelnetHandler;
import org.junit.jupiter.api.Test;

public class InvokeTelnetHandlerTest {
    @Test
    void invokesAnExportedServiceMethodFromJsonArguments() {
        try (TelnetTestSupport.ExportedService service = TelnetTestSupport.exportEchoService()) {
            TelnetTestSupport.RecordingChannel channel =
                    new TelnetTestSupport.RecordingChannel(0);

            String response =
                    new InvokeTelnetHandler().telnet(channel, "Echo.echo(\"hello\")");

            assertThat(response).contains("echo:hello").contains("elapsed:");
            assertThat(service.getInvoker().getInvocation().getMethodName()).isEqualTo("echo");
            assertThat(service.getInvoker().getInvocation().getArguments())
                    .containsExactly("hello");
        }
    }
}
