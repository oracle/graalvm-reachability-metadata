/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.glassfish.jaxb.runtime.api.RawAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's raw-property accessor subsystem for a profile editor workflow.
public class RawAccessorProfileTest {

    private static final String PROFILE_NAMESPACE = "urn:profiles";

    @Test
    @Timeout(60)
    void editsAProfilePropertyThroughTheRuntimeAccessor() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Profile.class);
        JAXBRIContext runtimeContext = (JAXBRIContext) context;
        RawAccessor<Profile, String> displayNameAccessor = runtimeContext.getElementPropertyAccessor(
                Profile.class, PROFILE_NAMESPACE, "displayName");

        Profile profile = new Profile("Rhea");
        assertEquals("Rhea", displayNameAccessor.get(profile));
        displayNameAccessor.set(profile, "Mina");
        assertEquals("Mina", displayNameAccessor.get(profile));

        Marshaller marshaller = context.createMarshaller();
        StringWriter output = new StringWriter();
        marshaller.marshal(profile, output);
        String xml = output.toString();
        assertTrue(xml.contains("<displayName>Mina</displayName>"));

        Profile restored = (Profile) context.createUnmarshaller()
                .unmarshal(new StringReader(xml));
        assertEquals("Mina", displayNameAccessor.get(restored));
    }

    @XmlRootElement(name = "profile", namespace = PROFILE_NAMESPACE)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Profile {

        @XmlElement(namespace = PROFILE_NAMESPACE, required = true)
        private String displayName;

        public Profile() {
        }

        private Profile(String displayName) {
            this.displayName = displayName;
        }
    }
}
