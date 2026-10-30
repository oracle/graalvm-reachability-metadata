/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class VelocityTestSupport {
    private VelocityTestSupport() {
    }

    static VelocityEngine configuredEngine() {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty("runtime.log.logsystem", new NullLogChute());
        engine.setProperty("velocimacro.library", "");
        engine.setProperty("resource.loader", "class");
        engine.setProperty("class.resource.loader.class", ClasspathResourceLoader.class.getName());
        return engine;
    }

    static VelocityEngine engine() throws Exception {
        VelocityEngine engine = configuredEngine();
        engine.init();
        return engine;
    }

    static String render(VelocityContext context, String source) throws Exception {
        return render(engine(), context, source);
    }

    static String render(VelocityEngine engine, VelocityContext context, String source) throws Exception {
        StringWriter output = new StringWriter();
        assertTrue(engine.evaluate(context, output, "test-template", source));
        return output.toString();
    }
}
