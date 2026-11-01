/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.io.StringReader;
import java.net.URI;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ext.ExtNamespaceHandler;
import org.apache.aries.blueprint.namespace.NamespaceHandlerRegistryImpl;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NamespaceHandlerRegistryImplTest {
    @Test
    void validatesBlueprintDocumentsAndReusesCompiledSchema() throws Exception {
        NamespaceHandlerRegistryImpl registry = new NamespaceHandlerRegistryImpl(new TestBundleContext());
        try {
            Map<URI, NamespaceHandler> handlers = Map.of();
            Schema schema = registry.getSchema(handlers);
            schema.newValidator().validate(xml("""
                    <blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0">
                        <bean id="example" class="java.lang.String">
                            <argument value="hello"/>
                        </bean>
                    </blueprint>
                    """));
            assertThat(registry.getSchema(handlers)).isSameAs(schema);
            assertThatThrownBy(() -> schema.newValidator().validate(xml("""
                    <blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0">
                        <bean id="example" class="java.lang.String" unsupported="true"/>
                    </blueprint>
                    """))).isInstanceOf(SAXException.class);
        } finally {
            registry.destroy();
        }
    }

    @Test
    void combinesBlueprintAndExtensionSchemas() throws Exception {
        NamespaceHandlerRegistryImpl registry = new NamespaceHandlerRegistryImpl(new TestBundleContext());
        try {
            Map<URI, NamespaceHandler> handlers = Map.of(
                    URI.create(ExtNamespaceHandler.BLUEPRINT_EXT_NAMESPACE), new ExtNamespaceHandler());
            Schema schema = registry.getSchema(handlers);
            schema.newValidator().validate(xml("""
                    <blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"
                               xmlns:ext="http://aries.apache.org/blueprint/xmlns/blueprint-ext/v1.0.0">
                        <ext:property-placeholder id="defaults" system-properties="never">
                            <ext:default-properties>
                                <ext:property name="region" value="west"/>
                            </ext:default-properties>
                        </ext:property-placeholder>
                    </blueprint>
                    """));
            assertThat(registry.getSchema(handlers)).isSameAs(schema);
        } finally {
            registry.destroy();
        }
    }

    private static StreamSource xml(String content) {
        return new StreamSource(new StringReader(content));
    }
}
