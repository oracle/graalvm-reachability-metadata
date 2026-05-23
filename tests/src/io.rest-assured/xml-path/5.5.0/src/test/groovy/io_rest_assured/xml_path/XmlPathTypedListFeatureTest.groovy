/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.xml_path

import io.restassured.path.xml.XmlPath
import org.junit.jupiter.api.Test

public class XmlPathTypedListFeatureTest {
    @Test
    void convertsXmlValuesToRequestedListElementType() {
        XmlPath xmlPath = new XmlPath('<numbers><value>10</value><value>20</value><value>30</value></numbers>')

        List values = xmlPath.getList('numbers.value', Integer.class)

        assert values.size() == 3
        assert values.get(0) == 10
        assert values.get(1) == 20
        assert values.get(2) == 30
    }
}
