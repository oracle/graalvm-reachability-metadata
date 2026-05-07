/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap_descriptors.shrinkwrap_descriptors_api_base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.descriptor.api.DescriptorExportException;
import org.jboss.shrinkwrap.descriptor.api.DescriptorImportException;
import org.jboss.shrinkwrap.descriptor.api.DescriptorImporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.junit.jupiter.api.Test;

public class DescriptorInstantiatorTest {
    private static final String DEFAULT_DESCRIPTOR_NAME = "default-descriptor-name";
    private static final String CUSTOM_DESCRIPTOR_NAME = "custom-descriptor-name";

    @Test
    void createInstantiatesConfiguredDescriptorImplementation() {
        withDescriptorServiceMapping(() -> {
            TestDescriptor descriptor = Descriptors.create(TestDescriptor.class, CUSTOM_DESCRIPTOR_NAME);

            TestDescriptorImpl implementation = assertInstanceOf(TestDescriptorImpl.class, descriptor);
            assertEquals(CUSTOM_DESCRIPTOR_NAME, implementation.getDescriptorName());
            assertEquals("descriptor:" + CUSTOM_DESCRIPTOR_NAME, implementation.exportAsString());
        });
    }

    @Test
    void importAsInstantiatesConfiguredImporterImplementation() {
        withDescriptorServiceMapping(() -> {
            DescriptorImporter<TestDescriptor> importer = Descriptors.importAs(TestDescriptor.class);

            TestDescriptorImporter implementation = assertInstanceOf(TestDescriptorImporter.class, importer);
            assertSame(TestDescriptorImpl.class, implementation.getDescriptorType());
            TestDescriptor descriptor = importer.fromString("ignored input");
            assertEquals(DEFAULT_DESCRIPTOR_NAME, descriptor.getDescriptorName());
        });
    }

    private static void withDescriptorServiceMapping(Runnable action) {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new DescriptorServiceClassLoader(previousClassLoader));
        try {
            action.run();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    public interface TestDescriptor extends Descriptor {
    }

    public static class TestDescriptorImpl implements TestDescriptor {
        private final String descriptorName;

        public TestDescriptorImpl(String descriptorName) {
            this.descriptorName = descriptorName;
        }

        @Override
        public String getDescriptorName() {
            return descriptorName;
        }

        @Override
        public String exportAsString() throws DescriptorExportException {
            return "descriptor:" + descriptorName;
        }

        @Override
        public void exportTo(OutputStream output) throws DescriptorExportException, IllegalArgumentException {
            if (output == null) {
                throw new IllegalArgumentException("output must be specified");
            }
        }
    }

    public static class TestDescriptorImporter implements DescriptorImporter<TestDescriptor> {
        private final Class<TestDescriptor> descriptorType;
        private final String descriptorName;

        public TestDescriptorImporter(Class<TestDescriptor> descriptorType, String descriptorName) {
            this.descriptorType = descriptorType;
            this.descriptorName = descriptorName;
        }

        Class<TestDescriptor> getDescriptorType() {
            return descriptorType;
        }

        @Override
        public TestDescriptor from(File file) throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor fromFile(File file) throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor fromFile(String file) throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor from(InputStream in) throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor fromStream(InputStream in) throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor from(InputStream in, boolean close)
            throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor fromStream(InputStream in, boolean close)
            throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor from(String string) throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        @Override
        public TestDescriptor fromString(String string) throws IllegalArgumentException, DescriptorImportException {
            return createDescriptor();
        }

        private TestDescriptor createDescriptor() {
            return new TestDescriptorImpl(descriptorName);
        }
    }

    private static final class DescriptorServiceClassLoader extends ClassLoader {
        private static final String SERVICE_RESOURCE_NAME = "META-INF/services/" + TestDescriptor.class.getName();

        private DescriptorServiceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!SERVICE_RESOURCE_NAME.equals(name)) {
                return super.getResourceAsStream(name);
            }
            String properties = """
                implClass=%s
                importerClass=%s
                defaultName=%s
                """.formatted(
                    TestDescriptorImpl.class.getName(),
                    TestDescriptorImporter.class.getName(),
                    DEFAULT_DESCRIPTOR_NAME);
            return new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
        }
    }
}
