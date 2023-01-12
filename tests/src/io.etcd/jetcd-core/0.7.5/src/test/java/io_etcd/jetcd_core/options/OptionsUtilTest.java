/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.options;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.options.OptionsUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionsUtilTest {
    static void check(byte[] prefix, byte[] expectedPrefixEndOf) {
        ByteSequence actual = OptionsUtil.prefixEndOf(ByteSequence.from(prefix));
        assertThat(actual).isEqualTo(ByteSequence.from(expectedPrefixEndOf));
    }

    @Test
    void aaPlus1() {
        check(new byte[]{(byte) 'a', (byte) 'a'}, new byte[]{(byte) 'a', (byte) 'b'});
    }

    @Test
    void axffPlus1() {
        check(new byte[]{(byte) 'a', (byte) 0xff}, new byte[]{(byte) 'b'});
    }

    @Test
    void xffPlus1() {
        check(new byte[]{(byte) 0xff}, new byte[]{(byte) 0x00});
    }
}
