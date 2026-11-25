/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.StringReader;
import org.xml.sax.InputSource;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.CopyParentClassLoaderRule;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyParentClassLoaderRuleTest {
    @Test
    void copiesParentLoaderDuringXmlConfiguration() throws Exception {
        StandardHost host = new StandardHost();
        host.setParentClassLoader(getClass().getClassLoader());
        StandardContext context = new StandardContext();
        Digester digester = new Digester();
        digester.push(host);
        digester.push(context);
        digester.addRule("Context", new CopyParentClassLoaderRule());
        digester.parse(new InputSource(new StringReader("<Context/>")));
        assertThat(context.getParentClassLoader()).isSameAs(host.getParentClassLoader());
    }
}
