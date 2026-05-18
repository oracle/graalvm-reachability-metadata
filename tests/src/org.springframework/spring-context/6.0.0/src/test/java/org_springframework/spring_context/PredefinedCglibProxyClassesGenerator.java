/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.objenesis.SpringObjenesis;

public final class PredefinedCglibProxyClassesGenerator {
    private static final String DEBUG_LOCATION_PROPERTY =
            "cglib.debugLocation";
    private static final List<String> EXPECTED_CLASS_FILES = List.of(
            "org_springframework/spring_context/ConfigurationClassBeanDefinitionReaderTest$ImportedResourceConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$1.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$2.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$1.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$2.class",
            "org/springframework/cache/concurrent/ConcurrentMapCacheFactoryBean$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest$ProxyBackedConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest$ProxyBackedConfiguration$$SpringCGLIB$$1.class",
            "org_springframework/spring_context/ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest$ProxyBackedConfiguration$$SpringCGLIB$$2.class"
    );

    private PredefinedCglibProxyClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }
        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);

        String previousDebugLocation = System.getProperty(DEBUG_LOCATION_PROPERTY);
        String previousObjenesisMode = System.getProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME);
        try {
            System.setProperty(DEBUG_LOCATION_PROPERTY, outputDirectory.toString());
            System.setProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME, "true");
            generateProxyClasses();
            verifyExpectedClassFiles(outputDirectory);
        } finally {
            restoreSystemProperty(DEBUG_LOCATION_PROPERTY, previousDebugLocation);
            restoreSystemProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME, previousObjenesisMode);
        }
    }

    private static void generateProxyClasses() {
        generateConfigurationProxyClass(ConfigurationClassBeanDefinitionReaderTest.ImportedResourceConfiguration.class);
        generateConfigurationProxyClass(
                ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration.class
        );
        generateConfigurationProxyClass(
                ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.InterfaceProxyConfiguration.class
        );
        generateFactoryBeanProxyClass();
        generateAheadOfTimeConfigurationProxyClass();
    }

    private static void generateConfigurationProxyClass(Class<?> configurationClass) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configurationClass)) {
            context.getBean(configurationClass);
        }
    }

    private static void generateFactoryBeanProxyClass() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration.class
        )) {
            ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration configuration =
                    context.getBean(ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration.class);
            configuration.cglibFactoryBean();
        }
    }

    private static void generateAheadOfTimeConfigurationProxyClass() {
        InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
        ClassName targetClassName = ClassName.get(
                "org_springframework.spring_context.generated", "TestApplication");
        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(targetClassName), generatedFiles);
        GenericApplicationContext context = new GenericApplicationContext();
        try {
            AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(context);
            reader.register(
                    ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest.ProxyBackedConfiguration.class
            );
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
            generationContext.writeGeneratedContent();
            if (generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE).isEmpty()) {
                throw new IllegalStateException("Expected generated AOT sources for proxy-backed configuration");
            }
        } finally {
            context.close();
        }
    }

    private static void verifyExpectedClassFiles(Path outputDirectory) throws IOException {
        List<String> missingClassFiles = EXPECTED_CLASS_FILES.stream()
                .filter(relativePath -> !Files.isRegularFile(outputDirectory.resolve(relativePath)))
                .toList();
        if (missingClassFiles.isEmpty()) {
            return;
        }

        List<String> availableClassFiles;
        try (Stream<Path> stream = Files.walk(outputDirectory)) {
            availableClassFiles = stream
                    .filter(Files::isRegularFile)
                    .map(outputDirectory::relativize)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        throw new IOException("Missing generated CGLIB proxy classes: " + missingClassFiles + "; available=" + availableClassFiles);
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
