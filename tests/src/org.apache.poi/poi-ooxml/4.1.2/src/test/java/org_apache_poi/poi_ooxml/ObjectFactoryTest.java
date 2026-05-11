/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml;

import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xdgf.util.ObjectFactory;
import org.apache.xmlbeans.XmlObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFactoryTest {
    @Test
    void loadInstantiatesRegisteredConstructor() throws Exception {
        ObjectFactory<POIXMLException, XmlObject> factory = new ObjectFactory<>();
        factory.put("exception", POIXMLException.class, String.class);

        POIXMLException exception = factory.load("exception", "created by ObjectFactory");

        assertThat(exception).hasMessage("created by ObjectFactory");
    }
}
