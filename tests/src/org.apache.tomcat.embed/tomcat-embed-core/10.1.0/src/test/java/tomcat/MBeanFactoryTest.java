/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Path;

import org.apache.catalina.mbeans.MBeanFactory;
import org.apache.catalina.valves.RemoteAddrValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class MBeanFactoryTest {
    @TempDir
    Path directory;

    @Test
    void createsValveOnNamedHostUsingManagementFactory() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            server.start();
            MBeanFactory factory = new MBeanFactory();
            factory.setContainer(server.tomcat().getService());
            String objectName = factory.createValve(RemoteAddrValve.class.getName(), "Tomcat:type=Host,host=localhost");
            assertThat(objectName).contains("RemoteAddrValve");
            assertThat(server.tomcat().getHost().getPipeline().getValves())
                    .anyMatch(valve -> valve instanceof RemoteAddrValve);
        }
    }
}
