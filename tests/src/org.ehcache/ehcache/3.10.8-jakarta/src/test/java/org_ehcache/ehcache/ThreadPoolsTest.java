/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.PooledExecutionServiceConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.impl.config.persistence.CacheManagerPersistenceConfiguration;
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

public class ThreadPoolsTest {

    @Test
    void testDiskStore() {
        String dirPathString = "src/test/resources/diskStoreData/";
        CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .using(PooledExecutionServiceConfigurationBuilder.newPooledExecutionServiceConfigurationBuilder()
                        .defaultPool("default", 0, 10)
                        .pool("defaultDiskPool", 1, 3).pool("cache2Pool", 2, 2).build())
                .with(new CacheManagerPersistenceConfiguration(new File(dirPathString)))
                .withDefaultDiskStoreThreadPool("defaultDiskPool")
                .withCache("cache1",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).disk(10L, MemoryUnit.MB)))
                .withCache("cache2",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                                        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).disk(10L, MemoryUnit.MB))
                                .withDiskStoreThreadPool("cache2Pool", 2))
                .build(true);
        assertThat(cacheManager.getCache("cache1", Long.class, String.class)).isNotNull();
        assertThat(cacheManager.getCache("cache2", Long.class, String.class)).isNotNull();
        cacheManager.close();
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
