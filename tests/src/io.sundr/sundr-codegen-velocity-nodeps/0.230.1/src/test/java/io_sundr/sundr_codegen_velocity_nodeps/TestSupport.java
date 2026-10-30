/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.runtime.ParserPoolImpl;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.runtime.resource.ResourceCacheImpl;
import io.sundr.deps.org.apache.velocity.runtime.resource.ResourceManagerImpl;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import io.sundr.deps.org.apache.velocity.util.introspection.UberspectImpl;

import java.io.StringWriter;

final class TestSupport {
    private TestSupport() {
    }

    static VelocityEngine engine() {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty("runtime.log.logsystem", new NullLogChute());
        engine.setProperty("resource.manager.class", ResourceManagerImpl.class.getName());
        engine.setProperty("resource.manager.cache.class", ResourceCacheImpl.class.getName());
        engine.setProperty("parser.pool.class", ParserPoolImpl.class.getName());
        engine.setProperty("runtime.introspector.uberspect", UberspectImpl.class.getName());
        engine.setProperty("resource.loader", "classpath");
        engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        return engine;
    }

    static VelocityEngine classpathEngine() {
        VelocityEngine engine = engine();
        engine.setProperty("resource.loader", "classpath");
        engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        engine.init();
        return engine;
    }

    static String render(VelocityContext context, String template) {
        VelocityEngine engine = engine();
        engine.init();
        StringWriter writer = new StringWriter();
        if (!engine.evaluate(context, writer, "public-api", template)) {
            throw new AssertionError("Template evaluation did not complete");
        }
        return writer.toString();
    }

    public static class Person {
        private String name = "Ada";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return true;
        }
    }
}
