/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.File;

import org.jets3t.service.utils.Mimetypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MimetypesTest {
    @Test
    public void loadsMimetypesFromClasspathResource() {
        Mimetypes mimetypes = Mimetypes.getInstance();

        assertThat(mimetypes.getMimetype("index.coveragehtml")).isEqualTo("text/x-coverage-html");
        assertThat(mimetypes.getMimetype(new File("archive.coveragebin"))).isEqualTo("application/x-coverage-binary");
        assertThat(mimetypes.getMimetype("unknown.extension")).isEqualTo(Mimetypes.MIMETYPE_OCTET_STREAM);
        assertThat(mimetypes.getMimetype("filename-without-extension")).isEqualTo(Mimetypes.MIMETYPE_OCTET_STREAM);
    }
}
