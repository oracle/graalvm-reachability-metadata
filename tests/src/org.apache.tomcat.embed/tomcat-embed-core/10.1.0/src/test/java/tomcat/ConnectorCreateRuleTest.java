/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.StringReader;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.core.StandardThreadExecutor;
import org.apache.catalina.startup.ConnectorCreateRule;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ConnectorCreateRuleTest {
    @Test
    void configuresExecutorAndSslImplementationFromXml() throws Exception {
        StandardService service = new StandardService();
        StandardThreadExecutor executor = new StandardThreadExecutor();
        executor.setName("workers");
        service.addExecutor(executor);
        Digester digester = new Digester();
        digester.push(service);
        digester.addRule("Connector", new ConnectorCreateRule());
        digester.addSetNext("Connector", "addConnector", Connector.class.getName());
        String xml = "<Connector executor=\"workers\" sslImplementationName=\""
                + JSSEImplementation.class.getName() + "\"/>";
        digester.parse(new InputSource(new StringReader(xml)));
        Http11NioProtocol protocol = (Http11NioProtocol) service.findConnectors()[0].getProtocolHandler();
        assertSame(executor, protocol.getExecutor());
        assertEquals(JSSEImplementation.class.getName(), protocol.getSslImplementationName());
    }
}
