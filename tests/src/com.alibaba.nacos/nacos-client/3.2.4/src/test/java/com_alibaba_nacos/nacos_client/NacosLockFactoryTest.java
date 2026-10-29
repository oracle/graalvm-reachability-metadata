/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.lock.NacosLockFactory;
import com.alibaba.nacos.api.lock.model.LockInstance;
import org.junit.jupiter.api.Test;

public class NacosLockFactoryTest {
    @Test
    void rejectsLockOperationsWhenTheServerDoesNotAdvertiseLockSupport() throws Exception {
        try (ConfigRpcServer server = new ConfigRpcServer()) {
            LockService service = NacosLockFactory.createLockService(server.properties());
            try {
                LockInstance lock = new LockInstance("jobs", 30000L, "metadata-test-lock");
                NacosRuntimeException failure = assertThrows(NacosRuntimeException.class, () -> service.lock(lock));
                assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, failure.getErrCode());
            } finally {
                service.shutdown();
            }
        }
    }
}
