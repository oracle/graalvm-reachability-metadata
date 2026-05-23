/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_nio

import groovy.lang.Writable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

public class WritablePathTest {
    @Test
    void writesPathContentAndDelegatesPathOperations(@TempDir Path tempDir) {
        Path source = tempDir.resolve('source.txt')
        source.text = 'writable path content'

        Writable writable = source as Writable
        assertTrue(writable instanceof Path)

        Path writablePath = source.asWritable('UTF-8')
        assertTrue(writablePath instanceof Writable)
        assertEquals(source.fileSystem, writablePath.fileSystem)
        assertEquals(source.absolute, writablePath.absolute)
        assertEquals(source.root, writablePath.root)
        assertEquals(source.fileName, writablePath.fileName)
        assertEquals(source.parent, writablePath.parent)
        assertEquals(source.nameCount, writablePath.nameCount)
        assertEquals(source.getName(0), writablePath.getName(0))
        assertEquals(source.subpath(0, source.nameCount), writablePath.subpath(0, writablePath.nameCount))
        assertTrue(writablePath.startsWith(source.parent))
        assertTrue(writablePath.startsWith(source.parent.toString()))
        assertTrue(writablePath.endsWith(source.fileName))
        assertTrue(writablePath.endsWith(source.fileName.toString()))
        assertEquals(source.normalize(), writablePath.normalize())
        assertEquals(source.resolve('child'), writablePath.resolve('child'))
        assertEquals(source.resolve(source.fileName), writablePath.resolve(source.fileName))
        assertEquals(source.resolveSibling('sibling.txt'), writablePath.resolveSibling('sibling.txt'))
        assertEquals(source.resolveSibling(source.fileName), writablePath.resolveSibling(source.fileName))

        Path sibling = source.resolveSibling('sibling.txt')
        assertEquals(source.relativize(sibling), writablePath.relativize(sibling))
        assertEquals(source.toUri(), writablePath.toUri())
        assertEquals(source.toAbsolutePath(), writablePath.toAbsolutePath())
        assertEquals(source.toRealPath(), writablePath.toRealPath())
        assertEquals(source.toFile(), writablePath.toFile())

        List<String> sourceNames = source.iterator().collect { sourceName -> sourceName.toString() }
        List<String> writableNames = writablePath.iterator().collect { writableName -> writableName.toString() }
        assertEquals(sourceNames, writableNames)
        assertEquals(0, writablePath.compareTo(source))
        assertEquals(writablePath, source)
        assertEquals(source.hashCode(), writablePath.hashCode())
        assertEquals(source.toString(), writablePath.toString())
    }

    @Test
    void registersWatchServiceThroughWritablePathDelegate(@TempDir Path tempDir) {
        Path watchedDirectory = tempDir.resolve('watched')
        Files.createDirectories(watchedDirectory)
        Path writableDirectory = watchedDirectory.asWritable()

        WatchService watchService = watchedDirectory.fileSystem.newWatchService()
        try {
            WatchKey key = writableDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
            assertTrue(key.valid)
            key.cancel()
        } finally {
            watchService.close()
        }
    }
}
