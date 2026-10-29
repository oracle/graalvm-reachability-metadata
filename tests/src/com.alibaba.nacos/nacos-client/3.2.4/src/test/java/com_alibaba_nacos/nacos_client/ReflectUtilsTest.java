/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

public class ReflectUtilsTest {
    @Test
    void extractsConfiguredInstanceProperties() {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        assertEquals("127.0.0.1", ReflectUtils.getFieldValue(instance, "ip"));
        assertEquals(8080, ReflectUtils.getFieldValue(instance, "port", -1));
    }
}
