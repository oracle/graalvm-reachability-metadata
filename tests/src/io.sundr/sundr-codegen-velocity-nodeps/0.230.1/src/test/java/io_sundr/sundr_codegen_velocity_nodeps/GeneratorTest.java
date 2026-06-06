/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.runtime.ParserPoolImpl;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeConstants;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.runtime.resource.ResourceCacheImpl;
import io.sundr.deps.org.apache.velocity.runtime.resource.ResourceManagerImpl;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import io.sundr.deps.org.apache.velocity.texen.Generator;
import io.sundr.deps.org.apache.velocity.util.introspection.UberspectImpl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GeneratorTest {
    @TempDir
    Path templateRoot;

    @Test
    void getInstanceInitializesSingletonFromPackagedDefaults() {
        Generator generator = Generator.getInstance();

        assertThat(generator).isSameAs(Generator.getInstance());
    }

    @Test
    void rendersTemplateWithConfiguredVelocityEngine() throws Exception {
        Path outputRoot = templateRoot.resolve("generated");
        Files.createDirectories(outputRoot);
        Files.writeString(
                templateRoot.resolve("greeting.vm"),
                "Hello $name from $outputDirectory",
                StandardCharsets.UTF_8);

        Generator generator = new Generator(generatorProperties(outputRoot));
        generator.setVelocityEngine(fileTemplateEngine());
        VelocityContext context = new VelocityContext();
        context.put("name", "Grace");

        String rendered = generator.parse("greeting.vm", context);

        assertThat(rendered).isEqualTo("Hello Grace from " + outputRoot);
    }

    private static Properties generatorProperties(Path outputRoot) {
        Properties properties = new Properties();
        properties.setProperty(Generator.OUTPUT_PATH, outputRoot.toString());
        return properties;
    }

    private VelocityEngine fileTemplateEngine() {
        Properties properties = shadedRuntimeProperties();
        properties.setProperty(RuntimeConstants.RESOURCE_LOADER, "file");
        properties.setProperty("file.resource.loader.class", FileResourceLoader.class.getName());
        properties.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templateRoot.toString());

        VelocityEngine engine = new VelocityEngine(properties);
        engine.init();
        return engine;
    }

    private static Properties shadedRuntimeProperties() {
        Properties properties = new Properties();
        properties.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                NullLogChute.class.getName());
        properties.setProperty(
                RuntimeConstants.RESOURCE_MANAGER_CLASS,
                ResourceManagerImpl.class.getName());
        properties.setProperty(
                RuntimeConstants.RESOURCE_MANAGER_CACHE_CLASS,
                ResourceCacheImpl.class.getName());
        properties.setProperty(RuntimeConstants.PARSER_POOL_CLASS, ParserPoolImpl.class.getName());
        properties.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, UberspectImpl.class.getName());
        return properties;
    }
}
