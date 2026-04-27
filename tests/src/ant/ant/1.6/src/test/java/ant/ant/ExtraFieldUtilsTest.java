/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.zip.AsiExtraField;
import org.apache.tools.zip.ExtraFieldUtils;
import org.apache.tools.zip.ZipExtraField;
import org.junit.jupiter.api.Test;

public class ExtraFieldUtilsTest {
    @Test
    void parsesRegisteredAsiExtraFieldFromMergedLocalFileData() throws Exception {
        AsiExtraField original = new AsiExtraField();
        original.setUserId(1001);
        original.setGroupId(1002);
        original.setMode(0644);
        original.setLinkedFile("target.txt");

        ExtraFieldUtils.register(AsiExtraField.class);
        byte[] merged = ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] {original});

        ZipExtraField[] parsed = ExtraFieldUtils.parse(merged);

        assertThat(parsed).hasSize(1);
        assertThat(parsed[0]).isInstanceOf(AsiExtraField.class);
        AsiExtraField parsedField = (AsiExtraField) parsed[0];
        assertThat(parsedField.getUserId()).isEqualTo(original.getUserId());
        assertThat(parsedField.getGroupId()).isEqualTo(original.getGroupId());
        assertThat(parsedField.getLinkedFile()).isEqualTo(original.getLinkedFile());
        assertThat(parsedField.isLink()).isTrue();
    }
}
