/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.pathencoder.PathEncoder;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NacosServiceLoaderTest {
    @Test
    void discoversPathEncodersAndCreatesFreshInstancesFromTheCache() {
        Collection<PathEncoder> discovered = NacosServiceLoader.load(PathEncoder.class);
        assertFalse(discovered.isEmpty());
        PathEncoder original = windowsEncoder(discovered);
        PathEncoder cached = windowsEncoder(NacosServiceLoader.newServiceInstances(PathEncoder.class));
        PathEncoder reloaded = windowsEncoder(NacosServiceLoader.load(PathEncoder.class));
        assertNotSame(original, cached);
        assertNotSame(cached, reloaded);
        for (PathEncoder encoder : new PathEncoder[]{original, cached, reloaded}) {
            String path = "group:configuration?";
            assertTrue(encoder.needEncode(path));
            String encoded = encoder.encode(path, "UTF-8");
            assertFalse(encoded.contains(":"));
            assertFalse(encoded.contains("?"));
            assertEquals(path, encoder.decode(encoded, "UTF-8"));
        }
    }

    private PathEncoder windowsEncoder(Collection<PathEncoder> encoders) {
        return encoders.stream().filter(encoder -> "window".equals(encoder.name())).findFirst().orElseThrow();
    }
}
