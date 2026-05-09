/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_webapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.Descriptor;
import org.eclipse.jetty.webapp.IterativeDescriptorProcessor;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IterativeDescriptorProcessorTest {
    @Test
    void invokesRegisteredVisitorForMatchingDescriptorNode(@TempDir Path temporaryDirectory) throws Exception {
        Path descriptorPath = temporaryDirectory.resolve("web.xml");
        Files.writeString(descriptorPath, """
                <web-app>
                  <observed-node>value</observed-node>
                  <ignored-node>other</ignored-node>
                </web-app>
                """);
        TestDescriptor descriptor = new TestDescriptor(Resource.newResource(descriptorPath.toUri()));
        descriptor.parse();
        RecordingDescriptorProcessor processor = new RecordingDescriptorProcessor();
        processor.registerVisitor("observed-node", RecordingDescriptorProcessor.class.getMethod(
                "visitObservedNode", IterativeDescriptorProcessor.__signature));

        processor.process(null, descriptor);

        assertThat(processor.isStarted()).isTrue();
        assertThat(processor.isEnded()).isTrue();
        assertThat(processor.getVisitedNodeTag()).isEqualTo("observed-node");
        assertThat(processor.getVisitedDescriptor()).isSameAs(descriptor);
    }

    public static final class RecordingDescriptorProcessor extends IterativeDescriptorProcessor {
        private boolean started;
        private boolean ended;
        private Descriptor visitedDescriptor;
        private String visitedNodeTag;

        @Override
        public void start(WebAppContext context, Descriptor descriptor) {
            started = true;
        }

        @Override
        public void end(WebAppContext context, Descriptor descriptor) {
            ended = true;
        }

        public void visitObservedNode(WebAppContext context, Descriptor descriptor, XmlParser.Node node) {
            visitedDescriptor = descriptor;
            visitedNodeTag = node.getTag();
        }

        boolean isStarted() {
            return started;
        }

        boolean isEnded() {
            return ended;
        }

        Descriptor getVisitedDescriptor() {
            return visitedDescriptor;
        }

        String getVisitedNodeTag() {
            return visitedNodeTag;
        }
    }

    private static final class TestDescriptor extends Descriptor {
        private TestDescriptor(Resource xml) {
            super(xml);
        }

        @Override
        public void ensureParser() {
            _parser = new XmlParser(false);
        }
    }
}
