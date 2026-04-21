/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.openejb.javaee.api.support;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Validator;

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
