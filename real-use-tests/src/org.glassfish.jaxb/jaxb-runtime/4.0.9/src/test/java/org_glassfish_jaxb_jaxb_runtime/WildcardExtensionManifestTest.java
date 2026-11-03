/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import javax.xml.namespace.QName;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Element;

/// Exercises JAXB runtime's wildcard-element subsystem for a versioned manifest workflow.
public class WildcardExtensionManifestTest {

    private static final String VENDOR_NAMESPACE = "urn:vendor:extensions";
    private static final String MANIFEST_XML = """
            <manifest>
              <application>checkout</application>
              <signature><algorithm>SHA-256</algorithm></signature>
              <vendor:extension xmlns:vendor="urn:vendor:extensions" code="v1">
                <vendor:enabled>true</vendor:enabled>
              </vendor:extension>
            </manifest>
            """;

    @Test
    @Timeout(60)
    void preservesKnownAndUnknownExtensionsWhenUpdatingManifest() throws Exception {
        JAXBRIContext context = JAXBRIContext.newInstance(
                new Class<?>[] {Manifest.class, Signature.class}, List.of(), null, null, false, null);
        assertEquals(new QName("", "manifest"), context.getElementName(Manifest.class));

        Unmarshaller unmarshaller = context.createUnmarshaller();
        Manifest manifest = assertInstanceOf(Manifest.class,
                unmarshaller.unmarshal(new StringReader(MANIFEST_XML)));

        assertEquals("checkout", manifest.application);
        assertEquals(2, manifest.extensions.size());
        Signature signature = assertInstanceOf(Signature.class, manifest.extensions.get(0));
        assertEquals("SHA-256", signature.algorithm);

        Element vendorExtension = assertInstanceOf(Element.class, manifest.extensions.get(1));
        assertEquals(VENDOR_NAMESPACE, vendorExtension.getNamespaceURI());
        assertEquals("v1", vendorExtension.getAttribute("code"));
        assertEquals("true", vendorExtension.getElementsByTagNameNS(VENDOR_NAMESPACE, "enabled")
                .item(0).getTextContent());

        vendorExtension.setAttribute("code", "v2");
        Marshaller marshaller = context.createMarshaller();
        StringWriter output = new StringWriter();
        marshaller.marshal(manifest, output);
        String updatedXml = output.toString();
        assertTrue(updatedXml.contains("<signature>"));
        assertTrue(updatedXml.contains("SHA-256"));
        assertTrue(updatedXml.contains("code=\"v2\""));
        assertTrue(updatedXml.contains("enabled"));

        Manifest restored = assertInstanceOf(Manifest.class,
                context.createUnmarshaller().unmarshal(new StringReader(updatedXml)));
        Element restoredExtension = assertInstanceOf(Element.class, restored.extensions.get(1));
        assertEquals("v2", restoredExtension.getAttribute("code"));
        assertEquals("true", restoredExtension.getElementsByTagNameNS(VENDOR_NAMESPACE, "enabled")
                .item(0).getTextContent());
    }

    @XmlRootElement(name = "manifest")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Manifest {

        private String application;

        @XmlAnyElement(lax = true)
        private List<Object> extensions;

        public Manifest() {
        }
    }

    @XmlRootElement(name = "signature")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Signature {

        @XmlElement(required = true)
        private String algorithm;

        public Signature() {
        }
    }
}
