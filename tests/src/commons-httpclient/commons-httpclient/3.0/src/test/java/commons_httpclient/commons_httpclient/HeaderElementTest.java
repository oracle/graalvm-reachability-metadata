/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.httpclient.HeaderElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HeaderElementTest {
    @Test
    void parsesQuotedValuesAndCaseInsensitiveParameters() {
        HeaderElement[] elements = HeaderElement.parseElements("attachment; filename=\"report,final.txt\"; charset=UTF-8");
        assertEquals(1, elements.length);
        assertEquals("attachment", elements[0].getName());
        assertEquals("report,final.txt", elements[0].getParameterByName("FILENAME").getValue());
        assertEquals("UTF-8", elements[0].getParameterByName("charset").getValue());
    }
}
