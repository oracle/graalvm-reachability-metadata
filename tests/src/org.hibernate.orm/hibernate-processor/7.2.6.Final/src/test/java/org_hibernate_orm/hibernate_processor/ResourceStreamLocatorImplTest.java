/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_processor;

import org.hibernate.processor.Context;
import org.hibernate.processor.HibernateProcessor;
import org.hibernate.processor.xml.ResourceStreamLocatorImpl;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceStreamLocatorImplTest {
    @Test
    void locatesProcessorServiceResourceFromClasspathWhenFilerCannotProvideIt() throws IOException {
        final ResourceStreamLocatorImpl locator = new ResourceStreamLocatorImpl(
                new Context(new MinimalProcessingEnvironment()));

        try (InputStream stream = locator.locateResourceStream(
                "META-INF/services/javax.annotation.processing.Processor")) {
            assertThat(stream).isNotNull();
            final String serviceDescriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(serviceDescriptor).contains(HibernateProcessor.class.getName());
        }
    }

    private static final class MinimalProcessingEnvironment implements ProcessingEnvironment {
        @Override
        public Map<String, String> getOptions() {
            return Map.of();
        }

        @Override
        public Messager getMessager() {
            return null;
        }

        @Override
        public Filer getFiler() {
            return new ClassOutputUnavailableFiler();
        }

        @Override
        public Elements getElementUtils() {
            return null;
        }

        @Override
        public Types getTypeUtils() {
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public boolean isPreviewEnabled() {
            return false;
        }
    }

    private static final class ClassOutputUnavailableFiler implements Filer {
        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) {
            throw new UnsupportedOperationException("Source file creation is not used by this test");
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) {
            throw new UnsupportedOperationException("Class file creation is not used by this test");
        }

        @Override
        public FileObject createResource(
                JavaFileManager.Location location,
                CharSequence pkg,
                CharSequence relativeName,
                Element... originatingElements) {
            throw new UnsupportedOperationException("Resource creation is not used by this test");
        }

        @Override
        public FileObject getResource(
                JavaFileManager.Location location,
                CharSequence pkg,
                CharSequence relativeName) throws IOException {
            assertThat(location).isEqualTo(StandardLocation.CLASS_OUTPUT);
            throw new FileNotFoundException("The test exercises the classpath fallback");
        }
    }
}
