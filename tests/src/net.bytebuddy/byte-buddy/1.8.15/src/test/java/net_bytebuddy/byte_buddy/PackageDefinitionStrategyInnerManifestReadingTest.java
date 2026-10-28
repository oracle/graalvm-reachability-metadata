/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.InputStream;

import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackageDefinitionStrategyInnerManifestReadingTest {
    public static class ResourceLoader extends ClassLoader {
        public ResourceLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return super.getResourceAsStream(name.startsWith("/") ? name.substring(1) : name);
        }
    }

    @Test
    void combinesMainAndPackageManifestAttributes() {
        ClassLoader loader = new ResourceLoader(getClass().getClassLoader());
        PackageDefinitionStrategy.Definition definition = new PackageDefinitionStrategy.ManifestReading()
                .define(loader, "net_bytebuddy.byte_buddy", TestFixtures.class.getName());
        assertTrue(definition.isDefined());
        assertEquals("Fixture package", definition.getSpecificationTitle());
        assertEquals("Fixture vendor", definition.getImplementationVendor());
        assertEquals("Fixture implementation", definition.getImplementationTitle());
        assertNull(definition.getSealBase());
    }
}
