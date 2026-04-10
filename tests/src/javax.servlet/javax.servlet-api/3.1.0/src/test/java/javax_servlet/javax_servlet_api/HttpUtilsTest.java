/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.javax_servlet_api;

import java.util.Hashtable;

import javax.servlet.http.HttpUtils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class HttpUtilsTest {
    @Test
    void parseQueryStringLoadsHttpUtilsResources() {
        Hashtable<String, String[]> parameters =
                HttpUtils.parseQueryString("name=hello+world&name=second&encoded=%7Bvalue%7D");

        assertArrayEquals(new String[] {"hello world", "second"}, parameters.get("name"));
        assertArrayEquals(new String[] {"{value}"}, parameters.get("encoded"));
    }
}
