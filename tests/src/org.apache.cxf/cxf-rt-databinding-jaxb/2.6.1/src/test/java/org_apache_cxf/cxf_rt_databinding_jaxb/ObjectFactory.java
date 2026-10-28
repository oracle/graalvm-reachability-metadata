/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

public class ObjectFactory {
    public ObjectFactory() {
    }

    public JAXBElement<String> createReflectionWrapperPayload(String value) {
        return new JAXBElement<String>(
                new QName("urn:cxf-jaxb-test", "payload"), String.class, value);
    }
}
