/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.configuration.ConfigUtils;
import io.quarkus.runtime.configuration.MemorySize;
import io.quarkus.runtime.configuration.MemorySizeConverter;
import io.smallrye.config.SmallRyeConfig;

public class MemorySizeConverterTest {
    @Test
    void discoversConverterForBinaryMemorySizes() {
        SmallRyeConfig config = ConfigUtils.emptyConfigBuilder()
                .withDefaultValues(Map.of(
                        "app.buffer", " 16k ",
                        "app.cache", "2M",
                        "app.bytes", "512",
                        "app.archive", "1Y"))
                .build();

        assertThat(config.getValue("app.buffer", MemorySize.class).asLongValue()).isEqualTo(16384);
        assertThat(config.getValue("app.cache", MemorySize.class).asLongValue()).isEqualTo(2097152);
        assertThat(config.getValue("app.bytes", MemorySize.class).asLongValue()).isEqualTo(512);
        assertThat(config.getValue("app.archive", MemorySize.class).asBigInteger())
                .isEqualTo(BigInteger.valueOf(1024).pow(8));
    }

    @Test
    void handlesEmptyAndInvalidMemorySizes() {
        MemorySizeConverter converter = new MemorySizeConverter();

        assertThat(converter.convert("  ")).isNull();
        assertThatThrownBy(() -> converter.convert("1.5M")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.convert("-1K")).isInstanceOf(IllegalArgumentException.class);
    }
}
