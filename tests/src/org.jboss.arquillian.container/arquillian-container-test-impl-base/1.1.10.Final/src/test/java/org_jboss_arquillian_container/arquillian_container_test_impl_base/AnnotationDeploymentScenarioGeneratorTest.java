/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jboss.arquillian.container.spi.client.deployment.DeploymentDescription;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OverProtocol;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.container.test.impl.client.deployment.AnnotationDeploymentScenarioGenerator;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.descriptor.api.DescriptorExportException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationDeploymentScenarioGeneratorTest {
    @Test
    void generateInvokesStaticDescriptorDeploymentMethod() {
        DescriptorDeploymentFixture.invocationCount = 0;

        List<DeploymentDescription> deployments = new AnnotationDeploymentScenarioGenerator()
                .generate(new TestClass(DescriptorDeploymentFixture.class));

        assertThat(DescriptorDeploymentFixture.invocationCount).isEqualTo(1);
        assertThat(deployments).hasSize(1);
        DeploymentDescription deployment = deployments.get(0);
        assertThat(deployment.getName()).isEqualTo("application-descriptor");
        assertThat(deployment.getDescriptor()).isSameAs(DescriptorDeploymentFixture.DESCRIPTOR);
        assertThat(deployment.isDescriptorDeployment()).isTrue();
        assertThat(deployment.managed()).isFalse();
        assertThat(deployment.getOrder()).isEqualTo(5);
        assertThat(deployment.getTarget().getName()).isEqualTo("configured-container");
        assertThat(deployment.getProtocol().getName()).isEqualTo("Servlet 3.0");
    }

    public static final class DescriptorDeploymentFixture {
        private static final Descriptor DESCRIPTOR = new SimpleDescriptor("test-web.xml");
        private static int invocationCount;

        @Deployment(name = "application-descriptor", managed = false, order = 5)
        @TargetsContainer("configured-container")
        @OverProtocol("Servlet 3.0")
        public static Descriptor descriptorDeployment() {
            invocationCount++;
            return DESCRIPTOR;
        }
    }

    private static final class SimpleDescriptor implements Descriptor {
        private final String name;

        private SimpleDescriptor(String name) {
            this.name = name;
        }

        @Override
        public String getDescriptorName() {
            return name;
        }

        @Override
        public String exportAsString() {
            return "<web-app/>";
        }

        @Override
        public void exportTo(OutputStream outputStream) {
            try {
                outputStream.write(exportAsString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new DescriptorExportException("Could not write descriptor", e);
            }
        }
    }
}
