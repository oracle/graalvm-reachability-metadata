/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api.support;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Validator;

public final class StubJaxbContext extends JAXBContext {
    private final String source;

    public StubJaxbContext(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    @Override
    public Unmarshaller createUnmarshaller() throws JAXBException {
        return null;
    }

    @Override
    public Marshaller createMarshaller() throws JAXBException {
        return null;
    }

    @Override
    public Validator createValidator() throws JAXBException {
        return null;
    }
}
