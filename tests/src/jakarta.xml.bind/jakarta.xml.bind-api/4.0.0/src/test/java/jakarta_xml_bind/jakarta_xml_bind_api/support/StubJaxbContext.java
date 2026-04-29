/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api.support;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.Validator;

public class StubJaxbContext extends JAXBContext {
    private final String source;

    public StubJaxbContext(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    @Override
    public Unmarshaller createUnmarshaller() throws JAXBException {
        throw new UnsupportedOperationException("Unmarshaller is not needed for this test");
    }

    @Override
    public Marshaller createMarshaller() throws JAXBException {
        throw new UnsupportedOperationException("Marshaller is not needed for this test");
    }

    @Override
    public Validator createValidator() throws JAXBException {
        throw new UnsupportedOperationException("Validator is not needed for this test");
    }
}
