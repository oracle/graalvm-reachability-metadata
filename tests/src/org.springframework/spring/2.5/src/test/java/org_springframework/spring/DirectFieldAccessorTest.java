/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;

import static org.junit.jupiter.api.Assertions.*;

public class DirectFieldAccessorTest {
    @Test
    void readsAndConvertsFieldsWithoutBeanSetters() {
        TestBeans.Fields bean = new TestBeans.Fields();
        DirectFieldAccessor accessor = new DirectFieldAccessor(bean);
        assertEquals("before", accessor.getPropertyValue("label"));
        accessor.setPropertyValue("label", "after");
        accessor.setPropertyValue("count", "23");
        assertEquals("after", bean.label);
        assertEquals(23, bean.count);
        assertEquals(23, accessor.getPropertyValue("count"));
    }
}
