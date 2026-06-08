/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.apache.activemq.artemis.utils.XmlProvider;
import org.junit.jupiter.api.Test;

public class XmlProviderTest {
    @Test
    public void resolvesBundledSchemasWhenXxeIsDisabled() throws Exception {
        boolean previousXxeEnabled = XmlProvider.isXxeEnabled();
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        XmlProvider.setXxeEnabled(false);
        Thread.currentThread().setContextClassLoader(XmlProviderTest.class.getClassLoader());
        try {
            Schema schema = XmlProvider.newSchema(new StreamSource(new StringReader("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                               xmlns:core="urn:activemq:core"
                               xmlns:jms="urn:activemq:jms"
                               elementFormDefault="qualified">
                        <xs:import namespace="http://www.w3.org/XML/1998/namespace" schemaLocation="xml.xsd"/>
                        <xs:import namespace="urn:activemq:core" schemaLocation="artemis-configuration.xsd"/>
                        <xs:import namespace="urn:activemq:jms" schemaLocation="artemis-jms.xsd"/>
                        <xs:element name="configuration">
                            <xs:complexType>
                                <xs:sequence>
                                    <xs:element ref="core:core"/>
                                    <xs:element ref="jms:jms"/>
                                </xs:sequence>
                                <xs:attribute ref="xml:lang" xmlns:xml="http://www.w3.org/XML/1998/namespace"/>
                            </xs:complexType>
                        </xs:element>
                    </xs:schema>
                    """)), null);

            assertThat(schema).isNotNull();
            schema.newValidator().validate(new StreamSource(new StringReader("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <configuration xmlns:core="urn:activemq:core" xmlns:jms="urn:activemq:jms" xml:lang="en">
                        <core:core>broker</core:core>
                        <jms:jms>jms</jms:jms>
                    </configuration>
                    """)));
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
            XmlProvider.setXxeEnabled(previousXxeEnabled);
        }
    }
}
