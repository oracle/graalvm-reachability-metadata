/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.apache.tools.zip.AsiExtraField;
import org.apache.tools.zip.ExtraFieldUtils;
import org.apache.tools.zip.ZipExtraField;
import org.junit.jupiter.api.Test;

public class ExtraFieldUtilsTest {
    @Test
    void registersAndRoundTripsUnixExtraField() throws Exception {
        ExtraFieldUtils.register(AsiExtraField.class);
        AsiExtraField field = new AsiExtraField();
        field.setMode(0644);
        field.setUserId(123);
        field.setGroupId(456);
        assertInstanceOf(AsiExtraField.class, ExtraFieldUtils.createExtraField(field.getHeaderId()));
        byte[] encoded = ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] {field});
        ZipExtraField[] decoded = ExtraFieldUtils.parse(encoded);
        assertEquals(1, decoded.length);
        AsiExtraField actual = (AsiExtraField) decoded[0];
        assertEquals(field.getMode(), actual.getMode());
        assertEquals(123, actual.getUserId());
        assertEquals(456, actual.getGroupId());
    }
}
