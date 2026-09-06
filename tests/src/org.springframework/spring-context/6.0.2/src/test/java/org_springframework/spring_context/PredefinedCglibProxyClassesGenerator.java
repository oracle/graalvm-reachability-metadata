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

import org.springframework.cache.concurrent.ConcurrentMapCacheFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class PredefinedCglibProxyClassesGenerator {

    private static final String CGLIB_DEBUG_LOCATION_PROPERTY = "cglib.debugLocation";

    private static final String SPRING_OBJENESIS_IGNORE_PROPERTY = "spring.objenesis.ignore";

    private static final List<String> EXPECTED_CLASS_FILES = List.of(
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$0.class",
            "org/springframework/cache/concurrent/ConcurrentMapCacheFactoryBean$$SpringCGLIB$$0.class");

    private PredefinedCglibProxyClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }

        final Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);
        System.setProperty(CGLIB_DEBUG_LOCATION_PROPERTY, outputDirectory.toString());
        System.setProperty(SPRING_OBJENESIS_IGNORE_PROPERTY, "true");

        generateCglibFactoryBeanProxyClasses();
        generateInterfaceFactoryBeanProxyClasses();
        verifyExpectedClassFiles(outputDirectory);
    }

    private static void generateCglibFactoryBeanProxyClasses() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration.class)) {
            final ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration configuration =
                    context.getBean(
                            ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.CglibProxyConfiguration.class);
            final ConcurrentMapCacheFactoryBean factoryBean = configuration.cglibFactoryBean();
            factoryBean.getObjectType();
            factoryBean.getObject();
        }
    }

    private static void generateInterfaceFactoryBeanProxyClasses() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.InterfaceProxyConfiguration.class)) {
            final ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.InterfaceProxyConfiguration configuration =
                    context.getBean(
                            ConfigurationClassEnhancerInnerBeanMethodInterceptorTest.InterfaceProxyConfiguration.class);
            configuration.interfaceFactoryBean().getObject();
        }
    }

    private static void verifyExpectedClassFiles(Path outputDirectory) throws IOException {
        final List<String> missingClassFiles = EXPECTED_CLASS_FILES.stream()
                .filter(relativePath -> !Files.isRegularFile(outputDirectory.resolve(relativePath)))
                .toList();
        if (!missingClassFiles.isEmpty()) {
            throw new IOException("Missing generated CGLIB proxy classes: " + missingClassFiles);
        }
    }
}
