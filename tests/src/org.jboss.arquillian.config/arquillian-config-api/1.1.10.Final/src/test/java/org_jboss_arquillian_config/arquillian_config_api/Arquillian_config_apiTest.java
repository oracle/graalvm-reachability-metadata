/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_config.arquillian_config_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.DefaultProtocolDef;
import org.jboss.arquillian.config.descriptor.api.EngineDef;
import org.jboss.arquillian.config.descriptor.api.ExtensionDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.config.descriptor.api.ProtocolDef;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.junit.jupiter.api.Test;

public class Arquillian_config_apiTest {
    @Test
    void fluentDescriptorConfigurationExposesEngineContainersProtocolsGroupsAndExtensions() {
        ArquillianDescriptor descriptor = newDescriptor("arquillian.xml");

        assertThat(descriptor.getDescriptorName()).isEqualTo("arquillian.xml");
        assertThat(descriptor.getDefaultProtocol()).isNull();
        assertThat(descriptor.getContainers()).isEmpty();
        assertThat(descriptor.getGroups()).isEmpty();
        assertThat(descriptor.getExtensions()).isEmpty();

        EngineDef engine = descriptor.engine()
                .deploymentExportPath("target/arquillian-deployments")
                .deploymentExportExploded(Boolean.TRUE)
                .maxTestClassesBeforeRestart(5);

        DefaultProtocolDef defaultProtocol = descriptor.defaultProtocol("Servlet 3.0")
                .property("host", "127.0.0.1")
                .property("port", "8181");

        ContainerDef managedContainer = descriptor.container("managed-jboss");
        assertThat(managedContainer.isDefault()).isFalse();
        assertThat(managedContainer.getMode()).isEqualTo("suite");

        managedContainer.setContainerName("managed-jboss")
                .setDefault()
                .setMode("class")
                .dependency("org.example:server-adapter")
                .dependency("org.example:test-utilities")
                .property("bindAddress", "0.0.0.0")
                .property("managementPort", "9990")
                .overrideProperty("managementPort", "9991");
        ProtocolDef servletProtocol = managedContainer.protocol("Servlet 3.0")
                .property("contextRoot", "/sample")
                .property("secure", "false");

        ContainerDef remoteContainer = descriptor.container("remote-jboss")
                .setMode("suite")
                .property("managementAddress", "localhost");
        remoteContainer.protocol("JMX")
                .setType("JMX")
                .property("port", "1090");

        GroupDef smokeGroup = descriptor.group("smoke").setGroupName("smoke").setGroupDefault();
        ContainerDef groupContainer = smokeGroup.container("grouped-managed")
                .setMode("test")
                .property("startupTimeoutInSeconds", "30");
        groupContainer.protocol("Servlet 3.1").property("contextRoot", "/grouped");

        ExtensionDef extension = descriptor.extension("jacoco")
                .property("enabled", "true")
                .property("append", "false");

        assertThat(engine.getDeploymentExportPath()).isEqualTo("target/arquillian-deployments");
        assertThat(engine.getDeploymentExportExploded()).isTrue();
        assertThat(engine.getMaxTestClassesBeforeRestart()).isEqualTo(5);

        assertThat(defaultProtocol.getType()).isEqualTo("Servlet 3.0");
        assertThat(defaultProtocol.getProperties()).containsOnly(entry("host", "127.0.0.1"), entry("port", "8181"));
        assertThat(descriptor.getDefaultProtocol().getProperties()).containsEntry("host", "127.0.0.1");

        assertThat(descriptor.getContainers())
                .extracting(ContainerDef::getContainerName)
                .containsExactly("managed-jboss", "remote-jboss");
        assertThat(managedContainer.getContainerName()).isEqualTo("managed-jboss");
        assertThat(managedContainer.isDefault()).isTrue();
        assertThat(managedContainer.getMode()).isEqualTo("class");
        assertThat(managedContainer.getDependencies())
                .containsExactly("org.example:server-adapter", "org.example:test-utilities");
        assertThat(managedContainer.getContainerProperties())
                .containsOnly(entry("bindAddress", "0.0.0.0"), entry("managementPort", "9991"));
        assertThat(servletProtocol.getType()).isEqualTo("Servlet 3.0");
        assertThat(servletProtocol.getProtocolProperties())
                .containsOnly(entry("contextRoot", "/sample"), entry("secure", "false"));
        assertThat(managedContainer.getProtocols())
                .extracting(ProtocolDef::getType)
                .containsExactly("Servlet 3.0");

        assertThat(remoteContainer.getContainerProperties()).containsOnly(entry("managementAddress", "localhost"));
        assertThat(remoteContainer.getProtocols().get(0).getProtocolProperties()).containsOnly(entry("port", "1090"));

        assertThat(smokeGroup.getGroupName()).isEqualTo("smoke");
        assertThat(smokeGroup.isGroupDefault()).isTrue();
        assertThat(descriptor.getGroups())
                .extracting(GroupDef::getGroupName)
                .containsExactly("smoke");
        assertThat(smokeGroup.getGroupContainers())
                .extracting(ContainerDef::getContainerName)
                .containsExactly("grouped-managed");
        assertThat(smokeGroup.getGroupContainers().get(0).getProtocols().get(0).getProtocolProperties())
                .containsOnly(entry("contextRoot", "/grouped"));

        assertThat(extension.getExtensionName()).isEqualTo("jacoco");
        assertThat(extension.getExtensionProperties()).containsOnly(entry("enabled", "true"), entry("append", "false"));
        assertThat(descriptor.getExtensions())
                .extracting(ExtensionDef::getExtensionName)
                .containsExactly("jacoco");
    }

    @Test
    void descriptorExportsToXmlAndCanBeImportedFromString() {
        ArquillianDescriptor original = populatedDescriptor("roundtrip.xml");

        String xml = original.exportAsString();
        assertThat(xml)
                .contains("arquillian")
                .contains("engine")
                .contains("defaultProtocol")
                .contains("container")
                .contains("group")
                .contains("extension");

        ArquillianDescriptor imported = Descriptors.importAs(ArquillianDescriptor.class, "roundtrip.xml")
                .fromString(xml);

        assertImportedDescriptor(imported);
    }

    @Test
    void descriptorExportsToOutputStreamAndCanBeImportedFromStreamWithoutClosingIt() throws Exception {
        ArquillianDescriptor original = populatedDescriptor("stream.xml");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        original.exportTo(output);

        String xml = output.toString(StandardCharsets.UTF_8.name());
        assertThat(xml).isEqualTo(original.exportAsString());

        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        ArquillianDescriptor imported = Descriptors.importAs(ArquillianDescriptor.class, "stream.xml")
                .fromStream(input, false);

        assertImportedDescriptor(imported);
        assertThat(input.read()).isEqualTo(-1);
    }

    @Test
    void descriptorPreservesMultilineAndXmlEscapedPropertyValuesAcrossExportAndImport() {
        String startupScript = """
                if (serverName < target) {
                    log("server & test");
                }
                return "ready";
                """.trim();
        String protocolPayload = """
                {
                  "path": "/service?name=arquillian&mode=test",
                  "enabled": true
                }
                """.trim();
        ArquillianDescriptor descriptor = newDescriptor("escaped-values.xml");

        descriptor.defaultProtocol("Servlet 3.0")
                .property("payload", protocolPayload);
        descriptor.container("scripted")
                .property("startupScript", startupScript)
                .protocol("HTTP")
                .property("requestPayload", protocolPayload);
        descriptor.extension("script-runner")
                .property("startupScript", startupScript);

        ArquillianDescriptor imported = Descriptors.importAs(ArquillianDescriptor.class, "escaped-values.xml")
                .fromString(descriptor.exportAsString());

        assertThat(imported.getDefaultProtocol().getProperties()).containsOnly(entry("payload", protocolPayload));
        ContainerDef scripted = onlyContainerNamed(imported.getContainers(), "scripted");
        assertThat(scripted.getContainerProperties()).containsOnly(entry("startupScript", startupScript));
        assertThat(scripted.getProtocols().get(0).getProtocolProperties())
                .containsOnly(entry("requestPayload", protocolPayload));
        assertThat(onlyExtensionNamed(imported.getExtensions(), "script-runner").getExtensionProperties())
                .containsOnly(entry("startupScript", startupScript));
    }

    @Test
    void importedDescriptorCanBeModifiedAndReExported() {
        ArquillianDescriptor descriptor = Descriptors.importAs(ArquillianDescriptor.class, "modifiable.xml")
                .fromString(populatedDescriptor("modifiable.xml").exportAsString());

        descriptor.defaultProtocol("JMX").property("port", "1090");
        descriptor.container("managed")
                .overrideProperty("port", "10090")
                .protocol("JMX")
                .property("enabled", "true");
        descriptor.extension("drone").setExtensionName("webdriver").property("browser", "firefox");

        String reExportedXml = descriptor.exportAsString();
        ArquillianDescriptor reImported = Descriptors.importAs(ArquillianDescriptor.class, "modified.xml")
                .fromString(reExportedXml);

        assertThat(reImported.getDefaultProtocol().getType()).isEqualTo("JMX");
        assertThat(reImported.getDefaultProtocol().getProperties())
                .containsOnly(entry("host", "localhost"), entry("port", "1090"));
        ContainerDef managed = onlyContainerNamed(reImported.getContainers(), "managed");
        assertThat(managed.getContainerProperties()).containsOnly(entry("host", "localhost"), entry("port", "10090"));
        assertThat(managed.getProtocols()).extracting(ProtocolDef::getType).containsExactly("Servlet 3.0", "JMX");
        assertThat(managed.getProtocols().get(1).getProtocolProperties()).containsOnly(entry("enabled", "true"));
        assertThat(onlyExtensionNamed(reImported.getExtensions(), "webdriver").getExtensionProperties())
                .containsOnly(entry("browser", "firefox"));
    }

    private static ArquillianDescriptor populatedDescriptor(String descriptorName) {
        ArquillianDescriptor descriptor = newDescriptor(descriptorName);
        descriptor.engine()
                .deploymentExportPath("target/exported")
                .deploymentExportExploded(Boolean.FALSE)
                .maxTestClassesBeforeRestart(3);
        descriptor.defaultProtocol("Servlet 3.0")
                .property("host", "localhost")
                .property("port", "8080");
        descriptor.container("managed")
                .setDefault()
                .setMode("suite")
                .dependency("org.example:adapter")
                .property("host", "localhost")
                .property("port", "9990")
                .protocol("Servlet 3.0")
                .property("contextRoot", "/app");
        descriptor.container("remote")
                .setMode("manual")
                .property("host", "remote.example.test");
        descriptor.group("ci")
                .setGroupDefault()
                .container("managed")
                .property("port", "9991");
        descriptor.extension("recorder")
                .property("enabled", "true");
        return descriptor;
    }

    private static ArquillianDescriptor newDescriptor(String descriptorName) {
        return Descriptors.create(ArquillianDescriptor.class, descriptorName);
    }

    private static void assertImportedDescriptor(ArquillianDescriptor descriptor) {
        assertThat(descriptor.getDescriptorName()).isNotBlank();
        assertThat(descriptor.engine().getDeploymentExportPath()).isEqualTo("target/exported");
        assertThat(descriptor.engine().getDeploymentExportExploded()).isFalse();
        assertThat(descriptor.engine().getMaxTestClassesBeforeRestart()).isEqualTo(3);
        assertThat(descriptor.getDefaultProtocol().getType()).isEqualTo("Servlet 3.0");
        assertThat(descriptor.getDefaultProtocol().getProperties())
                .containsOnly(entry("host", "localhost"), entry("port", "8080"));

        assertThat(descriptor.getContainers())
                .extracting(ContainerDef::getContainerName)
                .containsExactly("managed", "remote");
        ContainerDef managed = onlyContainerNamed(descriptor.getContainers(), "managed");
        assertThat(managed.isDefault()).isTrue();
        assertThat(managed.getMode()).isEqualTo("suite");
        assertThat(managed.getDependencies()).containsExactly("org.example:adapter");
        assertThat(managed.getContainerProperties())
                .containsOnly(entry("host", "localhost"), entry("port", "9990"));
        assertThat(managed.getProtocols().get(0).getProtocolProperties()).containsOnly(entry("contextRoot", "/app"));

        ContainerDef remote = onlyContainerNamed(descriptor.getContainers(), "remote");
        assertThat(remote.isDefault()).isFalse();
        assertThat(remote.getMode()).isEqualTo("manual");
        assertThat(remote.getContainerProperties()).containsOnly(entry("host", "remote.example.test"));

        GroupDef ciGroup = descriptor.getGroups().get(0);
        assertThat(ciGroup.getGroupName()).isEqualTo("ci");
        assertThat(ciGroup.isGroupDefault()).isTrue();
        assertThat(ciGroup.getGroupContainers().get(0).getContainerProperties()).containsOnly(entry("port", "9991"));

        ExtensionDef recorder = descriptor.getExtensions().get(0);
        assertThat(recorder.getExtensionName()).isEqualTo("recorder");
        assertThat(recorder.getExtensionProperties()).containsOnly(entry("enabled", "true"));
    }

    private static ContainerDef onlyContainerNamed(List<ContainerDef> containers, String name) {
        return containers.stream()
                .filter(container -> name.equals(container.getContainerName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing container " + name));
    }

    private static ExtensionDef onlyExtensionNamed(List<ExtensionDef> extensions, String name) {
        return extensions.stream()
                .filter(extension -> name.equals(extension.getExtensionName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing extension " + name));
    }
}
