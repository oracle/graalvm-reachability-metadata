/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

import static org.junit.jupiter.api.Assertions.*;

public class HtmlCharacterEntityReferencesTest {
    @Test
    void escapesAndUnescapesNamedAndNumericHtmlEntities() {
        assertEquals("&lt;p&gt;A &amp; B&lt;/p&gt;", HtmlUtils.htmlEscape("<p>A & B</p>"));
        assertEquals("<p>A & B</p>", HtmlUtils.htmlUnescape("&lt;p&gt;A &amp; B&lt;/p&gt;"));
        assertEquals("A & B", HtmlUtils.htmlUnescape("A &#38; B"));
    }
}
