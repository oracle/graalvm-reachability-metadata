/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.internal.desc.Parameter;
import dm.jdbc.util.MiscUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class MiscUtilTest {
    @Test
    void clonesParameterDescriptorsIndependently() throws Exception {
        Parameter parameter = new Parameter();
        parameter.name = "quantity";
        parameter.prec = 12;
        Parameter[] original = {parameter};

        Parameter[] copied = MiscUtil.deepClone(original);
        assertNotSame(original, copied);
        assertNotSame(parameter, copied[0]);
        assertEquals("quantity", copied[0].name);
        assertEquals(12, copied[0].prec);
        copied[0].name = "price";
        assertEquals("quantity", parameter.name);
    }
}
