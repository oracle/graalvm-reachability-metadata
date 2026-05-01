/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.IoUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class IoUtilTest {
    @Test
    public void serializesAndDeserializesObject() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        IoUtil.writeObjects(output, false, "hutool-io");
        String result = IoUtil.readObj(new ByteArrayInputStream(output.toByteArray()), String.class);

        assertThat(result).isEqualTo("hutool-io");
    }
}
