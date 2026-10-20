/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.wsdl.WSDLExtensibilityPlugin;
import org.apache.cxf.wsdl11.WSDLDefinitionBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WSDLDefinitionBuilderTest {
    private Bus bus;

    @TempDir Path temporaryDirectory;

    @AfterEach
    void closeBus() {
        if (bus != null) {
            bus.shutdown(true);
        }
        BusFactory.setDefaultBus(null);
    }

    @Test
    void buildsDefinitionAndInitializesConfiguredExtensibilityPlugin() throws IOException {
        Path wsdl = temporaryDirectory.resolve("service.wsdl");
        Files.writeString(
                wsdl,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             targetNamespace="urn:test:wsdl"
                             name="TestDefinition"/>
                """);
        bus = new CXFBusFactory().createBus();
        WSDLDefinitionBuilder builder = new WSDLDefinitionBuilder(bus);

        Definition definition = builder.build(wsdl.toUri().toString());

        assertThat(definition.getTargetNamespace()).isEqualTo("urn:test:wsdl");
        assertThat(builder.getWSDLPlugins()).containsKey("test-extension");
        TestExtensibilityPlugin plugin =
                (TestExtensibilityPlugin) builder.getWSDLPlugins().get("test-extension");
        assertThat(plugin.extensionRegistry).isNotNull();
    }

    public static final class TestExtensibilityPlugin implements WSDLExtensibilityPlugin {
        private ExtensionRegistry extensionRegistry;

        public TestExtensibilityPlugin() {}

        @Override
        public void setExtensionRegistry(ExtensionRegistry extensionRegistry) {
            this.extensionRegistry = extensionRegistry;
        }

        @Override
        public ExtensibilityElement createExtension(Map<String, Object> args)
                throws WSDLException {
            return extensionRegistry.createExtension(
                    Definition.class, new QName("urn:test:wsdl", "extension"));
        }
    }
}
