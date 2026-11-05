/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_server;

import org.glassfish.grizzly.http.server.util.ClassLoaderUtil;
import org.glassfish.grizzly.http.server.util.StringParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ClassLoaderUtilTest {
    @Test
    void createsParserByNameWithExplicitClassLoader() {
        StringParser parser = assertInstanceOf(StringParser.class,
                ClassLoaderUtil.load("org.glassfish.grizzly.http.server.util.StringParser",
                        ClassLoaderUtilTest.class.getClassLoader()));

        parser.setString("name=value");
        int separator = parser.findChar('=');

        assertEquals("name", parser.extract(0, separator));
        parser.advance();
        assertEquals("value", parser.extract(parser.getIndex()));
    }

    @Test
    void createsIndependentParsersUsingContextClassLoader() {
        String className = "org.glassfish.grizzly.http.server.util.StringParser";
        StringParser first = assertInstanceOf(StringParser.class, ClassLoaderUtil.load(className));
        StringParser second = assertInstanceOf(StringParser.class, ClassLoaderUtil.load(className));

        first.setString("alpha beta");
        second.setString("gamma delta");

        assertNotSame(first, second);
        assertEquals("alpha", first.extract(0, first.findWhite()));
        assertEquals("gamma", second.extract(0, second.findWhite()));
        assertEquals("alpha beta", first.getString());
        assertEquals("gamma delta", second.getString());
    }
}
