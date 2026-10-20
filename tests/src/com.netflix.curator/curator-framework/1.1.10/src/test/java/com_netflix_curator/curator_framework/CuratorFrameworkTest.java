/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_curator.curator_framework;

import java.nio.charset.StandardCharsets;

import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.api.CompressionProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CuratorFrameworkTest {
    @Test
    void defaultCompressionProviderRoundTripsData() throws Exception {
        CompressionProvider compressionProvider = CuratorFrameworkFactory.builder().getCompressionProvider();
        byte[] original = "curator-compression-".repeat(32).getBytes(StandardCharsets.UTF_8);

        byte[] compressed = compressionProvider.compress("/configuration", original);

        assertThat(compressed).isNotEqualTo(original);
        assertThat(compressed.length).isLessThan(original.length);
        assertThat(compressionProvider.decompress("/configuration", compressed)).containsExactly(original);
    }
}
