/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.MySqlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class MySqlUtilsTest {
    @Test
    void recognizesKeywordsAndBuiltInTypesFromPackagedDictionaries() {
        assertTrue(MySqlUtils.isKeyword("SELECT"));
        assertTrue(MySqlUtils.isBuiltinDataType("VARCHAR"));
        assertFalse(MySqlUtils.isKeyword("customer_identifier"));
        assertFalse(MySqlUtils.isBuiltinDataType("customer_identifier"));
    }
}
