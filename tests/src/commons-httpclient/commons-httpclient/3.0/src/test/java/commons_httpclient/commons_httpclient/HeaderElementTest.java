/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.NameValuePair;
import org.junit.jupiter.api.Test;

public class HeaderElementTest {
    @Test
    void parseElementsSplitsHeaderValueAndPreservesQuotedComma() {
        HeaderElement[] elements = HeaderElement.parseElements(
                "text/html; charset=UTF-8, attachment; filename=\"a,b.txt\"");

        assertThat(elements).hasSize(2);
        assertThat(elements[0].getName()).isEqualTo("text/html");
        assertThat(elements[0].getValue()).isNull();
        assertThat(elements[0].getParameterByName("charset").getValue()).isEqualTo("UTF-8");
        assertThat(elements[1].getName()).isEqualTo("attachment");
        assertThat(elements[1].getParameterByName("filename").getValue()).isEqualTo("a,b.txt");
    }

    @Test
    void charArrayConstructorParsesElementNameValueAndParameters() {
        char[] headerValue = "form-data; name=upload; filename=report.csv".toCharArray();

        HeaderElement element = new HeaderElement(headerValue, 0, headerValue.length);

        assertThat(element.getName()).isEqualTo("form-data");
        assertThat(element.getValue()).isNull();
        assertThat(element.getParameters())
                .extracting(NameValuePair::getName)
                .containsExactly("name", "filename");
        assertThat(element.getParameterByName("FILENAME").getValue()).isEqualTo("report.csv");
    }

    @Test
    void nullInputsReturnEmptyElementsAndRejectNullParameterLookup() {
        HeaderElement element = new HeaderElement();

        assertThat(HeaderElement.parseElements((String) null)).isEmpty();
        assertThat(HeaderElement.parseElements((char[]) null)).isEmpty();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> element.getParameterByName(null))
                .withMessage("Name may not be null");
    }
}
