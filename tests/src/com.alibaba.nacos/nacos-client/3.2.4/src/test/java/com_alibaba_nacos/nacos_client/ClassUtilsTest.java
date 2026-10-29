/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    @Test
    void resolvesConfiguredTypesAndArrayNames() throws Exception {
        ClassLoader loader = Instance.class.getClassLoader();
        assertEquals(Instance.class, ClassUtils.findClassByName(Instance.class.getName()));
        assertEquals(Instance.class, ClassUtils.forName(Instance.class.getName(), loader));
        assertEquals(Instance[].class, ClassUtils.forName(Instance.class.getName() + "[]", loader));
        assertEquals(Instance[].class, ClassUtils.forName("[L" + Instance.class.getName() + ";", loader));
        assertEquals(Instance[][].class, ClassUtils.forName("[[L" + Instance.class.getName() + ";", loader));
        assertEquals(Thread.State.class, ClassUtils.forName("java.lang.Thread.State", loader));
    }
}
