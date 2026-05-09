/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import org.jets3t.service.utils.ByteFormatter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteFormatterTest {
    @Test
    public void formatsByteSizesWithCustomSuffixes() {
        ByteFormatter formatter = new ByteFormatter(" GiB", " MiB", " KiB", " bytes", 0);

        assertThat(formatter.formatByteSize(0)).isEqualTo("0 bytes");
        assertThat(formatter.formatByteSize(1024)).isEqualTo("1024 bytes");
        assertThat(formatter.formatByteSize(2L * 1024L)).isEqualTo("2 KiB");
        assertThat(formatter.formatByteSize(2L * 1024L * 1024L)).isEqualTo("2 MiB");
        assertThat(formatter.formatByteSize(2L * 1024L * 1024L * 1024L)).isEqualTo("2 GiB");
    }
}
