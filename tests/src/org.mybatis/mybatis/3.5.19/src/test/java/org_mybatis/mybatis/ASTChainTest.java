/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ASTChainTest {
    @Test
    void copiesAllElementsOfIndexedBeanProperty() {
        IndexedBean bean = new IndexedBean();
        int[] result = (int[]) OgnlCache.getValue("values[*]", bean);
        assertArrayEquals(new int[]{2, 6}, result);
        assertNotSame(bean.getValues(), result);
        assertEquals(6, OgnlCache.getValue("values[1]", bean));
    }

    public static class IndexedBean {
        private final int[] values = {2, 6};

        public int[] getValues() {
            return values;
        }

        public int getValues(int index) {
            return values[index];
        }
    }
}
