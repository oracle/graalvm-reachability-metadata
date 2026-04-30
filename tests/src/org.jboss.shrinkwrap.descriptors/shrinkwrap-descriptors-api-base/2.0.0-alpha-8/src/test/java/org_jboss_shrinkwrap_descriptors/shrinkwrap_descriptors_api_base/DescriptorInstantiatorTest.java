/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap_descriptors.shrinkwrap_descriptors_api_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
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
    private static final String CUSTOM_DESCRIPTOR_NAME = "custom-descriptor.xml";
    private static final String IMPORTED_DESCRIPTOR_NAME = "imported-descriptor.xml";
    private static final String DEFAULT_DESCRIPTOR_NAME = "generated-default.xml";
    private static final String IMPORTED_CONTENT = "<descriptor/>";

    @Test
    void createsDescriptorFromConfiguredUserView() throws Exception {
        GeneratedDescriptor descriptor = Descriptors.create(GeneratedDescriptor.class, CUSTOM_DESCRIPTOR_NAME);

        assertThat(descriptor.getDescriptorName()).isEqualTo(CUSTOM_DESCRIPTOR_NAME);
        assertThat(descriptor.exportAsString()).isEqualTo("descriptor:" + CUSTOM_DESCRIPTOR_NAME + ":");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        descriptor.exportTo(output);
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8)).isEqualTo(descriptor.exportAsString());
    }

    @Test
    void createsDescriptorWithConfiguredDefaultName() {
        GeneratedDescriptor descriptor = Descriptors.create(GeneratedDescriptor.class);

        assertThat(descriptor.getDescriptorName()).isEqualTo(DEFAULT_DESCRIPTOR_NAME);
        assertThat(descriptor.importedContent()).isEmpty();
    }

    @Test
    void createsImporterFromConfiguredUserView() throws Exception {
        DescriptorImporter<GeneratedDescriptor> importer = Descriptors.importAs(GeneratedDescriptor.class,
            IMPORTED_DESCRIPTOR_NAME);

        GeneratedDescriptor descriptor = importer.fromString(IMPORTED_CONTENT);

        assertThat(descriptor).isInstanceOf(DescriptorImplementation.class);
        assertThat(descriptor.getDescriptorName()).isEqualTo(IMPORTED_DESCRIPTOR_NAME);
        assertThat(descriptor.importedContent()).isEqualTo(IMPORTED_CONTENT);
        assertThat(descriptor.exportAsString()).isEqualTo("descriptor:" + IMPORTED_DESCRIPTOR_NAME + ":"
            + IMPORTED_CONTENT);
    }

    public static final class DescriptorImplementation implements GeneratedDescriptor {
        private final String descriptorName;
        private final String importedContent;

        public DescriptorImplementation(String descriptorName) {
            this(descriptorName, "");
        }

        private DescriptorImplementation(String descriptorName, String importedContent) {
            this.descriptorName = descriptorName;
            this.importedContent = importedContent;
        }

        @Override
        public String getDescriptorName() {
            return descriptorName;
        }

        @Override
        public String exportAsString() throws DescriptorExportException {
            return "descriptor:" + descriptorName + ":" + importedContent;
        }

        @Override
        public void exportTo(OutputStream output) throws DescriptorExportException, IllegalArgumentException {
            if (output == null) {
                throw new IllegalArgumentException("output must be specified");
            }
            try {
                output.write(exportAsString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new DescriptorExportException("Could not export descriptor", e);
            }
        }

        @Override
        public String importedContent() {
            return importedContent;
        }
    }

    public static final class DescriptorImporterImplementation implements DescriptorImporter<GeneratedDescriptor> {
        private final Class<?> descriptorType;
        private final String descriptorName;

        public DescriptorImporterImplementation(Class<?> descriptorType, String descriptorName) {
            this.descriptorType = descriptorType;
            this.descriptorName = descriptorName;
        }

        @Override
        @Deprecated
        public GeneratedDescriptor from(File file) throws IllegalArgumentException, DescriptorImportException {
            return fromFile(file);
        }

        @Override
        public GeneratedDescriptor fromFile(File file) throws IllegalArgumentException, DescriptorImportException {
            if (file == null) {
                throw new IllegalArgumentException("file must be specified");
            }
            return createDescriptor(file.getPath());
        }

        @Override
        public GeneratedDescriptor fromFile(String file) throws IllegalArgumentException, DescriptorImportException {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("file must be specified");
            }
            return createDescriptor(file);
        }

        @Override
        @Deprecated
        public GeneratedDescriptor from(InputStream in) throws IllegalArgumentException, DescriptorImportException {
            return fromStream(in);
        }

        @Override
        public GeneratedDescriptor fromStream(InputStream in)
            throws IllegalArgumentException, DescriptorImportException {
            return fromStream(in, true);
        }

        @Override
        @Deprecated
        public GeneratedDescriptor from(InputStream in, boolean close)
            throws IllegalArgumentException, DescriptorImportException {
            return fromStream(in, close);
        }

        @Override
        public GeneratedDescriptor fromStream(InputStream in, boolean close)
            throws IllegalArgumentException, DescriptorImportException {
            if (in == null) {
                throw new IllegalArgumentException("input stream must be specified");
            }
            return createDescriptor("stream:" + close);
        }

        @Override
        @Deprecated
        public GeneratedDescriptor from(String string) throws IllegalArgumentException, DescriptorImportException {
            return fromString(string);
        }

        @Override
        public GeneratedDescriptor fromString(String string)
            throws IllegalArgumentException, DescriptorImportException {
            if (string == null || string.isEmpty()) {
                throw new IllegalArgumentException("string must be specified");
            }
            return createDescriptor(string);
        }

        private GeneratedDescriptor createDescriptor(String importedContent) {
            assertThat(descriptorType).isEqualTo(DescriptorImplementation.class);
            return new DescriptorImplementation(descriptorName, importedContent);
        }
    }
}

interface GeneratedDescriptor extends Descriptor {
    String importedContent();
}
