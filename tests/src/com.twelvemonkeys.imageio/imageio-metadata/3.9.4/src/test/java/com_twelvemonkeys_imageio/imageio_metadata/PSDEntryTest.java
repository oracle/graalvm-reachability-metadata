/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_imageio.imageio_metadata;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.psd.PSD;
import com.twelvemonkeys.imageio.metadata.psd.PSDReader;
import org.junit.jupiter.api.Test;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class PSDEntryTest {
    @Test
    void resolvesKnownResourceNameAfterParsingPhotoshopMetadata() throws Exception {
        byte[] resourceData = {1, 2, 3};
        byte[] encodedResource;

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(PSD.RESOURCE_TYPE);
            output.writeShort(PSD.RES_IPTC_NAA);
            output.writeByte(4);
            output.writeBytes("Wire");
            output.writeByte(0);
            output.writeInt(resourceData.length);
            output.write(resourceData);
            output.writeByte(0);
            encodedResource = bytes.toByteArray();
        }

        Directory directory;
        try (ImageInputStream input =
                        new MemoryCacheImageInputStream(new ByteArrayInputStream(encodedResource))) {
            directory = new PSDReader().read(input);
        }

        Entry entry = directory.getEntryById(PSD.RES_IPTC_NAA);
        assertThat(directory.size()).isEqualTo(1);
        assertThat(entry).isNotNull();
        assertThat(entry.getIdentifier()).isEqualTo(PSD.RES_IPTC_NAA);
        assertThat((byte[]) entry.getValue()).containsExactly(resourceData);
        assertThat(entry.getFieldName()).isEqualTo("IptcNaa: Wire");
    }
}
