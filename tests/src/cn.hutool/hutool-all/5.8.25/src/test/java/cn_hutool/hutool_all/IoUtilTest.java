/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.ValidateObjectInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class IoUtilTest {
    @Test
    void serializesAndDeserializesObjectsThroughIoUtil() throws IOException {
        Payload first = new Payload("alpha", 1);
        Payload second = new Payload("beta", 2);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        IoUtil.writeObjects(output, true, first, second);

        try (ValidateObjectInputStream input = new ValidateObjectInputStream(
                new ByteArrayInputStream(output.toByteArray()), Payload.class)) {
            Payload readFirst = IoUtil.readObj(input, Payload.class);
            Payload readSecond = IoUtil.readObj(input, Payload.class);

            assertThat(readFirst.name()).isEqualTo("alpha");
            assertThat(readFirst.count()).isEqualTo(1);
            assertThat(readSecond.name()).isEqualTo("beta");
            assertThat(readSecond.count()).isEqualTo(2);
        }
    }

    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        public Payload(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String name() {
            return name;
        }

        public int count() {
            return count;
        }
    }
}
