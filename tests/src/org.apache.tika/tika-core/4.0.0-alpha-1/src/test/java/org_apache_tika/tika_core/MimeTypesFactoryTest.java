/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;

public class MimeTypesFactoryTest {

    @Test
    public void createsMimeTypesFromPackageResourcePath() throws Exception {
        MimeTypes mimeTypes = MimeTypesFactory.create("tika-mimetypes.xml");

        assertCommonTypes(mimeTypes);
    }

    @Test
    public void createsMimeTypesWithExplicitClassLoader() throws Exception {
        ClassLoader classLoader = MimeTypesFactoryTest.class.getClassLoader();
        MimeTypes mimeTypes = MimeTypesFactory.create(
                "tika-mimetypes.xml", "custom-mimetypes.xml", classLoader);

        assertCommonTypes(mimeTypes);
    }

    private static void assertCommonTypes(MimeTypes mimeTypes) throws Exception {
        MimeType pdf = mimeTypes.getRegisteredMimeType("application/pdf");
        assertThat(pdf).isNotNull();
        assertThat(pdf.getExtension()).isEqualTo(".pdf");
        assertThat(mimeTypes.getRegisteredMimeType("text/plain")).isNotNull();
    }
}
