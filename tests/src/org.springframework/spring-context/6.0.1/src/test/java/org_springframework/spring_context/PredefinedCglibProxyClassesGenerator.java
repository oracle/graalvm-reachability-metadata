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
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.cache.concurrent.ConcurrentMapCacheFactoryBean;
import org.springframework.javapoet.ClassName;

public final class PredefinedCglibProxyClassesGenerator {
    private static final String CGLIB_DEBUG_LOCATION_PROPERTY = "cglib.debugLocation";
    private static final String SPRING_OBJENESIS_IGNORE_PROPERTY = "spring.objenesis.ignore";

    private static final List<String> EXPECTED_CLASS_FILES = List.of(
            "org_springframework/spring_context/ConfigurationClassBeanDefinitionReaderTest$ImportedResourceConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest$EnhancedConfigurationApplication$$SpringCGLIB$$0.class",
            "org/springframework/cache/concurrent/ConcurrentMapCacheFactoryBean$$SpringCGLIB$$0.class"
    );

    private PredefinedCglibProxyClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }

        final Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);

        final String previousDebugLocation = System.getProperty(CGLIB_DEBUG_LOCATION_PROPERTY);
        final String previousObjenesisIgnore = System.getProperty(SPRING_OBJENESIS_IGNORE_PROPERTY);
        try {
            System.setProperty(CGLIB_DEBUG_LOCATION_PROPERTY, outputDirectory.toString());
            System.setProperty(SPRING_OBJENESIS_IGNORE_PROPERTY, "true");
            generateProxyClasses();
            verifyExpectedClassFiles(outputDirectory);
        } finally {
            restoreSystemProperty(CGLIB_DEBUG_LOCATION_PROPERTY, previousDebugLocation);
            restoreSystemProperty(SPRING_OBJENESIS_IGNORE_PROPERTY, previousObjenesisIgnore);
        }
    }

    private static void generateProxyClasses() throws Exception {
        generateImportedResourceConfigurationProxyClass();
        generateInterfaceProxyConfigurationClasses();
        generateCglibFactoryBeanProxyClasses();
        generateEnhancedConfigurationApplicationProxyClass();
    }

    private static void generateImportedResourceConfigurationProxyClass() {
        try (AnnotationConfigApplicationContext ignored = new AnnotationConfigApplicationContext(
                ConfigurationClassBeanDefinitionReaderTest.ImportedResourceConfiguration.class
        )) {
            // Refreshing the context generates and dumps the imported-resource configuration proxy class.
        }
    }

    private static void generateInterfaceProxyConfigurationClasses() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.InterfaceProxyConfiguration.class
        )) {
            final ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.InterfaceProxyConfiguration configuration =
                    context.getBean(ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.InterfaceProxyConfiguration.class);
            final ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.ProductFactory interceptedFactory =
                    configuration.interfaceFactoryBean();
            interceptedFactory.getObject();
        }
    }

    private static void generateCglibFactoryBeanProxyClasses() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration.class
        )) {
            final ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration configuration =
                    context.getBean(ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration.class);
            final ConcurrentMapCacheFactoryBean interceptedFactory = configuration.cglibFactoryBean();
            interceptedFactory.getObject();
            interceptedFactory.isSingleton();
        }
    }

    private static void generateEnhancedConfigurationApplicationProxyClass() {
        final InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
        final DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(
                        ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest.EnhancedConfigurationApplication.class
                )),
                generatedFiles
        );

        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            new AnnotatedBeanDefinitionReader(applicationContext).register(
                    ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest.EnhancedConfigurationApplication.class
            );
            new ApplicationContextAotGenerator().processAheadOfTime(applicationContext, generationContext);
            generationContext.writeGeneratedContent();
        }
    }

    private static void verifyExpectedClassFiles(Path outputDirectory) throws IOException {
        final List<String> missingClassFiles = EXPECTED_CLASS_FILES.stream()
                .filter(relativePath -> !Files.isRegularFile(outputDirectory.resolve(relativePath)))
                .toList();
        if (missingClassFiles.isEmpty()) {
            return;
        }

        final List<String> availableClassFiles;
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
