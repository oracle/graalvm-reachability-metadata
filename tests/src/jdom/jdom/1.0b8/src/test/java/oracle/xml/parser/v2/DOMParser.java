/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package oracle.xml.parser.v2;

import java.io.IOException;

import oracle.xml.parser.XMLParser;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DOMParser extends XMLParser {
    @Override
    public void parse(InputSource inputSource) throws IOException, SAXException {
        super.parse(inputSource);
    }

    @Override
    public Document getDocument() {
        return super.getDocument();
    }
}
