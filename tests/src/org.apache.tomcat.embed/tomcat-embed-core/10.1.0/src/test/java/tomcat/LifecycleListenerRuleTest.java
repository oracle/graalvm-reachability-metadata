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
import org.apache.catalina.startup.LifecycleListenerRule;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LifecycleListenerRuleTest {
    @Test
    void attachesListenerWhenParsingContainerConfiguration() throws Exception {
        StandardContext context = new StandardContext();
        Digester digester = new Digester();
        digester.push(context);
        digester.addRule("Context", new LifecycleListenerRule(StandardContextTest.WrapperLifecycle.class.getName(),
                "configClass"));
        digester.parse(new InputSource(new StringReader("<Context/>")));
        assertThat(context.findLifecycleListeners()).hasSize(1);
        assertThat(context.findLifecycleListeners()[0]).isInstanceOf(StandardContextTest.WrapperLifecycle.class);
    }
}
