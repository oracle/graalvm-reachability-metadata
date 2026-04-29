/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.weaver.CompressingDataOutputStream;
import org.aspectj.weaver.ResolvedTypeMunger;
import org.aspectj.weaver.VersionedDataInputStream;
import org.junit.jupiter.api.Test;

public class ResolvedTypeMungerTest {
    @Test
    void serializesAndDeserializesSourceLocationUsingLegacyObjectStreams() throws IOException {
        File sourceFile = Paths.get("src", "test", "java", "GeneratedAspect.java").toFile();
        SourceLocation sourceLocation = new SourceLocation(sourceFile, 42);
        sourceLocation.setOffset(1234);

        TestTypeMunger munger = new TestTypeMunger();
        munger.setSourceLocation(sourceLocation);

        ISourceLocation restored = TestTypeMunger.readLocation(munger.writeLocation());

        assertThat(restored).isNotNull();
        assertThat(restored.getSourceFile()).isEqualTo(sourceFile);
        assertThat(restored.getLine()).isEqualTo(42);
        assertThat(restored.getOffset()).isEqualTo(1234);
    }

    private static class TestTypeMunger extends ResolvedTypeMunger {
        TestTypeMunger() {
            super(ResolvedTypeMunger.Method, null);
        }

        byte[] writeLocation() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressingDataOutputStream output = new CompressingDataOutputStream(bytes, null);
            write(output);
            output.flush();
            return bytes.toByteArray();
        }

        static ISourceLocation readLocation(byte[] bytes) throws IOException {
            VersionedDataInputStream input = new VersionedDataInputStream(new ByteArrayInputStream(bytes), null);
            return readSourceLocation(input);
        }

        @Override
        public void write(CompressingDataOutputStream output) throws IOException {
            writeSourceLocation(output);
        }
    }
}
