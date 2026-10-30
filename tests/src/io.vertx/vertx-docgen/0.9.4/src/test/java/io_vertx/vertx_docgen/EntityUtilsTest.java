/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_docgen;

import io.vertx.docgen.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityUtilsTest {
    @Test
    void decodesDecimalHexadecimalAndUnicodeEntities() {
        assertEquals("A", EntityUtils.unescapeEntity("#65"));
        assertEquals("<", EntityUtils.unescapeEntity("#x003C"));
        assertEquals(">", EntityUtils.unescapeEntity("u003E"));
        assertEquals("\u20ac", EntityUtils.unescapeEntity("#8364"));
        assertEquals("\u20ac", EntityUtils.unescapeEntity("#x20AC"));
    }

    @Test
    void preservesNamedEntitiesForTheDocumentRenderer() {
        assertEquals("&amp;", EntityUtils.unescapeEntity("amp"));
        assertEquals("&nbsp;", EntityUtils.unescapeEntity("nbsp"));
        assertEquals("&custom;", EntityUtils.unescapeEntity("custom"));
    }

    @Test
    void leavesMalformedNumericEntitiesUnchanged() {
        assertEquals("#not-a-number", EntityUtils.unescapeEntity("#not-a-number"));
        assertEquals("#xZZ", EntityUtils.unescapeEntity("#xZZ"));
        assertEquals("uXYZ", EntityUtils.unescapeEntity("uXYZ"));
        assertEquals("#", EntityUtils.unescapeEntity("#"));
    }

    @Test
    void rendersMissingEntitiesAsEmptyText() {
        assertEquals("", EntityUtils.unescapeEntity(null));
        assertEquals("", EntityUtils.unescapeEntity(""));
        assertEquals("", EntityUtils.unescapeEntity(" \t "));
    }
}
