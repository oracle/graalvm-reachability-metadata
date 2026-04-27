/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import com.sun.jdmk.comm.HtmlAdapterServer;
import org.apache.log4j.jmx.Agent;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentTest {

    @Test
    void startCreatesAndStartsHtmlAdapterServer() throws Exception {
        List<MBeanServer> existingServers = new ArrayList<>(MBeanServerFactory.findMBeanServer(null));
        HtmlAdapterServer.reset();

        new Agent().start();

        List<MBeanServer> createdServers = MBeanServerFactory.findMBeanServer(null).stream()
                .filter(server -> !existingServers.contains(server))
                .toList();

        try {
            ObjectName htmlAdapterName = new ObjectName("Adaptor:name=html,port=8082");
            ObjectName hierarchyName = new ObjectName("log4j:hiearchy=default");

            assertThat(HtmlAdapterServer.getStartCount()).isEqualTo(1);
            assertThat(createdServers)
                    .isNotEmpty()
                    .anySatisfy(server -> {
                        assertThat(server.isRegistered(htmlAdapterName)).isTrue();
                        assertThat(server.isRegistered(hierarchyName)).isTrue();
                    });
        } finally {
            createdServers.forEach(MBeanServerFactory::releaseMBeanServer);
        }
    }
}
