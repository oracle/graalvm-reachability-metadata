/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.util.Collections;
import java.util.List;

import org.jboss.arquillian.container.spi.client.deployment.DeploymentDescription;
import org.jboss.arquillian.container.test.api.BeforeDeployment;
import org.jboss.arquillian.container.test.api.DeploymentConfiguration;
import org.jboss.arquillian.container.test.impl.client.deployment.AutomaticDeploymentScenarioGenerator;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutomaticDeploymentScenarioGeneratorTest {
    private static final String DEPLOYMENT_NAME = "automatic-deployment";

    @Test
    void generateInvokesMatchingBeforeDeploymentMethod() {
        BeforeDeploymentFixture.invocationCount = 0;
        JavaArchive originalArchive = ShrinkWrap.create(JavaArchive.class, "original.jar");
        JavaArchive preparedArchive = ShrinkWrap.create(JavaArchive.class, "prepared.jar");
        BeforeDeploymentFixture.preparedArchive = preparedArchive;

        List<DeploymentDescription> deployments = new FixedDeploymentGenerator(originalArchive)
                .generate(new TestClass(BeforeDeploymentFixture.class));

        assertThat(BeforeDeploymentFixture.invocationCount).isEqualTo(1);
        assertThat(BeforeDeploymentFixture.receivedArchive).isSameAs(originalArchive);
        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0).getArchive()).isSameAs(preparedArchive);
    }

    private static final class FixedDeploymentGenerator extends AutomaticDeploymentScenarioGenerator {
        private final Archive<?> archive;

        private FixedDeploymentGenerator(Archive<?> archive) {
            this.archive = archive;
        }

        @Override
        protected List<DeploymentConfiguration> generateDeploymentContent(TestClass testClass) {
            DeploymentConfiguration.DeploymentContentBuilder deploymentContentBuilder =
                    new DeploymentConfiguration.DeploymentContentBuilder(archive)
                            .withDeployment()
                            .withName(DEPLOYMENT_NAME)
                            .build();
            return Collections.singletonList(deploymentContentBuilder.get());
        }
    }

    public static final class BeforeDeploymentFixture {
        private static Archive<?> preparedArchive;
        private static Archive<?> receivedArchive;
        private static int invocationCount;

        @BeforeDeployment(name = DEPLOYMENT_NAME)
        public static Archive<?> prepareDeployment(Archive<?> archive) {
            invocationCount++;
            receivedArchive = archive;
            return preparedArchive;
        }
    }
}
