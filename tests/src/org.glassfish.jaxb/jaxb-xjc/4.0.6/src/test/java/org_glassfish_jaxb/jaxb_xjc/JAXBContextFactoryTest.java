/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.runtime.JAXBContextFactory;
import jakarta.xml.bind.JAXBContext;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org_glassfish_jaxb.jaxb_xjc.context.ObjectFactory;
import org_glassfish_jaxb.jaxb_xjc.context.impl.Greeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JAXBContextFactoryTest {
    @Test
    void classArrayBridgeUsesImplementationFactory() throws Exception {
        JAXBContext context = JAXBContextFactory.createContext(new Class<?>[]{ObjectFactory.class}, Map.of());
        roundTrip(context);
    }

    @Test
    void contextPathBridgeUsesImplementationFactory() throws Exception {
        JAXBContext context = JAXBContextFactory.createContext("org_glassfish_jaxb.jaxb_xjc.context",
                ObjectFactory.class.getClassLoader(), Map.of());
        roundTrip(context);
    }

    private static void roundTrip(JAXBContext context) throws Exception {
        Greeting greeting = new ObjectFactory().createGreeting();
        greeting.text = "Hello from JAXB";
        StringWriter xml = new StringWriter();
        context.createMarshaller().marshal(greeting, xml);
        assertTrue(xml.toString().contains("<greeting>"));
        Greeting restored = assertInstanceOf(Greeting.class,
                context.createUnmarshaller().unmarshal(new StringReader(xml.toString())));
        assertEquals(greeting.text, restored.text);
    }
}
