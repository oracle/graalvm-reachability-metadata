/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;

import com.google.gwt.user.server.rpc.EnumMapUtil;

import org.junit.jupiter.api.Test;

public class EnumMapUtilTest {
    @Test
    void getKeyTypeResolvesTypeForEmptyEnumMap() throws Exception {
        EnumMap<SampleKey, String> map = new EnumMap<>(SampleKey.class);

        Class<SampleKey> keyType = EnumMapUtil.getKeyType(map);

        assertThat(keyType).isEqualTo(SampleKey.class);
    }

    private enum SampleKey {
        FIRST,
        SECOND
    }
}
