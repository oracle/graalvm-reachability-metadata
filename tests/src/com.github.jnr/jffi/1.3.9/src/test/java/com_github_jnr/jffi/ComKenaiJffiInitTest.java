/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.Type;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ComKenaiJffiInitTest {
    @Test
    void reportsConfiguredExtractDirectoryThatCannotReceiveTheNativeStub(
            @TempDir Path temporaryDirectory) {
        Path missingExtractDirectory = temporaryDirectory.resolve("missing-extract-directory");
        String previousExtractDirectory = System.getProperty("jffi.extract.dir");

        try {
            System.setProperty("jffi.extract.dir", missingExtractDirectory.toString());

            assertThatThrownBy(() -> Type.SINT32.size())
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasMessageContaining("could not get native definition for type `SINT32`")
                    .hasMessageContaining("could not load jffi library from")
                    .hasMessageContaining(missingExtractDirectory.toString());
        } finally {
            if (previousExtractDirectory == null) {
                System.clearProperty("jffi.extract.dir");
            } else {
                System.setProperty("jffi.extract.dir", previousExtractDirectory);
            }
        }
    }
}
