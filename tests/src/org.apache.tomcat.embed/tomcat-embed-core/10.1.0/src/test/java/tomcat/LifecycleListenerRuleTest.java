/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.StringReader;

import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.LifecycleListenerRule;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LifecycleListenerRuleTest {
    @Test
    void attachesConfiguredListenerWhenParsingHost() throws Exception {
        StandardHost context = new StandardHost();
        Digester digester = new Digester();
        digester.push(context);
        digester.addRule("Context", new LifecycleListenerRule(Events.class.getName(), "configClass"));
        digester.parse(new InputSource(new StringReader("<Context/>")));
        Events listener = (Events) context.findLifecycleListeners()[0];
        try {
            context.init();
            assertEquals("after_init", listener.data);
        } finally {
            context.destroy();
        }
    }

    public static class Events implements LifecycleListener {
        private Object data;

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            data = event.getType();
        }
    }
}
