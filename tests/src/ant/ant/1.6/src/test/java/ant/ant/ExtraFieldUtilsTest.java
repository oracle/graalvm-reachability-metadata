/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.zip.AsiExtraField;
import org.apache.tools.zip.ExtraFieldUtils;
import org.apache.tools.zip.ZipExtraField;
import org.apache.tools.zip.ZipShort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFieldUtilsTest {

    @Test
    void registerMakesAsiExtraFieldAvailableForRecreation() throws Exception {
        AsiExtraField registeredField = new AsiExtraField();
        registeredField.setUserId(123);
        registeredField.setGroupId(456);
        registeredField.setLinkedFile("target-file");
        ZipShort headerId = registeredField.getHeaderId();

        ExtraFieldUtils.register(AsiExtraField.class);
        ZipExtraField recreatedField = ExtraFieldUtils.createExtraField(headerId);
        recreatedField.parseFromLocalFileData(
                registeredField.getLocalFileDataData(),
                0,
                registeredField.getLocalFileDataLength().getValue());

        assertThat(recreatedField).isInstanceOf(AsiExtraField.class);
        assertThat(recreatedField).isNotSameAs(registeredField);
        assertThat(recreatedField.getHeaderId()).isEqualTo(headerId);
        assertThat(((AsiExtraField) recreatedField).getUserId()).isEqualTo(123);
        assertThat(((AsiExtraField) recreatedField).getGroupId()).isEqualTo(456);
        assertThat(((AsiExtraField) recreatedField).getLinkedFile()).isEqualTo("target-file");
    }
}
