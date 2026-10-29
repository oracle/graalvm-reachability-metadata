/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import com.alibaba.nacos.api.remote.Payload;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public class NacosServiceLoaderTest {
    @Test
    void createsIndependentPayloadInstancesFromCachedServiceTypes() {
        Collection<Payload> discovered = NacosServiceLoader.load(Payload.class);
        Collection<Payload> reloaded = NacosServiceLoader.load(Payload.class);
        assertFalse(discovered.isEmpty());
        assertEquals(discovered.size(), reloaded.size());
        ConfigQueryRequest first = discovered.stream().filter(ConfigQueryRequest.class::isInstance)
                .map(ConfigQueryRequest.class::cast).findFirst().orElseThrow();
        ConfigQueryRequest second = reloaded.stream().filter(ConfigQueryRequest.class::isInstance)
                .map(ConfigQueryRequest.class::cast).findFirst().orElseThrow();
        first.setDataId("application");
        second.setDataId("settings");
        assertNotSame(first, second);
        assertEquals("application", first.getDataId());
        assertEquals("settings", second.getDataId());
    }
}
