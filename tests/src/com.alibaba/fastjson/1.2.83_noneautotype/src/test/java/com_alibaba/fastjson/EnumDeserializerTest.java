/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;

import org.junit.jupiter.api.Test;

public class EnumDeserializerTest {
    @Test
    void parseObjectUsesJsonFieldNamesAndAlternateNamesForEnums() {
        assertThat(JSON.parseObject("\"wire-name\"", WireStatus.class)).isEqualTo(WireStatus.READY);
        assertThat(JSON.parseObject("\"legacy-ready\"", WireStatus.class)).isEqualTo(WireStatus.READY);
        assertThat(JSON.parseObject("\"DONE\"", WireStatus.class)).isEqualTo(WireStatus.DONE);
    }

    public enum WireStatus {
        @JSONField(name = "wire-name", alternateNames = "legacy-ready")
        READY,
        DONE
    }
}
