/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class ByteBufferWriterCoverageSupport {
    private ByteBufferWriterCoverageSupport() {
    }

    public static void write(ByteBuffer buffer, OutputStream output) throws IOException {
        ByteBufferWriter.write(buffer, output);
    }
}
