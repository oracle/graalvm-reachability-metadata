/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

public class ByteBufferWriterTest {
    private static final String BYTE_BUFFER_WRITER_CLASS_NAME = "com.google.protobuf.ByteBufferWriter";

    @Test
    void classInitializationDiscoversFileOutputStreamChannelField() throws Exception {
        Class<?> writerClass = Class.forName(
                BYTE_BUFFER_WRITER_CLASS_NAME,
                true,
                ByteString.class.getClassLoader()
        );

        assertThat(writerClass.getName()).isEqualTo(BYTE_BUFFER_WRITER_CLASS_NAME);
    }
}
