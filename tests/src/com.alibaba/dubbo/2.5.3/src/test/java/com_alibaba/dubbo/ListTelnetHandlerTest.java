/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.rpc.protocol.dubbo.telnet.ListTelnetHandler;
import org.junit.jupiter.api.Test;

public class ListTelnetHandlerTest {
    @Test
    void listsDetailedMethodsForAnExportedService() {
        try (TelnetTestSupport.ExportedService ignored = TelnetTestSupport.exportEchoService()) {
            TelnetTestSupport.RecordingChannel channel =
                    new TelnetTestSupport.RecordingChannel(0);

            String methods = new ListTelnetHandler().telnet(channel, "-l Echo");

            assertThat(methods).contains("java.lang.String echo(java.lang.String)");
        }
    }
}
