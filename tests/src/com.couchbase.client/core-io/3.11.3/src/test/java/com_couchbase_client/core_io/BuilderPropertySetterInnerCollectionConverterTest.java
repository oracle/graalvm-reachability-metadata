/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.env.BuilderPropertySetter;
import com.couchbase.client.core.env.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BuilderPropertySetterInnerCollectionConverterTest {
    @Test
    void exercisesPublicApi() throws Exception {
        SecurityConfig.Builder builder = SecurityConfig.builder();
        new BuilderPropertySetter().set(builder, "ciphers", "TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384");
        assertEquals(List.of("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"), builder.build().ciphers());
    }
}
