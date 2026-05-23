/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class MiscCodecTest {
    @Test
    void parseObjectDeserializesPathFromJsonString() {
        Path path = JSON.parseObject("\"native-image-path\"", Path.class);

        assertThat(path).isEqualTo(Paths.get("native-image-path"));
    }
}
