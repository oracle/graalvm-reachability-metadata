/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.event.ReferenceInsertionEventHandler;
import io.sundr.deps.org.apache.velocity.exception.VelocityException;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeConstants;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeInstance;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class RuntimeInstanceTest {
    @Test
    void initializedRuntimeEvaluatesTemplatesWithBuiltInDirectives() {
        RuntimeInstance runtime = new RuntimeInstance();
        runtime.init(shadedRuntimeProperties());

        VelocityContext context = new VelocityContext();
        context.put("names", List.of("Ada", "Grace"));
        StringWriter writer = new StringWriter();

        boolean rendered = runtime.evaluate(
                context,
                writer,
                "runtime-instance-test",
                "#foreach($name in $names)$velocityCount:$name#if($velocityHasNext),#end#end");

        assertThat(rendered).isTrue();
        assertThat(runtime.isInitialized()).isTrue();
        assertThat(writer).hasToString("1:Ada,2:Grace");
        assertThat(runtime.getDirective("foreach")).isNotNull();
    }

    @Test
    void configuredReferenceInsertionHandlerParticipatesInRendering() {
        RuntimeInstance runtime = new RuntimeInstance();
        Properties properties = shadedRuntimeProperties();
        properties.setProperty(
                RuntimeConstants.EVENTHANDLER_REFERENCEINSERTION,
                ReferenceInsertionRecorder.class.getName());
        runtime.init(properties);

        VelocityContext context = new VelocityContext();
        context.put("name", "Ada");
        StringWriter writer = new StringWriter();

        boolean rendered = runtime.evaluate(context, writer, "event-handler-test", "Hello $name");

        assertThat(rendered).isTrue();
        assertThat(writer).hasToString("Hello Ada!");
    }

    @Test
    void initRejectsConfiguredResourceManagerThatDoesNotImplementRequiredApi() {
        RuntimeInstance runtime = new RuntimeInstance();
        Properties properties = shadedRuntimeProperties();
        properties.setProperty(RuntimeConstants.RESOURCE_MANAGER_CLASS, String.class.getName());

        assertThatThrownBy(() -> runtime.init(properties))
                .isInstanceOf(VelocityException.class)
                .hasMessageContaining("ResourceManager")
                .hasMessageContaining("does not implement");
    }

    public static final class ReferenceInsertionRecorder implements ReferenceInsertionEventHandler {
        public ReferenceInsertionRecorder() {
        }

        @Override
        public Object referenceInsert(String reference, Object value) {
            if (value instanceof String) {
                return value + "!";
            }
            return value;
        }
    }

    private static Properties shadedRuntimeProperties() {
        Properties properties = new Properties();
        properties.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                "io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute");
        properties.setProperty(
                RuntimeConstants.RESOURCE_MANAGER_CLASS,
                "io.sundr.deps.org.apache.velocity.runtime.resource.ResourceManagerImpl");
        properties.setProperty(
                RuntimeConstants.RESOURCE_MANAGER_CACHE_CLASS,
                "io.sundr.deps.org.apache.velocity.runtime.resource.ResourceCacheImpl");
        properties.setProperty(
                RuntimeConstants.PARSER_POOL_CLASS,
                "io.sundr.deps.org.apache.velocity.runtime.ParserPoolImpl");
        properties.setProperty(
                RuntimeConstants.UBERSPECT_CLASSNAME,
                "io.sundr.deps.org.apache.velocity.util.introspection.UberspectImpl");
        properties.setProperty(
                "file.resource.loader.class",
                "io.sundr.deps.org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        properties.setProperty(
                "string.resource.loader.class",
                "io.sundr.deps.org.apache.velocity.runtime.resource.loader.StringResourceLoader");
        return properties;
    }
}
