/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.env.BuilderPropertySetter;
import com.couchbase.client.core.env.CoreEnvironment;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BuilderPropertySetterTest {
    @Test
    void exercisesPublicApi() throws Exception {
        CoreEnvironment.Builder<?> builder = CoreEnvironment.builder();
        BuilderPropertySetter setter = new BuilderPropertySetter();
        setter.set(builder, "timeout.kvTimeout", "15s");
        setter.set(builder, "timeout.queryTimeout", "30s");
        assertEquals(Duration.ofSeconds(15), builder.timeoutConfig().build().kvTimeout());
        assertEquals(Duration.ofSeconds(30), builder.timeoutConfig().build().queryTimeout());
    }
}
