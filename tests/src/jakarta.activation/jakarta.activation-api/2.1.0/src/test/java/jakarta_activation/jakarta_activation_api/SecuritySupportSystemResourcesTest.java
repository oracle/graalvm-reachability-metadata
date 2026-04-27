/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import jakarta.activation.MimetypesFileTypeMap;
import jakarta.activation.spi.MimeTypeRegistryProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupportSystemResourcesTest {
    private static final String ANGUS_MIME_TYPE_REGISTRY_PROVIDER =
            "org.eclipse.angus.activation.MimeTypeRegistryProviderImpl";

    @Test
    void mimetypeLoadingWithoutContextClassLoaderUsesSystemResourcesFallback() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        String originalProvider = System.getProperty(MimeTypeRegistryProvider.class.getName());

        currentThread.setContextClassLoader(null);
        System.setProperty(MimeTypeRegistryProvider.class.getName(), ANGUS_MIME_TYPE_REGISTRY_PROVIDER);
        try {
            MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap(
                    mimeTypes("application/x-security-support-system-resource securitysupportsystemresource")
            );

            assertThat(fileTypeMap.getContentType("sample.securitysupportsystemresource"))
                    .isEqualTo("application/x-security-support-system-resource");
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
            if (originalProvider == null) {
                System.clearProperty(MimeTypeRegistryProvider.class.getName());
            } else {
                System.setProperty(MimeTypeRegistryProvider.class.getName(), originalProvider);
            }
        }
    }

    private static InputStream mimeTypes(String content) {
        return new ByteArrayInputStream((content + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }
}
