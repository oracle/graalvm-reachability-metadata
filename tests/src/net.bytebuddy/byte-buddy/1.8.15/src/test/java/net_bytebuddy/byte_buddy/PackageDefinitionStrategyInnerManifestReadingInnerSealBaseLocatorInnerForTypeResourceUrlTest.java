/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.net.URI;
import java.net.URL;

import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy.ManifestReading.SealBaseLocator;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PackageDefinitionStrategyInnerManifestReadingInnerSealBaseLocatorInnerForTypeResourceUrlTest {
    @Test
    void locatesSealBaseFromClasspathResourceOrConfiguredFallback() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        URL fallback = URI.create("file:/byte-buddy-seal-base").toURL();
        SealBaseLocator locator = new SealBaseLocator.ForTypeResourceUrl(new SealBaseLocator.ForFixedValue(fallback));
        URL resource = loader.getResource("net/bytebuddy/utility/JavaModule.class");
        assertNotNull(resource);
        String expected = switch (resource.getProtocol()) {
            case "jar" -> resource.getPath().substring(0, resource.getPath().indexOf('!'));
            case "file" -> resource.toExternalForm();
            default -> fallback.toExternalForm();
        };
        assertEquals(expected, locator.findSealBase(loader, JavaModule.class.getName()).toExternalForm());
        assertEquals(fallback.toExternalForm(), locator.findSealBase(loader, "missing.bytebuddy.NoSuchType").toExternalForm());
    }
}
