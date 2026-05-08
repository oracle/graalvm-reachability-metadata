/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib_extra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.extra.EclipseBasedStepBuilder;
import com.diffplug.spotless.extra.wtp.EclipseWtpFormatterStep;

public class EclipseWtpFormatterStepTest {
    private static final String CSS_FORMATTER_CLASS = "com/diffplug/spotless/extra/eclipse/wtp/"
            + "EclipseCssFormatterStepImpl.class";
    private static final String XML_FORMATTER_CLASS = "com/diffplug/spotless/extra/eclipse/wtp/"
            + "EclipseXmlFormatterStepImpl.class";
    private static final Map<String, String> STUB_FORMATTER_CLASSES = Map.of(
            CSS_FORMATTER_CLASS,
            """
                    yv66vgAAADQAEAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+
                    AQADKClWBwAIAQBDY29tL2RpZmZwbHVnL3Nwb3RsZXNzL2V4dHJhL2VjbGlwc2Uv
                    d3RwL0VjbGlwc2VDc3NGb3JtYXR0ZXJTdGVwSW1wbAEAGShMamF2YS91dGlsL1By
                    b3BlcnRpZXM7KVYBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAGZm9ybWF0AQAm
                    KExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL1N0cmluZzsBAApTb3VyY2VG
                    aWxlAQAgRWNsaXBzZUNzc0Zvcm1hdHRlclN0ZXBJbXBsLmphdmEAIQAHAAIAAAAA
                    AAIAAQAFAAkAAQAKAAAAIQABAAIAAAAFKrcAAbEAAAABAAsAAAAKAAIAAAAGAAQA
                    BwABAAwADQABAAoAAAAaAAEAAgAAAAIrsAAAAAEACwAAAAYAAQAAAAoAAQAOAAAA
                    AgAP
                    """,
            XML_FORMATTER_CLASS,
            """
                    yv66vgAAADQAEAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+
                    AQADKClWBwAIAQBDY29tL2RpZmZwbHVnL3Nwb3RsZXNzL2V4dHJhL2VjbGlwc2Uv
                    d3RwL0VjbGlwc2VYbWxGb3JtYXR0ZXJTdGVwSW1wbAEAGShMamF2YS91dGlsL1By
                    b3BlcnRpZXM7KVYBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAGZm9ybWF0AQA4
                    KExqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5n
                    L1N0cmluZzsBAApTb3VyY2VGaWxlAQAgRWNsaXBzZVhtbEZvcm1hdHRlclN0ZXBJ
                    bXBsLmphdmEAIQAHAAIAAAAAAAIAAQAFAAkAAQAKAAAAIQABAAIAAAAFKrcAAbEA
                    AAABAAsAAAAKAAIAAAAGAAQABwABAAwADQABAAoAAAAaAAEAAwAAAAIrsAAAAAEA
                    CwAAAAYAAQAAAAoAAQAOAAAAAgAP
                    """);

    @TempDir
    private Path tempDir;

    @Test
    void cssFormatterUsesReflectiveConstructorAndFormatMethod() throws Exception {
        try {
            final Path cssFile = tempDir.resolve("style.css");
            Files.writeString(cssFile, "a { color: red; }");

            final String formatted = formatter(EclipseWtpFormatterStep.CSS).format("a{color:red;}", cssFile.toFile());

            assertTrue(formatted.contains("color"), formatted);
            assertTrue(formatted.contains("red"), formatted);
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
        }
    }

    @Test
    void xmlFormatterUsesReflectiveConstructorAndFileAwareFormatMethod() throws Exception {
        try {
            final Path xmlFile = tempDir.resolve("document.xml");
            Files.writeString(xmlFile, "<root><child/></root>");

            final String formatted = formatter(EclipseWtpFormatterStep.XML)
                    .format("<root><child/></root>", xmlFile.toFile());

            assertTrue(formatted.contains("<root"), formatted);
            assertTrue(formatted.contains("child"), formatted);
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
        }
    }

    private FormatterStep formatter(EclipseWtpFormatterStep formatterStep) {
        final EclipseBasedStepBuilder builder = formatterStep.createBuilder(new StubFormatterProvisioner(tempDir));
        builder.setVersion(EclipseWtpFormatterStep.defaultVersion());
        return builder.build();
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Throwable throwable) {
        NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
    }

    private static final class StubFormatterProvisioner implements Provisioner {
        private final Path outputDirectory;

        private StubFormatterProvisioner(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return Collections.singleton(createStubFormatterJar());
        }

        private File createStubFormatterJar() {
            final Path jar = outputDirectory.resolve("stub-eclipse-wtp-formatters.jar");
            if (Files.isRegularFile(jar)) {
                return jar.toFile();
            }
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                for (Map.Entry<String, String> formatterClass : STUB_FORMATTER_CLASSES.entrySet()) {
                    output.putNextEntry(new JarEntry(formatterClass.getKey()));
                    output.write(Base64.getMimeDecoder().decode(formatterClass.getValue()));
                    output.closeEntry();
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to create Eclipse WTP formatter test JAR", exception);
            }
            return jar.toFile();
        }
    }
}
