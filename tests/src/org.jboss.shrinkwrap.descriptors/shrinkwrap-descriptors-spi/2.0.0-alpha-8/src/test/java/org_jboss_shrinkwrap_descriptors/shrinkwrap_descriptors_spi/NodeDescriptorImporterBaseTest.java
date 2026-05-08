/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap_descriptors.shrinkwrap_descriptors_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.shrinkwrap.descriptor.api.DescriptorExportException;
import org.jboss.shrinkwrap.descriptor.spi.node.Node;
import org.jboss.shrinkwrap.descriptor.spi.node.NodeDescriptor;
import org.jboss.shrinkwrap.descriptor.spi.node.NodeDescriptorImporterBase;
import org.jboss.shrinkwrap.descriptor.spi.node.NodeImporter;
import org.junit.jupiter.api.Test;

public class NodeDescriptorImporterBaseTest {
    private static final String DESCRIPTOR_NAME = "test-descriptor.xml";

    @Test
    void fromStreamCreatesDescriptorFromImportedRootNode() {
        TestNodeDescriptorImporter importer = new TestNodeDescriptorImporter();
        ByteArrayInputStream stream = new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8));

        ImportedNodeDescriptor descriptor = importer.fromStream(stream, false);

        assertThat(descriptor.getDescriptorName()).isEqualTo(DESCRIPTOR_NAME);
        assertThat(descriptor.getRootNode().getName()).isEqualTo("root");
        assertThat(descriptor.getRootNode().getText()).isEqualTo("payload");
        assertThat(importer.nodeImporter.streamWasClosed()).isFalse();
    }

    private static final class TestNodeDescriptorImporter extends NodeDescriptorImporterBase<ImportedNodeDescriptor> {
        private final TestNodeImporter nodeImporter = new TestNodeImporter();

        private TestNodeDescriptorImporter() {
            super(ImportedNodeDescriptor.class, DESCRIPTOR_NAME);
        }

        @Override
        public NodeImporter getNodeImporter() {
            return nodeImporter;
        }
    }

    public static final class ImportedNodeDescriptor implements NodeDescriptor {
        private final String descriptorName;
        private final Node rootNode;

        public ImportedNodeDescriptor(String descriptorName, Node rootNode) {
            this.descriptorName = descriptorName;
            this.rootNode = rootNode;
        }

        @Override
        public String getDescriptorName() {
            return descriptorName;
        }

        @Override
        public String exportAsString() throws DescriptorExportException {
            return rootNode.toString(true);
        }

        @Override
        public void exportTo(OutputStream output) throws DescriptorExportException, IllegalArgumentException {
            if (output == null) {
                throw new IllegalArgumentException("Output stream must be specified");
            }
        }

        @Override
        public Node getRootNode() {
            return rootNode;
        }
    }

    private static final class TestNodeImporter implements NodeImporter {
        private boolean streamWasClosed;

        @Override
        public Node importAsNode(InputStream stream, boolean close) throws IllegalArgumentException {
            if (stream == null) {
                throw new IllegalArgumentException("Stream must be specified");
            }
            try {
                byte[] content = stream.readAllBytes();
                if (close) {
                    stream.close();
                    streamWasClosed = true;
                }
                return new Node("root").text(new String(content, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not import stream", e);
            }
        }

        private boolean streamWasClosed() {
            return streamWasClosed;
        }
    }
}
