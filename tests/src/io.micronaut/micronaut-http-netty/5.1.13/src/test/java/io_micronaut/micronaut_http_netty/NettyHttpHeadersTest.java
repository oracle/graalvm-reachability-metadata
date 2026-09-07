/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http_netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.http.MediaType;
import io.micronaut.http.netty.NettyHttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class NettyHttpHeadersTest {
    @Test
    @Timeout(55)
    void managesAndConvertsHeadersThroughThePublicApi() {
        NettyHttpHeaders headers = new NettyHttpHeaders();

        headers.add("X-Attempt", "3");
        headers.add("X-Value", "first");
        headers.add("X-Value", "second");
        headers.contentType(MediaType.APPLICATION_JSON_TYPE);

        assertThat(headers.get("X-Attempt", ConversionContext.INT)).contains(3);
        assertThat(headers.getAll("X-Value")).containsExactly("first", "second");
        assertThat(headers.contentType()).contains(MediaType.APPLICATION_JSON_TYPE);
        assertThat(headers.getNettyHeaders().get("X-Attempt")).isEqualTo("3");

        headers.remove("X-Value");
        assertThat(headers.contains("X-Value")).isFalse();
    }
}
