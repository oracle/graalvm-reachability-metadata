/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.glassfish.jaxb;


import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

public class JaxbTest {

    @Test
    void createMarshaller() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Foo.class);
        context.createMarshaller();
    }

}
