/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.util.List;

import org.jboss.arquillian.container.spi.client.deployment.DeploymentDescription;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OverProtocol;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.container.test.impl.client.deployment.AnnotationDeploymentScenarioGenerator;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationDeploymentScenarioGeneratorTest {
    @Test
    void generateInvokesStaticArchiveDeploymentMethod() {
        ArchiveDeploymentFixture.invocationCount = 0;

        List<DeploymentDescription> deployments = new AnnotationDeploymentScenarioGenerator()
                .generate(new TestClass(ArchiveDeploymentFixture.class));

        assertThat(ArchiveDeploymentFixture.invocationCount).isEqualTo(1);
        assertThat(deployments).hasSize(1);
        DeploymentDescription deployment = deployments.get(0);
        assertThat(deployment.getName()).isEqualTo("application-archive");
        assertThat(deployment.getArchive()).isSameAs(ArchiveDeploymentFixture.ARCHIVE);
        assertThat(deployment.isArchiveDeployment()).isTrue();
        assertThat(deployment.managed()).isFalse();
        assertThat(deployment.getOrder()).isEqualTo(5);
        assertThat(deployment.getTarget().getName()).isEqualTo("configured-container");
        assertThat(deployment.getProtocol().getName()).isEqualTo("Servlet 3.0");
    }

    public static final class ArchiveDeploymentFixture {
        private static final JavaArchive ARCHIVE = ShrinkWrap.create(JavaArchive.class, "application-archive.jar");
        private static int invocationCount;

        @Deployment(name = "application-archive", managed = false, order = 5)
        @TargetsContainer("configured-container")
        @OverProtocol("Servlet 3.0")
        public static JavaArchive archiveDeployment() {
            invocationCount++;
            return ARCHIVE;
        }
    }
}
