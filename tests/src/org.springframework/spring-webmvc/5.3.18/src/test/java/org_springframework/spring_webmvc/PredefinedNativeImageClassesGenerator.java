/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;

import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

public final class PredefinedNativeImageClassesGenerator {
    private static final String DEBUG_LOCATION_PROPERTY = "cglib.debugLocation";
    private static final String IGNORE_OBJENESIS_PROPERTY = "spring.objenesis.ignore";
    private static final String ALL_MODE = "all";
    private static final String CGLIB_MODE = "cglib";
    private static final String TRANSLET_MODE = "translet";

    private PredefinedNativeImageClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected CGLIB output directory, translet output directory, and mode");
        }
        Path cglibOutputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Path transletOutputDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        String mode = args[2];
        Files.createDirectories(cglibOutputDirectory);
        Files.createDirectories(transletOutputDirectory);

        if (!ALL_MODE.equals(mode) && !CGLIB_MODE.equals(mode) && !TRANSLET_MODE.equals(mode)) {
            throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        String previousDebugLocation = System.getProperty(DEBUG_LOCATION_PROPERTY);
        String previousObjenesisMode = System.getProperty(IGNORE_OBJENESIS_PROPERTY);
        try {
            if (ALL_MODE.equals(mode) || CGLIB_MODE.equals(mode)) {
                System.setProperty(DEBUG_LOCATION_PROPERTY, cglibOutputDirectory.toString());
                System.setProperty(IGNORE_OBJENESIS_PROPERTY, "true");
                generateMvcUriComponentsBuilderClasses();
                verifyGeneratedCglibClassFiles(cglibOutputDirectory);
            }
            if (ALL_MODE.equals(mode) || TRANSLET_MODE.equals(mode)) {
                generateXsltTranslet(transletOutputDirectory);
                verifyGeneratedTransletClassFiles(transletOutputDirectory);
            }
        }
        finally {
            restoreSystemProperty(DEBUG_LOCATION_PROPERTY, previousDebugLocation);
            restoreSystemProperty(IGNORE_OBJENESIS_PROPERTY, previousObjenesisMode);
        }
    }

    private static void generateMvcUriComponentsBuilderClasses() {
        MvcUriComponentsBuilderInnerControllerMethodInvocationInterceptorTest.ConstructorFallbackController controller =
                MvcUriComponentsBuilder.controller(
                        MvcUriComponentsBuilderInnerControllerMethodInvocationInterceptorTest.ConstructorFallbackController.class
                );
        controller.handle("spring");
        MvcUriComponentsBuilder.fromMethodCall(UriComponentsBuilder.fromPath(""), controller)
                .build()
                .toUriString();
    }

    private static void generateXsltTranslet(Path outputDirectory) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        XsltViewNativeImageSupport.configureTransletGeneration(transformerFactory, outputDirectory);
        Templates templates = transformerFactory.newTemplates(XsltViewNativeImageSupport.newStylesheetSource());
        templates.newTransformer();
    }

    private static void verifyGeneratedCglibClassFiles(Path cglibOutputDirectory) throws IOException {
        List<String> availableCglibClassFiles;
        try (Stream<Path> stream = Files.walk(cglibOutputDirectory)) {
            availableCglibClassFiles = stream
                    .filter(Files::isRegularFile)
                    .map(cglibOutputDirectory::relativize)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        verifyGeneratedClass(availableCglibClassFiles,
                "org/springframework/cglib/proxy/Enhancer\\$EnhancerKey\\$\\$KeyFactoryByCGLIB\\$\\$.*\\.class");
        verifyGeneratedClass(availableCglibClassFiles,
                "org_springframework/spring_webmvc/MvcUriComponentsBuilderInnerControllerMethodInvocationInterceptorTest\\$ConstructorFallbackController\\$\\$.*\\.class");
    }

    private static void verifyGeneratedTransletClassFiles(Path transletOutputDirectory) throws IOException {
        List<String> availableTransletClassFiles;
        try (Stream<Path> stream = Files.walk(transletOutputDirectory)) {
            availableTransletClassFiles = stream
                    .filter(Files::isRegularFile)
                    .map(transletOutputDirectory::relativize)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }

        verifyGeneratedClass(availableTransletClassFiles, XsltViewNativeImageSupport.TRANSLET_CLASS_FILE);
    }

    private static void verifyGeneratedClass(List<String> availableClassFiles, String expectedPattern) throws IOException {
        boolean matched = availableClassFiles.stream().anyMatch(path -> path.equals(expectedPattern) || path.matches(expectedPattern));
        if (matched) {
            return;
        }
        throw new IOException("Missing generated class matching " + expectedPattern + "; available=" + availableClassFiles);
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
