/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;

import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.ProtocolDef;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.deployment.Deployment;
import org.jboss.arquillian.container.spi.client.deployment.DeploymentDescription;
import org.jboss.arquillian.container.spi.client.deployment.DeploymentScenario;
import org.jboss.arquillian.container.spi.client.deployment.DeploymentTargetDescription;
import org.jboss.arquillian.container.spi.client.deployment.TargetDescription;
import org.jboss.arquillian.container.spi.client.deployment.Validate;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.NamedContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.RMIContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.container.spi.event.ContainerMultiControlEvent;
import org.jboss.arquillian.container.spi.event.DeployDeployment;
import org.jboss.arquillian.container.spi.event.DeployManagedDeployments;
import org.jboss.arquillian.container.spi.event.KillContainer;
import org.jboss.arquillian.container.spi.event.SetupContainer;
import org.jboss.arquillian.container.spi.event.SetupContainers;
import org.jboss.arquillian.container.spi.event.StartClassContainers;
import org.jboss.arquillian.container.spi.event.StartContainer;
import org.jboss.arquillian.container.spi.event.StartSuiteContainers;
import org.jboss.arquillian.container.spi.event.StopClassContainers;
import org.jboss.arquillian.container.spi.event.StopContainer;
import org.jboss.arquillian.container.spi.event.StopManualContainers;
import org.jboss.arquillian.container.spi.event.StopSuiteContainers;
import org.jboss.arquillian.container.spi.event.UnDeployDeployment;
import org.jboss.arquillian.container.spi.event.UnDeployManagedDeployments;
import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.AfterKill;
import org.jboss.arquillian.container.spi.event.container.AfterSetup;
import org.jboss.arquillian.container.spi.event.container.AfterStart;
import org.jboss.arquillian.container.spi.event.container.AfterStop;
import org.jboss.arquillian.container.spi.event.container.AfterUnDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeKill;
import org.jboss.arquillian.container.spi.event.container.BeforeSetup;
import org.jboss.arquillian.container.spi.event.container.BeforeStart;
import org.jboss.arquillian.container.spi.event.container.BeforeStop;
import org.jboss.arquillian.container.spi.event.container.BeforeUnDeploy;
import org.jboss.arquillian.container.spi.event.container.ContainerEvent;
import org.jboss.arquillian.container.spi.event.container.DeployerEvent;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchiveEventHandler;
import org.jboss.shrinkwrap.api.ArchiveFormat;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Assignable;
import org.jboss.shrinkwrap.api.Filter;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.NamedAsset;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.formatter.Formatter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.descriptor.api.DescriptorExportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Arquillian_container_spiTest {
    @Test
    void deploymentDescriptionSeparatesArchiveAndDescriptorDeployments() {
        RecordingArchive archive = new RecordingArchive("orders.war");
        RecordingArchive testableArchive = new RecordingArchive("orders-testable.war");
        TargetDescription target = new TargetDescription("managed-server");
        ProtocolDescription protocol = new ProtocolDescription("Servlet 3.0");

        DeploymentDescription archiveDeployment = new DeploymentDescription("orders", archive)
                .setOrder(5)
                .setTarget(target)
                .setProtocol(protocol)
                .shouldBeManaged(false)
                .shouldBeTestable(false)
                .setTestableArchive(testableArchive)
                .setExpectedException(IllegalStateException.class);

        assertThat(archiveDeployment.getName()).isEqualTo("orders");
        assertThat(archiveDeployment.getArchive()).isSameAs(archive);
        assertThat(archiveDeployment.getDescriptor()).isNull();
        assertThat(archiveDeployment.isArchiveDeployment()).isTrue();
        assertThat(archiveDeployment.isDescriptorDeployment()).isFalse();
        assertThat(archiveDeployment.getOrder()).isEqualTo(5);
        assertThat(archiveDeployment.getTarget()).isSameAs(target);
        assertThat(archiveDeployment.getProtocol()).isSameAs(protocol);
        assertThat(archiveDeployment.managed()).isFalse();
        assertThat(archiveDeployment.testable()).isFalse();
        assertThat(archiveDeployment.getTestableArchive()).isSameAs(testableArchive);
        assertThat(archiveDeployment.getExpectedException()).isEqualTo(IllegalStateException.class);
        assertThat(archiveDeployment.toString()).isEqualTo("orders");

        RecordingDescriptor descriptor = new RecordingDescriptor("web.xml", "<web-app />");
        DeploymentDescription descriptorDeployment = new DeploymentDescription("descriptor", descriptor)
                .shouldBeManaged(true);

        assertThat(descriptorDeployment.getDescriptor()).isSameAs(descriptor);
        assertThat(descriptorDeployment.getArchive()).isNull();
        assertThat(descriptorDeployment.isDescriptorDeployment()).isTrue();
        assertThat(descriptorDeployment.isArchiveDeployment()).isFalse();
        assertThat(descriptorDeployment.testable()).isTrue();
        assertThatThrownBy(() -> descriptorDeployment.shouldBeTestable(false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non ArchiveDeployment");
    }

    @Test
    void deploymentDescriptionRejectsMissingRequiredConstructorArguments() {
        assertThatThrownBy(() -> new DeploymentDescription(null, new RecordingArchive("test.war")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name must be specified");
        assertThatThrownBy(() -> new DeploymentDescription("missing-archive", (Archive<?>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Archive must be specified");
        assertThatThrownBy(() -> new DeploymentDescription("missing-descriptor", (Descriptor) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Descriptor must be specified");
        DeploymentDescription deployment = new DeploymentDescription("orders", new RecordingArchive("orders.war"));
        assertThatThrownBy(() -> deployment.setTarget(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TargetDescription must be specified");
    }

    @Test
    void deploymentTracksSuccessfulFailedAndUndeployedStates() {
        Deployment deployment = new Deployment(new DeploymentDescription("orders", new RecordingArchive("orders.war")));
        RuntimeException deploymentError = new RuntimeException("boom");

        assertThat(deployment.getDescription().getName()).isEqualTo("orders");
        assertThat(deployment.isDeployed()).isFalse();
        assertThat(deployment.hasDeploymentError()).isFalse();

        deployment.deployed();
        assertThat(deployment.isDeployed()).isTrue();
        assertThat(deployment.hasDeploymentError()).isFalse();

        deployment.undeployed();
        assertThat(deployment.isDeployed()).isFalse();

        deployment.deployedWithError(deploymentError);
        assertThat(deployment.isDeployed()).isTrue();
        assertThat(deployment.hasDeploymentError()).isTrue();
    }

    @Test
    void deploymentScenarioOrdersFiltersAndFindsDeployments() {
        TargetDescription primary = new TargetDescription("primary");
        TargetDescription secondary = new TargetDescription("secondary");
        ProtocolDescription servlet = new ProtocolDescription("Servlet");
        ProtocolDescription custom = new ProtocolDescription("Custom");
        DeploymentDescription first = archiveDeployment("first", "first.war", primary, servlet, 20, true);
        DeploymentDescription second = archiveDeployment("second", "second.war", primary, custom, 10, true);
        DeploymentDescription manual = archiveDeployment("manual", "manual.war", secondary, servlet, 30, false);
        DeploymentScenario scenario = new DeploymentScenario()
                .addDeployment(first)
                .addDeployment(second)
                .addDeployment(manual);

        scenario.deployment(new DeploymentTargetDescription("manual")).deployed();
        Deployment secondDeployment = scenario.deployment(new DeploymentTargetDescription("second"));
        secondDeployment.deployedWithError(new DeploymentException("failed"));

        assertThat(scenario.deployments()).hasSize(3);
        assertThat(scenario.targets()).containsExactlyInAnyOrder(primary, secondary);
        assertThat(scenario.protocols()).containsExactlyInAnyOrder(servlet, custom);
        assertThat(scenario.managedDeploymentsInDeployOrder())
                .extracting(deployment -> deployment.getDescription().getName())
                .containsExactly("second", "first");
        assertThat(scenario.deployedDeploymentsInUnDeployOrder())
                .extracting(deployment -> deployment.getDescription().getName())
                .containsExactly("manual", "first", "second");
        assertThat(scenario.startupDeploymentsFor(primary))
                .extracting(deployment -> deployment.getDescription().getName())
                .containsExactly("second", "first");
        assertThat(scenario.startupDeploymentsFor(secondary)).isEmpty();
        assertThat(scenario.deployedDeployments())
                .extracting(deployment -> deployment.getDescription().getName())
                .containsExactlyInAnyOrder("second", "manual");
        assertThat(scenario.deploymentsInError())
                .extracting(deployment -> deployment.getDescription().getName())
                .containsExactly("second");
        assertThat(scenario.deployment(new DeploymentTargetDescription("first")).getDescription()).isSameAs(first);
        assertThat(scenario.deployment(new DeploymentTargetDescription("missing"))).isNull();
        assertThatThrownBy(() -> scenario.deployment(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploymentScenarioDefaultSelectionSupportsArchiveAndManagedFallbacks() {
        DeploymentScenario single = new DeploymentScenario()
                .addDeployment(new DeploymentDescription("only", new RecordingDescriptor("only.xml", "<descriptor/>")));
        assertThat(single.deployment(DeploymentTargetDescription.DEFAULT).getDescription().getName()).isEqualTo("only");

        RecordingDescriptor webDescriptor = new RecordingDescriptor("web.xml", "<web-app/>");
        DeploymentScenario oneArchive = new DeploymentScenario()
                .addDeployment(new DeploymentDescription("descriptor", webDescriptor))
                .addDeployment(new DeploymentDescription("archive", new RecordingArchive("archive.war")));
        assertThat(oneArchive.deployment(DeploymentTargetDescription.DEFAULT).getDescription().getName())
                .isEqualTo("archive");

        DeploymentScenario oneManaged = new DeploymentScenario()
                .addDeployment(new DeploymentDescription("manual-one", new RecordingArchive("manual-one.war"))
                        .shouldBeManaged(false))
                .addDeployment(new DeploymentDescription("manual-two", new RecordingArchive("manual-two.war"))
                        .shouldBeManaged(false))
                .addDeployment(new DeploymentDescription("managed", new RecordingArchive("managed.war")));
        assertThat(oneManaged.deployment(DeploymentTargetDescription.DEFAULT).getDescription().getName())
                .isEqualTo("managed");

        DeploymentScenario defaultNamed = new DeploymentScenario()
                .addDeployment(new DeploymentDescription("orders", new RecordingArchive("orders.war"))
                        .shouldBeManaged(false))
                .addDeployment(new DeploymentDescription(DeploymentTargetDescription.DEFAULT.getName(),
                        new RecordingArchive("default.war")).shouldBeManaged(false));
        assertThat(defaultNamed.deployment(DeploymentTargetDescription.DEFAULT).getDescription().getName())
                .isEqualTo(DeploymentTargetDescription.DEFAULT.getName());
    }

    @Test
    void deploymentScenarioRejectsAmbiguousDuplicateDeployments() {
        TargetDescription primary = new TargetDescription("primary");
        TargetDescription secondary = new TargetDescription("secondary");
        DeploymentScenario scenario = new DeploymentScenario()
                .addDeployment(archiveDeployment("orders", "orders.war", primary,
                        ProtocolDescription.DEFAULT, 0, true));

        assertThatThrownBy(() -> scenario.addDeployment(archiveDeployment("orders", "orders-copy.war", secondary,
                ProtocolDescription.DEFAULT, 0, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same name");
        assertThatThrownBy(() -> scenario.addDeployment(archiveDeployment("billing", "orders.war", primary,
                ProtocolDescription.DEFAULT, 0, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same archive name");
    }

    @Test
    void deploymentScenarioAllowsSameDeploymentNameWhenPayloadTypesDiffer() {
        RecordingDescriptor descriptor = new RecordingDescriptor("mixed.xml", "<descriptor/>");
        DeploymentScenario scenario = new DeploymentScenario()
                .addDeployment(new DeploymentDescription("mixed", descriptor))
                .addDeployment(new DeploymentDescription("mixed", new RecordingArchive("mixed.war")));

        assertThat(scenario.deployment(new DeploymentTargetDescription("mixed")).getDescription().isArchiveDeployment())
                .isTrue();
    }

    @Test
    void protocolMetadataCollectsTypedHttpRmiAndJmxContexts() {
        HTTPContext httpContext = new HTTPContext("public", "127.0.0.1", 8080);
        Servlet rootServlet = new Servlet("RootServlet", "/");
        Servlet apiServlet = new Servlet("ApiServlet", "api");
        httpContext.add(rootServlet).add(apiServlet);
        RMIContext rmiContext = new RMIContext("registry", "127.0.0.1", 1099);
        MBeanServerConnection connection = ManagementFactory.getPlatformMBeanServer();
        JMXContext jmxContext = new JMXContext("platform", connection);

        ProtocolMetaData metaData = new ProtocolMetaData()
                .addContext(httpContext)
                .addContext(rmiContext)
                .addContext(jmxContext)
                .addContext("custom-context");

        assertThat(metaData.hasContext(HTTPContext.class)).isTrue();
        assertThat(metaData.hasContext(NamedContext.class)).isTrue();
        assertThat(metaData.hasContext(Integer.class)).isFalse();
        assertThat(metaData.getContexts(HTTPContext.class)).containsExactly(httpContext);
        assertThat(metaData.getContexts(NamedContext.class))
                .containsExactlyInAnyOrder(httpContext, rmiContext, jmxContext);
        assertThat(metaData.getContexts()).containsExactly(httpContext, rmiContext, jmxContext, "custom-context");
        assertThatThrownBy(() -> metaData.getContexts().add(new Object()))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(httpContext.getName()).isEqualTo("public");
        assertThat(httpContext.getHost()).isEqualTo("127.0.0.1");
        assertThat(httpContext.getPort()).isEqualTo(8080);
        assertThat(httpContext.getServlets()).containsExactlyInAnyOrder(rootServlet, apiServlet);
        assertThat(httpContext.getServletByName("ApiServlet")).isSameAs(apiServlet);
        assertThat(httpContext.getServletByName("missing")).isNull();
        assertThat(rootServlet.getContextRoot()).isEmpty();
        assertThat(rootServlet.getBaseURI().toString()).isEqualTo("http://127.0.0.1:8080/");
        assertThat(rootServlet.getFullURI().toString()).isEqualTo("http://127.0.0.1:8080/RootServlet");
        assertThat(apiServlet.getContextRoot()).isEqualTo("/api");
        assertThat(apiServlet.getFullURI().toString()).isEqualTo("http://127.0.0.1:8080/api/ApiServlet");
        assertThat(rmiContext.getName()).isEqualTo("registry");
        assertThat(rmiContext.getIp()).isEqualTo("127.0.0.1");
        assertThat(rmiContext.getPort()).isEqualTo(1099);
        assertThat(rmiContext.getInitialContext()).isNull();
        assertThat(jmxContext.getName()).isEqualTo("platform");
        assertThat(jmxContext.getConnection()).isSameAs(connection);
        assertThat(metaData.toString()).contains("HTTPContext", "RMIContext", "JMXContext", "custom-context");
    }

    @Test
    void servletEqualityRequiresAttachedHttpContext() {
        Servlet unattached = new Servlet("Orders", "orders");
        HTTPContext firstContext = new HTTPContext("localhost", 8080);
        HTTPContext sameContext = new HTTPContext("localhost", 8080);
        HTTPContext otherContext = new HTTPContext("localhost", 8181);
        Servlet first = new Servlet("Orders", "/orders");
        Servlet same = new Servlet("Orders", "orders");
        Servlet other = new Servlet("Orders", "orders");

        assertThatThrownBy(unattached::hashCode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("host must not be null");

        firstContext.add(first);
        sameContext.add(same);
        otherContext.add(other);

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(other);
        assertThat(first).isNotEqualTo("Orders");
        assertThat(first.toString()).contains("Orders", "/orders");
        assertThatThrownBy(() -> new HTTPContext(null, 8080)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Servlet(null, "/orders")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Servlet("Orders", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptionsUseNamesForEqualityAndHashing() {
        TargetDescription primary = new TargetDescription("primary");
        TargetDescription samePrimary = new TargetDescription("primary");
        TargetDescription secondary = new TargetDescription("secondary");
        DeploymentTargetDescription deployment = new DeploymentTargetDescription("orders");
        DeploymentTargetDescription sameDeployment = new DeploymentTargetDescription("orders");
        ProtocolDescription protocol = new ProtocolDescription("Servlet");
        ProtocolDescription sameProtocol = new ProtocolDescription("Servlet");

        assertThat(primary).isEqualTo(samePrimary).hasSameHashCodeAs(samePrimary);
        assertThat(primary).isNotEqualTo(secondary).isNotEqualTo(deployment);
        assertThat(primary.toString()).isEqualTo("primary");
        assertThat(TargetDescription.DEFAULT.getName()).isEqualTo("_DEFAULT_");

        assertThat(deployment).isEqualTo(sameDeployment).hasSameHashCodeAs(sameDeployment);
        assertThat(deployment).isNotEqualTo(new DeploymentTargetDescription("billing")).isNotEqualTo(primary);
        assertThat(deployment.toString()).isEqualTo("orders");
        assertThat(DeploymentTargetDescription.DEFAULT.getName()).isEqualTo("_DEFAULT_");

        assertThat(protocol).isEqualTo(sameProtocol).hasSameHashCodeAs(sameProtocol);
        assertThat(protocol).isNotEqualTo(new ProtocolDescription("Custom")).isNotEqualTo(primary);
        assertThat(protocol.getName()).isEqualTo("Servlet");
        assertThat(ProtocolDescription.DEFAULT.getName()).isEqualTo("_DEFAULT_");
        assertThatThrownBy(() -> new ProtocolDescription(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containerEventsExposeContainersDeploymentsAndDeployableContainers() {
        RecordingDeployableContainer deployableContainer = new RecordingDeployableContainer();
        RecordingContainer container = new RecordingContainer("managed-server", deployableContainer);
        Deployment deployment = new Deployment(new DeploymentDescription("orders", new RecordingArchive("orders.war")));

        assertContainerControlEvent(new SetupContainer(container), container);
        assertContainerControlEvent(new StartContainer(container), container);
        assertContainerControlEvent(new StopContainer(container), container);
        assertContainerControlEvent(new KillContainer(container), container);

        DeployDeployment deployDeployment = new DeployDeployment(container, deployment);
        UnDeployDeployment unDeployDeployment = new UnDeployDeployment(container, deployment);
        assertThat(deployDeployment.getDeployment()).isSameAs(deployment);
        assertThat(deployDeployment.getContainer()).isSameAs(container);
        assertThat(deployDeployment.getDeployableContainer()).isSameAs(deployableContainer);
        assertThat(unDeployDeployment.getDeployment()).isSameAs(deployment);
        assertThat(unDeployDeployment.getContainerName()).isEqualTo("managed-server");
        assertThat(unDeployDeployment.getDeployableContainer()).isSameAs(deployableContainer);
    }

    @Test
    void deployableContainerEventsExposeContainerAndDeploymentDescriptions() {
        RecordingDeployableContainer deployableContainer = new RecordingDeployableContainer();
        DeploymentDescription deployment = new DeploymentDescription("orders", new RecordingArchive("orders.war"));

        assertContainerEvent(new BeforeSetup(deployableContainer), deployableContainer);
        assertContainerEvent(new AfterSetup(deployableContainer), deployableContainer);
        assertContainerEvent(new BeforeStart(deployableContainer), deployableContainer);
        assertContainerEvent(new AfterStart(deployableContainer), deployableContainer);
        assertContainerEvent(new BeforeStop(deployableContainer), deployableContainer);
        assertContainerEvent(new AfterStop(deployableContainer), deployableContainer);
        assertContainerEvent(new BeforeKill(deployableContainer), deployableContainer);
        assertContainerEvent(new AfterKill(deployableContainer), deployableContainer);

        assertDeployerEvent(new BeforeDeploy(deployableContainer, deployment), deployableContainer, deployment);
        assertDeployerEvent(new AfterDeploy(deployableContainer, deployment), deployableContainer, deployment);
        assertDeployerEvent(new BeforeUnDeploy(deployableContainer, deployment), deployableContainer, deployment);
        assertDeployerEvent(new AfterUnDeploy(deployableContainer, deployment), deployableContainer, deployment);

        assertThatThrownBy(() -> new ContainerEvent(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeployerEvent(deployableContainer, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiControlEventsAreMarkerCommandsForBulkContainerActions() {
        assertThat(new SetupContainers()).isInstanceOf(ContainerMultiControlEvent.class);
        assertThat(new StartClassContainers()).isInstanceOf(ContainerMultiControlEvent.class);
        assertThat(new StartSuiteContainers()).isInstanceOf(ContainerMultiControlEvent.class);
        assertThat(new StopClassContainers()).isInstanceOf(ContainerMultiControlEvent.class);
        assertThat(new StopSuiteContainers()).isInstanceOf(ContainerMultiControlEvent.class);
        assertThat(new StopManualContainers()).isInstanceOf(ContainerMultiControlEvent.class);
        assertThat(new DeployManagedDeployments()).isInstanceOf(ContainerMultiControlEvent.class);
        assertThat(new UnDeployManagedDeployments()).isInstanceOf(ContainerMultiControlEvent.class);
    }

    @Test
    void deployableContainerAndContainerContractsCanCoordinateLifecycle() throws Exception {
        RecordingDeployableContainer deployableContainer = new RecordingDeployableContainer();
        RecordingContainer container = new RecordingContainer("managed-server", deployableContainer);
        SimpleConfiguration configuration = container.createDeployableConfiguration();
        RecordingArchive archive = new RecordingArchive("orders.war");
        RecordingDescriptor descriptor = new RecordingDescriptor("web.xml", "<web-app />");

        configuration.validate();
        container.setup();
        container.start();
        ProtocolMetaData metaData = deployableContainer.deploy(archive);
        deployableContainer.deploy(descriptor);
        deployableContainer.undeploy(archive);
        deployableContainer.undeploy(descriptor);
        container.stop();
        container.kill();

        assertThat(deployableContainer.getConfigurationClass()).isEqualTo(SimpleConfiguration.class);
        assertThat(deployableContainer.getDefaultProtocol()).isEqualTo(ProtocolDescription.DEFAULT);
        assertThat(metaData.hasContext(HTTPContext.class)).isTrue();
        assertThat(container.getName()).isEqualTo("managed-server");
        assertThat(container.getDeployableContainer()).isSameAs(deployableContainer);
        assertThat(container.getContainerConfiguration()).isNull();
        assertThat(container.hasProtocolConfiguration(ProtocolDescription.DEFAULT)).isFalse();
        assertThat(container.getProtocolConfiguration(ProtocolDescription.DEFAULT)).isNull();
        assertThat(container.getState()).isEqualTo(Container.State.KILLED);
        assertThat(container.getFailureCause()).isNull();
        assertThat(deployableContainer.events).containsExactly(
                "setup:validated=false",
                "start",
                "deploy-archive:orders.war",
                "deploy-descriptor:web.xml",
                "undeploy-archive:orders.war",
                "undeploy-descriptor:web.xml",
                "stop",
                "kill");
    }

    @Test
    void validateUtilityRecognizesArchiveTypesFromStandardFileExtensions() {
        RecordingArchive javaArchive = new RecordingArchive("library.jar");
        RecordingArchive webArchive = new RecordingArchive("orders.war");
        RecordingArchive enterpriseArchive = new RecordingArchive("suite.ear");
        RecordingArchive resourceAdapterArchive = new RecordingArchive("connector.rar");
        RecordingArchive unknownArchive = new RecordingArchive("notes.txt");

        assertThat(Validate.getArchiveExpression(JavaArchive.class)).isEqualTo(".jar");
        assertThat(Validate.getArchiveExpression(WebArchive.class)).isEqualTo(".war");
        assertThat(Validate.getArchiveExpression(EnterpriseArchive.class)).isEqualTo(".ear");
        assertThat(Validate.getArchiveExpression(ResourceAdapterArchive.class)).isEqualTo(".rar");
        assertThat(Validate.getArchiveExpression(RecordingArchive.class)).isNull();

        assertThat(Validate.isArchiveOfType(JavaArchive.class, javaArchive)).isTrue();
        assertThat(Validate.isArchiveOfType(WebArchive.class, webArchive)).isTrue();
        assertThat(Validate.isArchiveOfType(EnterpriseArchive.class, enterpriseArchive)).isTrue();
        assertThat(Validate.isArchiveOfType(ResourceAdapterArchive.class, resourceAdapterArchive)).isTrue();
        assertThat(Validate.isArchiveOfType(JavaArchive.class, webArchive)).isFalse();
        assertThat(Validate.isArchiveOfType(WebArchive.class, unknownArchive)).isFalse();
        assertThat(Validate.isArchiveOfType(RecordingArchive.class, javaArchive)).isFalse();
    }

    @Test
    void validateUtilityRequiresExistingConfigurationDirectory(@TempDir Path configurationDirectory) throws Exception {
        String failureMessage = "Configuration directory is required";
        Path regularFile = Files.createFile(configurationDirectory.resolve("container.conf"));
        Path missingDirectory = configurationDirectory.resolve("missing");

        Validate.configurationDirectoryExists(configurationDirectory.toString(), failureMessage);

        assertThatThrownBy(() -> Validate.configurationDirectoryExists(null, failureMessage))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(failureMessage);
        assertThatThrownBy(() -> Validate.configurationDirectoryExists("", failureMessage))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(failureMessage);
        assertThatThrownBy(() -> Validate.configurationDirectoryExists(regularFile.toString(), failureMessage))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(failureMessage);
        assertThatThrownBy(() -> Validate.configurationDirectoryExists(missingDirectory.toString(), failureMessage))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(failureMessage);
    }

    @Test
    void configurationAndSpiExceptionsExposeMessagesAndCauses() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid port");

        assertThatThrownBy(() -> new SimpleConfiguration(true).validate())
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Configuration is invalid");
        assertThat(new ConfigurationException("invalid")).hasMessage("invalid");
        assertThat(new ConfigurationException(cause)).hasCause(cause);
        assertThat(new ConfigurationException("invalid", cause)).hasMessage("invalid").hasCause(cause);
        assertThat(new LifecycleException("start failed")).hasMessage("start failed");
        assertThat(new LifecycleException("start failed", cause)).hasMessage("start failed").hasCause(cause);
        assertThat(new DeploymentException("deploy failed")).hasMessage("deploy failed");
        assertThat(new DeploymentException("deploy failed", cause)).hasMessage("deploy failed").hasCause(cause);
    }

    private static DeploymentDescription archiveDeployment(String name, String archiveName, TargetDescription target,
            ProtocolDescription protocol, int order, boolean managed) {
        return new DeploymentDescription(name, new RecordingArchive(archiveName))
                .setTarget(target)
                .setProtocol(protocol)
                .setOrder(order)
                .shouldBeManaged(managed);
    }

    private static void assertContainerControlEvent(SetupContainer event, Container container) {
        assertThat(event.getContainer()).isSameAs(container);
        assertThat(event.getContainerName()).isEqualTo(container.getName());
    }

    private static void assertContainerControlEvent(StartContainer event, Container container) {
        assertThat(event.getContainer()).isSameAs(container);
        assertThat(event.getContainerName()).isEqualTo(container.getName());
    }

    private static void assertContainerControlEvent(StopContainer event, Container container) {
        assertThat(event.getContainer()).isSameAs(container);
        assertThat(event.getContainerName()).isEqualTo(container.getName());
    }

    private static void assertContainerControlEvent(KillContainer event, Container container) {
        assertThat(event.getContainer()).isSameAs(container);
        assertThat(event.getContainerName()).isEqualTo(container.getName());
    }

    private static void assertContainerEvent(ContainerEvent event, DeployableContainer<?> deployableContainer) {
        assertThat(event.getDeployableContainer()).isSameAs(deployableContainer);
    }

    private static void assertDeployerEvent(DeployerEvent event, DeployableContainer<?> deployableContainer,
            DeploymentDescription deployment) {
        assertThat(event.getDeployableContainer()).isSameAs(deployableContainer);
        assertThat(event.getDeployment()).isSameAs(deployment);
    }

    private static final class SimpleConfiguration implements ContainerConfiguration {
        private final boolean failValidation;
        private boolean validated;

        SimpleConfiguration() {
            this(false);
        }

        SimpleConfiguration(boolean failValidation) {
            this.failValidation = failValidation;
        }

        @Override
        public void validate() throws ConfigurationException {
            if (failValidation) {
                throw new ConfigurationException("Configuration is invalid");
            }
            validated = true;
        }
    }

    private static final class RecordingDeployableContainer implements DeployableContainer<SimpleConfiguration> {
        private final List<String> events = new ArrayList<String>();
        private SimpleConfiguration configuration;

        @Override
        public Class<SimpleConfiguration> getConfigurationClass() {
            return SimpleConfiguration.class;
        }

        @Override
        public void setup(SimpleConfiguration configuration) {
            this.configuration = configuration;
            events.add("setup:validated=" + configuration.validated);
        }

        @Override
        public void start() throws LifecycleException {
            events.add("start");
        }

        @Override
        public void stop() throws LifecycleException {
            events.add("stop");
        }

        @Override
        public ProtocolDescription getDefaultProtocol() {
            return ProtocolDescription.DEFAULT;
        }

        @Override
        public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
            events.add("deploy-archive:" + archive.getName());
            return new ProtocolMetaData().addContext(new HTTPContext("localhost", 8080));
        }

        @Override
        public void undeploy(Archive<?> archive) throws DeploymentException {
            events.add("undeploy-archive:" + archive.getName());
        }

        @Override
        public void deploy(Descriptor descriptor) throws DeploymentException {
            events.add("deploy-descriptor:" + descriptor.getDescriptorName());
        }

        @Override
        public void undeploy(Descriptor descriptor) throws DeploymentException {
            events.add("undeploy-descriptor:" + descriptor.getDescriptorName());
        }
    }

    private static final class RecordingContainer implements Container {
        private final String name;
        private final RecordingDeployableContainer deployableContainer;
        private Container.State state = Container.State.STOPPED;
        private Throwable failureCause;

        RecordingContainer(String name, RecordingDeployableContainer deployableContainer) {
            this.name = name;
            this.deployableContainer = deployableContainer;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public DeployableContainer<?> getDeployableContainer() {
            return deployableContainer;
        }

        @Override
        public ContainerDef getContainerConfiguration() {
            return null;
        }

        @Override
        public SimpleConfiguration createDeployableConfiguration() throws Exception {
            return new SimpleConfiguration();
        }

        @Override
        public boolean hasProtocolConfiguration(ProtocolDescription protocolDescription) {
            return false;
        }

        @Override
        public ProtocolDef getProtocolConfiguration(ProtocolDescription protocolDescription) {
            return null;
        }

        @Override
        public void setup() throws Exception {
            SimpleConfiguration configuration = createDeployableConfiguration();
            deployableContainer.setup(configuration);
            state = Container.State.SETUP;
        }

        @Override
        public void start() throws LifecycleException {
            try {
                deployableContainer.start();
                state = Container.State.STARTED;
            } catch (LifecycleException e) {
                failureCause = e;
                state = Container.State.STARTED_FAILED;
                throw e;
            }
        }

        @Override
        public void stop() throws LifecycleException {
            try {
                deployableContainer.stop();
                state = Container.State.STOPPED;
            } catch (LifecycleException e) {
                failureCause = e;
                state = Container.State.STOPPED_FAILED;
                throw e;
            }
        }

        @Override
        public void kill() throws Exception {
            deployableContainer.events.add("kill");
            state = Container.State.KILLED;
        }

        @Override
        public Container.State getState() {
            return state;
        }

        @Override
        public Throwable getFailureCause() {
            return failureCause;
        }

        @Override
        public void setState(Container.State state) {
            this.state = state;
        }
    }

    private static final class RecordingDescriptor implements Descriptor {
        private final String name;
        private final String body;

        RecordingDescriptor(String name, String body) {
            this.name = name;
            this.body = body;
        }

        @Override
        public String getDescriptorName() {
            return name;
        }

        @Override
        public String exportAsString() throws DescriptorExportException {
            return body;
        }

        @Override
        public void exportTo(OutputStream outputStream) throws DescriptorExportException, IllegalArgumentException {
            try {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new DescriptorExportException("Could not export descriptor", e);
            }
        }
    }

    private static final class RecordingArchive implements Archive<RecordingArchive> {
        private final String name;

        RecordingArchive(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getId() {
            return name;
        }

        @Override
        public RecordingArchive add(Asset asset, ArchivePath target) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Asset asset, ArchivePath target, String name) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Asset asset, String target, String name) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(NamedAsset asset) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Asset asset, String target) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectory(String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectories(String... paths) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectory(ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectories(ArchivePath... paths) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addHandlers(ArchiveEventHandler... handlers) {
            return unsupported();
        }

        @Override
        public Node get(ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public Node get(String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, String path) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, ArchivePath path) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> Collection<X> getAsType(Class<X> type, Filter<ArchivePath> filter) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, String path, ArchiveFormat archiveFormat) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, ArchivePath path, ArchiveFormat archiveFormat) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> Collection<X> getAsType(
                Class<X> type, Filter<ArchivePath> filter, ArchiveFormat archiveFormat) {
            return unsupported();
        }

        @Override
        public boolean contains(ArchivePath path) throws IllegalArgumentException {
            return false;
        }

        @Override
        public boolean contains(String path) throws IllegalArgumentException {
            return false;
        }

        @Override
        public Node delete(ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public Node delete(String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public Map<ArchivePath, Node> getContent() {
            return Collections.emptyMap();
        }

        @Override
        public Map<ArchivePath, Node> getContent(Filter<ArchivePath> filter) {
            return Collections.emptyMap();
        }

        @Override
        public RecordingArchive filter(Filter<ArchivePath> filter) {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Archive<?> archive, ArchivePath path, Class<? extends StreamExporter> exporter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Archive<?> archive, String path, Class<? extends StreamExporter> exporter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, Filter<ArchivePath> filter) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, ArchivePath path, Filter<ArchivePath> filter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, String path, Filter<ArchivePath> filter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive move(ArchivePath source, ArchivePath target) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive move(String source, String target) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public String toString(boolean verbose) {
            return name;
        }

        @Override
        public String toString(Formatter formatter) throws IllegalArgumentException {
            return name;
        }

        @Override
        public void writeTo(OutputStream outputStream, Formatter formatter) throws IllegalArgumentException {
            throw new UnsupportedOperationException("RecordingArchive only exposes an archive name");
        }

        @Override
        public Archive<RecordingArchive> shallowCopy() {
            return unsupported();
        }

        @Override
        public Archive<RecordingArchive> shallowCopy(Filter<ArchivePath> filter) {
            return unsupported();
        }

        @Override
        public <TYPE extends Assignable> TYPE as(Class<TYPE> type) {
            return unsupported();
        }

        private static <T> T unsupported() {
            throw new UnsupportedOperationException("RecordingArchive only exposes an archive name");
        }
    }
}
