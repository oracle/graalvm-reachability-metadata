/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.weaver.CompressingDataOutputStream;
import org.aspectj.weaver.ResolvedTypeMunger;
import org.aspectj.weaver.VersionedDataInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResolvedTypeMungerTest {
    @Test
    void sourceLocationRoundTripsThroughLegacyObjectSerialization() throws IOException {
        File sourceFile = new File("src/test/resources/aspectj/ExampleAspect.aj");
        SourceLocation sourceLocation = new SourceLocation(sourceFile, 37);
        sourceLocation.setOffset(1_337);
        TestResolvedTypeMunger munger = new TestResolvedTypeMunger();
        munger.setSourceLocation(sourceLocation);

        byte[] serializedLocation = munger.writeSourceLocationToBytes();

        ISourceLocation restoredLocation = TestResolvedTypeMunger.readSourceLocationFrom(serializedLocation);
        assertThat(restoredLocation).isNotNull();
        assertThat(restoredLocation.getSourceFile()).isEqualTo(sourceFile);
        assertThat(restoredLocation.getLine()).isEqualTo(sourceLocation.getLine());
        assertThat(restoredLocation.getOffset()).isEqualTo(sourceLocation.getOffset());
    }

    private static final class TestResolvedTypeMunger extends ResolvedTypeMunger {
        private TestResolvedTypeMunger() {
            super(ResolvedTypeMunger.Method, null);
        }

        private byte[] writeSourceLocationToBytes() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (CompressingDataOutputStream output = new CompressingDataOutputStream(bytes, null)) {
                writeSourceLocation(output);
            }
            return bytes.toByteArray();
        }

        private static ISourceLocation readSourceLocationFrom(byte[] bytes) throws IOException {
            try (VersionedDataInputStream input = new VersionedDataInputStream(new ByteArrayInputStream(bytes), null)) {
                return readSourceLocation(input);
            }
        }

        @Override
        public void write(CompressingDataOutputStream output) throws IOException {
            writeSourceLocation(output);
        }
    }
}
