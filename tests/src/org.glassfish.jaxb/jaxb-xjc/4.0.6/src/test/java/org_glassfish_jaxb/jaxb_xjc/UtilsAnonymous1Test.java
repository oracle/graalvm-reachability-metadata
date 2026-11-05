/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.model.nav.NType;
import com.sun.tools.xjc.model.nav.NavigatorImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class UtilsAnonymous1Test {
    @Test
    void navigatorFindsACollectionBaseType() {
        NavigatorImpl navigator = NavigatorImpl.theInstance;
        NType base = navigator.getBaseClass(NavigatorImpl.create(ArrayList.class),
                NavigatorImpl.create(List.class));
        assertNotNull(base);
        assertEquals("java.util.List<E>", base.fullName());
        assertFalse(navigator.isParameterizedType(NavigatorImpl.create(String.class)));
    }
}
