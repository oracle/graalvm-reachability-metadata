/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.support.logging.Resources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourcesTest {
    @Test
    void loadsDruidClassByName() throws Exception {
        ClassLoader original = Resources.getDefaultClassLoader();
        try {
            Resources.setDefaultClassLoader(ClassLoader.getPlatformClassLoader());
            assertEquals(Resources.class, Resources.classForName("com.alibaba.druid.support.logging.Resources"));
        } finally {
            Resources.setDefaultClassLoader(original);
        }
    }
}
