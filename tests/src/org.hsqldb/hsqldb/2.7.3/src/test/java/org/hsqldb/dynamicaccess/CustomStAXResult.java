/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hsqldb.dynamicaccess;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stax.StAXResult;

public class CustomStAXResult extends StAXResult {
    public CustomStAXResult(XMLStreamWriter xmlStreamWriter) {
        super(xmlStreamWriter);
    }
}
