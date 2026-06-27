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

public final class PredefinedCglibProxyClassesGenerator {

    private static final String DEBUG_LOCATION_PROPERTY = "cglib.debugLocation";

    private static final List<String> EXPECTED_CLASS_FILES = List.of(
            "org_springframework/spring_context/ConfigurationClassBeanDefinitionReaderTest$ImportedResourceConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$1.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$CglibProxyConfiguration$$SpringCGLIB$$2.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$1.class",
            "org_springframework/spring_context/ConfigurationClassEnhancerInnerBeanMethodInterceptorTest$InterfaceProxyConfiguration$$SpringCGLIB$$2.class",
            "org_springframework/spring_context/ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest$ConstructorInjectedConfiguration$$SpringCGLIB$$0.class",
            "org_springframework/spring_context/ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest$ConstructorInjectedConfiguration$$SpringCGLIB$$1.class",
            "org_springframework/spring_context/ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest$ConstructorInjectedConfiguration$$SpringCGLIB$$2.class"
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
        String previousObjenesisMode = System.getProperty("spring.objenesis.ignore");
        try {
            System.setProperty(DEBUG_LOCATION_PROPERTY, outputDirectory.toString());
            System.setProperty("spring.objenesis.ignore", "true");
            generateProxyClasses();
            verifyExpectedClassFiles(outputDirectory);
        } finally {
            restoreSystemProperty(DEBUG_LOCATION_PROPERTY, previousDebugLocation);
            restoreSystemProperty("spring.objenesis.ignore", previousObjenesisMode);
        }
    }

    private static void generateProxyClasses() throws Exception {
        new ConfigurationClassBeanDefinitionReaderTest().importedResourceUsesDeclaredBeanDefinitionReaderConstructor();

        ConfigurationClassEnhancerInnerBeanMethodInterceptorTest enhancerTest =
                new ConfigurationClassEnhancerInnerBeanMethodInterceptorTest();
        enhancerTest.nonFinalFactoryBeanUsesCglibProxyInstantiatedThroughDefaultConstructorFallback();
        enhancerTest.finalFactoryBeanWithInterfaceReturnTypeUsesInterfaceProxy();

        new ConfigurationClassPostProcessorInnerConfigurationClassProxyBeanRegistrationCodeFragmentsTest()
                .aotGenerationUsesProxyConstructorForConstructorInjectedConfigurationClass();
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
