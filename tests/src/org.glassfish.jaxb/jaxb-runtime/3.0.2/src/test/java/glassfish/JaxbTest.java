/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package glassfish;


import java.io.StringReader;
import java.io.StringWriter;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class JaxbTest {

    @Test
    void marshal() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Foo.class);
        Marshaller marshaller = context.createMarshaller();
        Foo foo = new Foo();
        foo.setMessage("hello");
        StringWriter stringWriter = new StringWriter();
        marshaller.marshal(foo, stringWriter);
        Assertions.assertThat(stringWriter.toString())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><foo><message>hello</message></foo>");
    }

    @Test
    void unmarshal() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Foo.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        StringReader reader = new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><foo><message>hello</message></foo>");
        Foo foo = (Foo) unmarshaller.unmarshal(reader);
        Assertions.assertThat(foo).isEqualTo(new Foo("hello"));
    }

}
