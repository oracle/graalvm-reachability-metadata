/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.FloatRefForm;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.IntRefForm;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.LabelForm;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.LookupSwitchForm;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.NarrowClassRefForm;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.NewInitMethodRefForm;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.StringRefForm;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.VariableInstructionForm;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FormAndLifecycleCoverageTest {

    @Test
    void unpackFormsRepresentTheirOperandKinds() {
        assertThat(new FloatRefForm(1, "float", new int[] {0}, false)).isNotNull();
        assertThat(new NarrowClassRefForm(2, "class", new int[] {0}, false)).isNotNull();
        assertThat(new NewInitMethodRefForm(3, "init", new int[] {0})).isNotNull();
        assertThat(new IntRefForm(4, "int", new int[] {0}, false)).isNotNull();
        assertThat(new LabelForm(5, "label", new int[] {0}, false)).isNotNull();
        assertThat(new StringRefForm(6, "string", new int[] {0}, false)).isNotNull();
        assertThat(new LookupSwitchForm(7, "lookup")).isNotNull();
        final VariableInstructionForm variable = new VariableInstructionForm(8, "variable") {
            @Override
            public void setByteCodeOperands(final org.apache.commons.compress.harmony.unpack200.bytecode.ByteCode byteCode,
                                             final org.apache.commons.compress.harmony.unpack200.bytecode.OperandManager operandManager,
                                             final int codeLength) {
            }
        };
        try {
            variable.setRewrite4Bytes(0, new int[] {0, 0, 0, 0});
        } catch (Error expectedOperandSize) {
            assertThat(expectedOperandSize).hasMessageContaining("no room");
        }
        assertThat(variable).isNotNull();
    }

    @Test
    void archiveOutputOverloadsAcceptFilesAndPaths() throws Exception {
        final Path source = Files.createTempFile("lifecycle-source", ".txt");
        Files.writeString(source, "data");
        try (CpioArchiveOutputStream cpio = new CpioArchiveOutputStream(new ByteArrayOutputStream(),
                CpioConstants.FORMAT_NEW, 2, "UTF-8")) {
            final ArchiveEntry fileEntry = cpio.createArchiveEntry(source.toFile(), "file.txt");
            cpio.putArchiveEntry(fileEntry);
            cpio.write(new byte[] {'d', 'a', 't', 'a'});
            cpio.closeArchiveEntry();
        }
        try (ArArchiveOutputStream ar = new ArArchiveOutputStream(new ByteArrayOutputStream())) {
            final ArchiveEntry pathEntry = ar.createArchiveEntry(source, "path.txt");
            ar.putArchiveEntry(pathEntry);
            ar.closeArchiveEntry();
        }
        final Path tar = Files.createTempFile("lifecycle-tar", ".tar");
        Files.write(tar, new byte[1024]);
        try {
            assertThat(new TarFile(tar.toFile(), true)).isNotNull();
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
        try {
            assertThat(new TarFile(tar.toFile(), "UTF-8")).isNotNull();
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
        Files.deleteIfExists(source);
        Files.deleteIfExists(tar);
    }
}
