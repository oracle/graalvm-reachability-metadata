/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_context;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

public final class PredefinedCglibProxyClassesGenerator {
    private static final String DEBUG_LOCATION_PROPERTY = "cglib.debugLocation";
    private static final String EXPECTED_CLASS_FILE =
            "org_springframework_cloud/spring_cloud_function_context/"
                    + "BeanFactoryAwareFunctionRegistryAnonymous1Test$GreetingPojo$$SpringCGLIB$$0.class";

    private PredefinedCglibProxyClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }
        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);

        String previousDebugLocation = System.getProperty(DEBUG_LOCATION_PROPERTY);
        try {
            System.setProperty(DEBUG_LOCATION_PROPERTY, outputDirectory.toString());
            generateGreetingPojoProxyClass();
            verifyExpectedClassFile(outputDirectory);
        } finally {
            restoreSystemProperty(DEBUG_LOCATION_PROPERTY, previousDebugLocation);
        }
    }

    private static void generateGreetingPojoProxyClass() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(
                "greetingPojo",
                BeanFactoryAwareFunctionRegistryAnonymous1Test.GreetingPojo.class
        );
        applicationContext.refresh();
        try {
            BeanFactoryAwareFunctionRegistry registry = new BeanFactoryAwareFunctionRegistry(
                    new DefaultConversionService(),
                    new CompositeMessageConverter(List.of(new StringMessageConverter())),
                    new PassthroughJsonMapper(),
                    null,
                    null
            );
            registry.setApplicationContext(applicationContext);
            registry.lookup(Function.class, "greetingPojo");
        } finally {
            applicationContext.close();
        }
    }

    private static void verifyExpectedClassFile(Path outputDirectory) throws IOException {
        Path generatedClassFile = outputDirectory.resolve(EXPECTED_CLASS_FILE);
        if (Files.isRegularFile(generatedClassFile)) {
            return;
        }
        throw new IOException("Missing generated CGLIB proxy class: " + generatedClassFile);
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    private static final class PassthroughJsonMapper extends JsonMapper {
        @SuppressWarnings("unchecked")
        @Override
        protected <T> T doFromJson(Object json, Type type) {
            return (T) json;
        }

        @Override
        public byte[] toJson(Object value) {
            byte[] json = super.toJson(value);
            return json != null ? json : toString(value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String toString(Object value) {
            return String.valueOf(value);
        }
    }
}
