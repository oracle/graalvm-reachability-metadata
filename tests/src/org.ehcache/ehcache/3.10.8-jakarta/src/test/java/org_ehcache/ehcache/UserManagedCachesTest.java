/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.PersistentUserManagedCache;
import org.ehcache.UserManagedCache;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.builders.UserManagedCacheBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.spi.service.LocalPersistenceService;
import org.ehcache.impl.config.persistence.DefaultPersistenceConfiguration;
import org.ehcache.impl.config.persistence.UserManagedPersistenceContext;
import org.ehcache.impl.persistence.DefaultLocalPersistenceService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class UserManagedCachesTest {
    @Test
    void testBasicCacheLifecycle() {
        UserManagedCache<Long, String> userManagedCache = UserManagedCacheBuilder.newUserManagedCacheBuilder(Long.class, String.class).build(false);
        userManagedCache.init();
        userManagedCache.put(1L, "da one!");
        assertThat(userManagedCache.get(1L)).isEqualTo("da one!");
        userManagedCache.close();
    }

    @Test
    void testDiskPersistenceAndLifecycle() {
        String dirPathString = "src/test/resources/myUserData/";
        LocalPersistenceService persistenceService = new DefaultLocalPersistenceService(new DefaultPersistenceConfiguration(new File(dirPathString)));
        PersistentUserManagedCache<Long, String> cache = UserManagedCacheBuilder.newUserManagedCacheBuilder(Long.class, String.class)
                .with(new UserManagedPersistenceContext<>("cache-name", persistenceService))
                .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10L, EntryUnit.ENTRIES).disk(10L, MemoryUnit.MB, true))
                .build(true);
        cache.put(42L, "The Answer!");
        assertThat(cache.get(42L)).isEqualTo("The Answer!");
        cache.close();
        assertDoesNotThrow(cache::destroy);
        persistenceService.stop();
        assertDoesNotThrow(() -> {
            Files.walkFileTree(Paths.get(dirPathString), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        });
    }
}
