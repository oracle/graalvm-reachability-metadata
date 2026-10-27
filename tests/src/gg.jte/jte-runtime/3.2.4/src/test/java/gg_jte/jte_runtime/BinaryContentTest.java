/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import gg.jte.runtime.BinaryContent;
import org.junit.jupiter.api.Test;

public class BinaryContentTest {
    @Test
    void loadsResourceContentInConfiguredChunks() {
        BinaryContent content = BinaryContent.load(
                BinaryContentTest.class, "binary-content.txt", 4, 6, 8);

        assertThat(content.get(0)).containsExactly((byte) 'j', (byte) 't', (byte) 'e', (byte) '-');
        assertThat(content.get(1)).containsExactly(
                (byte) 'b', (byte) 'i', (byte) 'n', (byte) 'a', (byte) 'r', (byte) 'y');
        assertThat(content.get(2)).containsExactly(
                (byte) '-',
                (byte) 'c',
                (byte) 'o',
                (byte) 'n',
                (byte) 't',
                (byte) 'e',
                (byte) 'n',
                (byte) 't');
    }
}
