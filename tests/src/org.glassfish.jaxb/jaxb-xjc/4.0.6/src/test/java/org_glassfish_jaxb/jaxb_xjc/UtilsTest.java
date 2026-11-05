/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.model.nav.NavigatorImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilsTest {
    @Test
    void navigatorNamesConcreteAndArrayTypes() {
        assertEquals("java.lang.String", NavigatorImpl.create(String.class).fullName());
        assertEquals("java.lang.String[]", NavigatorImpl.create(String[].class).fullName());
    }
}
