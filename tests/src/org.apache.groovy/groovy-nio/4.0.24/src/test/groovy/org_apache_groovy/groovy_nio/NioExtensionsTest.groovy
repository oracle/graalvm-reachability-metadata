/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_nio

import groovy.io.FileType
import groovy.lang.Writable
import org.apache.groovy.nio.extensions.NioExtensions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertTrue

public class NioExtensionsTest {
    @Test
    void readsWritesAndTransformsTextUsingPathExtensionMethods(@TempDir Path tempDir) {
        Path file = tempDir.resolve('sample.txt')
        NioExtensions.setText(file, 'alpha\nbeta\ngamma\n')

        assertEquals('alpha\nbeta\ngamma\n', NioExtensions.getText(file))
        assertEquals(['alpha', 'beta', 'gamma'], NioExtensions.readLines(file))
        assertEquals(17L, NioExtensions.size(file))

        List<String> numberedLines = []
        String eachLineResult = NioExtensions.eachLine(file, 10) { numberedLine, number ->
            numberedLines << "${number}:${numberedLine}".toString()
            'finished'
        }
        assertEquals('finished', eachLineResult)
        assertEquals(['10:alpha', '11:beta', '12:gamma'], numberedLines)

        Path csv = tempDir.resolve('data.csv')
        NioExtensions.write(csv, 'name|value\none|1\ntwo|2\n', 'UTF-8')
        List<List<String>> rows = []
        NioExtensions.splitEachLine(csv, '\\|', 'UTF-8') { parts -> rows << parts }
        assertEquals([['name', 'value'], ['one', '1'], ['two', '2']], rows)

        StringWriter filtered = new StringWriter()
        NioExtensions.filterLine(file, filtered, 'UTF-8') { filteredLine -> filteredLine.startsWith('g') }
        assertTrue(filtered.toString().contains('gamma'))
        assertFalse(filtered.toString().contains('alpha'))

        Writable writable = NioExtensions.filterLine(file) { writableLine -> writableLine.contains('a') }
        StringWriter writableTarget = new StringWriter()
        assertSame(writableTarget, writable.writeTo(writableTarget))
        assertTrue(writableTarget.toString().contains('alpha'))
        assertTrue(writableTarget.toString().contains('gamma'))
    }

    @Test
    void appendsAndStreamsBinaryContentUsingPathExtensionMethods(@TempDir Path tempDir) {
        Path bytes = tempDir.resolve('bytes.bin')
        byte[] initialBytes = [1, 2, 3] as byte[]
        NioExtensions.setBytes(bytes, initialBytes)

        assertArrayEquals(initialBytes, NioExtensions.getBytes(bytes))
        assertArrayEquals(initialBytes, NioExtensions.readBytes(bytes))

        NioExtensions.leftShift(bytes, [4, 5] as byte[])
        NioExtensions.leftShift(bytes, new ByteArrayInputStream([6, 7] as byte[]))
        assertArrayEquals([1, 2, 3, 4, 5, 6, 7] as byte[], NioExtensions.getBytes(bytes))

        List<Integer> values = []
        NioExtensions.eachByte(bytes) { value -> values << ((Number) value).intValue() }
        assertEquals([1, 2, 3, 4, 5, 6, 7], values)

        List<String> chunks = []
        NioExtensions.eachByte(bytes, 3) { buffer, count ->
            chunks << Arrays.toString(Arrays.copyOf(buffer, count))
        }
        assertEquals(['[1, 2, 3]', '[4, 5, 6]', '[7]'], chunks)

        Path copied = tempDir.resolve('copied.bin')
        NioExtensions.withOutputStream(copied) { copiedOutput -> copiedOutput.write(NioExtensions.getBytes(bytes)) }
        int firstByte = NioExtensions.withInputStream(copied) { copiedInput -> copiedInput.read() }
        assertEquals(1, firstByte)

        Path data = tempDir.resolve('data.bin')
        NioExtensions.withDataOutputStream(data) { dataOutput ->
            dataOutput.writeInt(123456)
            dataOutput.writeUTF('groovy-nio')
        }
        Map<String, Object> decoded = NioExtensions.withDataInputStream(data) { dataInput ->
            [number: dataInput.readInt(), label: dataInput.readUTF()]
        }
        assertEquals([number: 123456, label: 'groovy-nio'], decoded)
    }

    @Test
    void createsReadersWritersAndPrintWritersForPaths(@TempDir Path tempDir) {
        Path file = tempDir.resolve('io.txt')

        NioExtensions.withWriter(file, 'UTF-8') { firstWriter -> firstWriter.write('first') }
        NioExtensions.withWriterAppend(file, 'UTF-8') { secondWriter -> secondWriter.write('\nsecond') }
        assertEquals(['first', 'second'], NioExtensions.readLines(file, 'UTF-8'))

        String content = NioExtensions.withReader(file, 'UTF-8') { contentReader -> contentReader.text }
        assertEquals('first\nsecond', content)

        NioExtensions.withPrintWriter(file, 'UTF-8') { printWriter ->
            printWriter.println('printed')
            printWriter.print('tail')
        }
        assertEquals('printed\ntail', NioExtensions.getText(file, 'UTF-8').replace('\r\n', '\n'))

        BufferedWriter bufferedWriter = NioExtensions.newWriter(file, 'UTF-8', true)
        try {
            bufferedWriter.write('\nnew-writer')
        } finally {
            bufferedWriter.close()
        }
        BufferedReader bufferedReader = NioExtensions.newReader(file, 'UTF-8')
        try {
            assertEquals('printed', bufferedReader.readLine())
        } finally {
            bufferedReader.close()
        }
        assertTrue(NioExtensions.getText(file, 'UTF-8').contains('new-writer'))
    }

    @Test
    void traversesAndMatchesDirectoryContentsUsingPathExtensionMethods(@TempDir Path tempDir) {
        Path root = tempDir.resolve('root')
        Path child = root.resolve('child')
        Path other = root.resolve('other')
        Files.createDirectories(child)
        Files.createDirectories(other)
        NioExtensions.setText(child.resolve('alpha.txt'), 'alpha')
        NioExtensions.setText(child.resolve('skip.log'), 'skip')
        NioExtensions.setText(other.resolve('beta.txt'), 'beta')
        NioExtensions.setText(root.resolve('top.txt'), 'top')

        List<String> immediateFiles = []
        NioExtensions.eachFile(root, FileType.FILES) { immediateFile -> immediateFiles << immediateFile.fileName.toString() }
        assertEquals(['top.txt'], immediateFiles.sort())

        List<String> immediateDirs = []
        NioExtensions.eachDir(root) { immediateDir -> immediateDirs << immediateDir.fileName.toString() }
        assertEquals(['child', 'other'], immediateDirs.sort())

        List<String> recursiveFiles = []
        NioExtensions.eachFileRecurse(root, FileType.FILES) { recursiveFile ->
            recursiveFiles << root.relativize(recursiveFile).toString()
        }
        List<String> expectedRecursiveFiles = ['child/alpha.txt', 'child/skip.log', 'other/beta.txt', 'top.txt']
        assertEquals(expectedRecursiveFiles, normalizeAndSort(recursiveFiles))

        List<String> matchedFiles = []
        NioExtensions.eachFileMatch(child, FileType.FILES, ~/.*\.txt/) { matchedFile ->
            matchedFiles << matchedFile.fileName.toString()
        }
        assertEquals(['alpha.txt'], matchedFiles)

        List<String> traversed = []
        NioExtensions.traverse(root, [type: FileType.FILES, nameFilter: ~/.*\.txt/]) { traversedFile ->
            traversed << root.relativize(traversedFile).toString()
        }
        assertEquals(['child/alpha.txt', 'other/beta.txt', 'top.txt'], normalizeAndSort(traversed))
    }

    @Test
    void managesDirectoriesAndRenamesUsingPathExtensionMethods(@TempDir Path tempDir) {
        Path nested = tempDir.resolve('parents/child/file.txt')
        assertSame(nested, NioExtensions.createParentDirectories(nested))
        NioExtensions.setText(nested, 'created')
        assertTrue(Files.isDirectory(nested.parent))
        assertEquals('created', NioExtensions.getText(nested))

        Path original = tempDir.resolve('original.txt')
        Path renamed = tempDir.resolve('renamed.txt')
        NioExtensions.setText(original, 'rename me')
        assertTrue(NioExtensions.renameTo(original, renamed.toString()))
        assertFalse(Files.exists(original))
        assertEquals('rename me', NioExtensions.getText(renamed))

        Path deleteRoot = tempDir.resolve('delete-me')
        Files.createDirectories(deleteRoot.resolve('nested'))
        NioExtensions.setText(deleteRoot.resolve('nested/file.txt'), 'remove')
        assertTrue(NioExtensions.deleteDir(deleteRoot))
        assertFalse(Files.exists(deleteRoot))
        assertTrue(NioExtensions.deleteDir(tempDir.resolve('missing')))
        assertFalse(NioExtensions.deleteDir(renamed))
    }

    private static List<String> normalizeAndSort(List<String> values) {
        values.collect { pathText -> pathText.replace(File.separator, '/') }.sort()
    }
}
