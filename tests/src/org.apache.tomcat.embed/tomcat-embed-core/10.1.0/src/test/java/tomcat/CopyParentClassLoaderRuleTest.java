/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.StringReader;

import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.startup.CopyParentClassLoaderRule;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertSame;

public class CopyParentClassLoaderRuleTest {
    @Test
    void propagatesParentLoaderDuringContainerParsing() throws Exception {
        StandardService service = new StandardService();
        service.setParentClassLoader(getClass().getClassLoader());
        StandardHost host = new StandardHost();
        Digester digester = new Digester();
        digester.push(service);
        digester.push(host);
        digester.addRule("Host", new CopyParentClassLoaderRule());
        digester.parse(new InputSource(new StringReader("<Host/>")));
        assertSame(service.getParentClassLoader(), host.getParentClassLoader());
    }
}
