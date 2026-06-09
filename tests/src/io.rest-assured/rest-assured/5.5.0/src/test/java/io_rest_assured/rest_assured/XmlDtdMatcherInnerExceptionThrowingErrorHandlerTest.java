/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import static io.restassured.matcher.RestAssuredMatchers.matchesDtd;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class XmlDtdMatcherInnerExceptionThrowingErrorHandlerTest {
    @Test
    void throwsParserExceptionWhenXmlDoesNotMatchDtd() {
        String dtd = """
                <!ELEMENT greeting (message)>
                <!ELEMENT message (#PCDATA)>
                """;
        String xml = """
                <greeting>
                    <unexpected>Hello from Rest Assured</unexpected>
                </greeting>
                """;
        Matcher<String> matcher = matchesDtd(dtd);

        SAXParseException exception = assertThrows(SAXParseException.class, () -> matcher.matches(xml));

        assertNotNull(exception.getMessage());
    }

    @Test
    void throwsParserExceptionWhenDtdCannotBeParsed() {
        String malformedDtd = """
                <!ELEMENT greeting (message)
                <!ELEMENT message (#PCDATA)>
                """;
        String xml = """
                <greeting>
                    <message>Hello from Rest Assured</message>
                </greeting>
                """;
        Matcher<String> matcher = matchesDtd(malformedDtd);

        SAXParseException exception = assertThrows(SAXParseException.class, () -> matcher.matches(xml));

        assertNotNull(exception.getMessage());
    }
}
