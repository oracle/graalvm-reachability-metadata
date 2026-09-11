/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.MemoryLimitException;
import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.StreamingNotSupportedException;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.arj.ArjArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.compress.archivers.dump.DumpArchiveConstants;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.apache.commons.compress.archivers.dump.DumpArchiveException;
import org.apache.commons.compress.compressors.bzip2.BZip2Utils;
import org.apache.commons.compress.utils.ArchiveUtils;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.compress.utils.CountingOutputStream;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.compress.utils.TimeUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

class EntryModelsCoverageTest {

    @Test
    void cpioEntriesExposeConfiguredPortableMetadata() throws Exception {
        final Path file = Files.createTempFile("compress-entry", ".txt");
        Files.writeString(file, "payload", StandardCharsets.UTF_8);
        final CpioArchiveEntry fromPath = new CpioArchiveEntry(file, "path.txt");
        final CpioArchiveEntry fromFile = new CpioArchiveEntry(file.toFile(), "file.txt");
        final CpioArchiveEntry named = new CpioArchiveEntry(CpioConstants.FORMAT_OLD_ASCII, "named", 7);
        final CpioArchiveEntry format = new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        final CpioArchiveEntry formatName = new CpioArchiveEntry(CpioConstants.FORMAT_NEW, "format");
        final CpioArchiveEntry formatSize = new CpioArchiveEntry(CpioConstants.FORMAT_NEW, "sized", 3);
        final CpioArchiveEntry formatFile = new CpioArchiveEntry(CpioConstants.FORMAT_NEW, file.toFile(), "ff");
        final CpioArchiveEntry formatPath = new CpioArchiveEntry(CpioConstants.FORMAT_NEW, file, "fp");
        assertThat(fromPath.getName()).isEqualTo("path.txt");
        assertThat(fromFile.getSize()).isEqualTo(7);
        assertThat(named.getSize()).isEqualTo(7);
        assertThat(format.getFormat()).isEqualTo(CpioConstants.FORMAT_NEW);
        assertThat(formatName.getName()).isEqualTo("format");
        assertThat(formatSize.getSize()).isEqualTo(3);
        assertThat(formatFile.getName()).isEqualTo("ff");
        assertThat(formatPath.getName()).isEqualTo("fp");

        final CpioArchiveEntry newFormatEntry = new CpioArchiveEntry("checksum", 1);
        newFormatEntry.setChksum(1);
        assertThat(newFormatEntry.getChksum()).isEqualTo(1);
        named.setDevice(2);
        newFormatEntry.setDeviceMaj(3);
        newFormatEntry.setDeviceMin(4);
        named.setGID(5);
        named.setInode(6);
        named.setMode(0100644);
        named.setName("renamed");
        named.setNumberOfLinks(2);
        named.setRemoteDevice(7);
        newFormatEntry.setRemoteDeviceMaj(8);
        newFormatEntry.setRemoteDeviceMin(9);
        named.setSize(10);
        named.setTime(11);
        named.setTime(FileTime.fromMillis(12_000));
        named.setUID(13);
        try {
            named.getChksum();
        } catch (UnsupportedOperationException expected) {
            assertThat(expected).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(named.getDevice()).isEqualTo(2);
        assertThat(newFormatEntry.getDeviceMaj()).isEqualTo(3);
        assertThat(newFormatEntry.getDeviceMin()).isEqualTo(4);
        assertThat(named.getGID()).isEqualTo(5);
        assertThat(named.getInode()).isEqualTo(6);
        assertThat(named.getMode()).isEqualTo(0100644);
        assertThat(named.getName()).isEqualTo("renamed");
        assertThat(named.getNumberOfLinks()).isEqualTo(2);
        assertThat(named.getRemoteDevice()).isEqualTo(7);
        assertThat(newFormatEntry.getRemoteDeviceMaj()).isEqualTo(8);
        assertThat(newFormatEntry.getRemoteDeviceMin()).isEqualTo(9);
        assertThat(named.getSize()).isEqualTo(10);
        assertThat(named.getUID()).isEqualTo(13);
        assertThat(named.getTime()).isGreaterThan(0);
        assertThat(named.getLastModifiedDate()).isNotNull();
        assertThat(named.getHeaderSize()).isPositive();
        assertThat(named.getHeaderPadCount()).isGreaterThanOrEqualTo(0);
        assertThat(named.getHeaderPadCount(StandardCharsets.UTF_8)).isGreaterThanOrEqualTo(0);
        assertThat(named.getHeaderPadCount(10)).isGreaterThanOrEqualTo(0);
        assertThat(named.getDataPadCount()).isGreaterThanOrEqualTo(0);
        assertThat(named.getAlignmentBoundary()).isGreaterThanOrEqualTo(0);
        assertThat(named.isRegularFile()).isTrue();
        assertThat(named.isDirectory()).isFalse();
        assertThat(named.isBlockDevice()).isFalse();
        assertThat(named.isCharacterDevice()).isFalse();
        assertThat(named.isNetwork()).isFalse();
        assertThat(named.isPipe()).isFalse();
        assertThat(named.isSocket()).isFalse();
        assertThat(named.isSymbolicLink()).isFalse();
        assertThat(named).isEqualTo(new CpioArchiveEntry(CpioConstants.FORMAT_OLD_ASCII, "renamed", 10));
        assertThat(named.hashCode()).isEqualTo(new CpioArchiveEntry(CpioConstants.FORMAT_OLD_ASCII, "renamed", 10).hashCode());
        Files.deleteIfExists(file);
    }

    @Test
    void dumpEntriesRoundTripStateAndEnums() {
        final DumpArchiveEntry entry = new DumpArchiveEntry("dir/file", "file");
        final Date date = new Date(123_000);
        entry.setAccessTime(date);
        entry.setCreationTime(date);
        entry.setDeleted(true);
        entry.setGeneration(4);
        entry.setGroupId(5);
        entry.setLastModifiedDate(date);
        entry.setMode(0100644);
        entry.setName("dir/file");
        entry.setNlink(2);
        entry.setOffset(6);
        entry.setSize(7);
        entry.setType(DumpArchiveEntry.TYPE.FILE);
        entry.setUserId(8);
        entry.setVolume(9);
        assertThat(entry.getAccessTime()).isEqualTo(date);
        assertThat(entry.getCreationTime()).isEqualTo(date);
        assertThat(entry.getGeneration()).isEqualTo(4);
        assertThat(entry.getGroupId()).isEqualTo(5);
        assertThat(entry.getLastModifiedDate()).isEqualTo(date);
        assertThat(entry.getMode()).isEqualTo(0644);
        assertThat(entry.getName()).isEqualTo("dir/file");
        assertThat(entry.getSimpleName()).isEqualTo("file");
        assertThat(entry.getNlink()).isEqualTo(2);
        assertThat(entry.getOffset()).isEqualTo(6);
        assertThat(entry.getSize()).isEqualTo(7);
        assertThat(entry.getType()).isEqualTo(DumpArchiveEntry.TYPE.FILE);
        assertThat(entry.getUserId()).isEqualTo(8);
        assertThat(entry.getVolume()).isEqualTo(9);
        assertThat(entry.isDeleted()).isTrue();
        assertThat(entry.isFile()).isTrue();
        assertThat(entry.isDirectory()).isFalse();
        assertThat(entry.isBlkDev()).isFalse();
        assertThat(entry.isChrDev()).isFalse();
        assertThat(entry.isFifo()).isFalse();
        assertThat(entry.isSocket()).isFalse();
        assertThat(entry.isSparseRecord(0)).isTrue();
        assertThat(entry.getPermissions()).isNotEmpty();
        assertThat(entry.toString()).contains("dir/file");
        assertThat(entry).isEqualTo(new DumpArchiveEntry("dir/file", "file"));
        assertThat(entry.hashCode()).isEqualTo(new DumpArchiveEntry("dir/file", "file").hashCode());
        assertThat(new DumpArchiveEntry()).isNotNull();
        assertThat(DumpArchiveEntry.TYPE.find(4)).isEqualTo(DumpArchiveEntry.TYPE.DIRECTORY);
        assertThat(DumpArchiveEntry.TYPE.values()).contains(DumpArchiveEntry.TYPE.FILE);
        assertThat(DumpArchiveEntry.TYPE.valueOf("FILE")).isEqualTo(DumpArchiveEntry.TYPE.FILE);
        assertThat(DumpArchiveEntry.PERMISSION.find(0400)).contains(DumpArchiveEntry.PERMISSION.USER_READ);
        assertThat(DumpArchiveEntry.PERMISSION.values()).isNotEmpty();
        assertThat(DumpArchiveEntry.PERMISSION.valueOf("WORLD_READ")).isEqualTo(DumpArchiveEntry.PERMISSION.WORLD_READ);
        assertThat(DumpArchiveConstants.SEGMENT_TYPE.find(1)).isEqualTo(DumpArchiveConstants.SEGMENT_TYPE.TAPE);
        assertThat(DumpArchiveConstants.SEGMENT_TYPE.values()).contains(DumpArchiveConstants.SEGMENT_TYPE.END);
        assertThat(DumpArchiveConstants.SEGMENT_TYPE.valueOf("INODE")).isEqualTo(DumpArchiveConstants.SEGMENT_TYPE.INODE);
        assertThat(DumpArchiveConstants.COMPRESSION_TYPE.find(1)).isEqualTo(DumpArchiveConstants.COMPRESSION_TYPE.BZLIB);
        assertThat(DumpArchiveConstants.COMPRESSION_TYPE.values()).contains(DumpArchiveConstants.COMPRESSION_TYPE.ZLIB);
        assertThat(DumpArchiveConstants.COMPRESSION_TYPE.valueOf("LZO")).isEqualTo(DumpArchiveConstants.COMPRESSION_TYPE.LZO);
    }

    @Test
    void arAndArjEntriesExposeFileIdentity() throws Exception {
        final Path file = Files.createTempFile("compress-ar", ".dat");
        Files.writeString(file, "ar", StandardCharsets.UTF_8);
        final ArArchiveEntry pathEntry = new ArArchiveEntry(file, "path");
        final ArArchiveEntry fileEntry = new ArArchiveEntry(file.toFile(), "file");
        final ArArchiveEntry detailed = new ArArchiveEntry("name", 12, 3, 4, 0100644, 5);
        final ArArchiveEntry simple = new ArArchiveEntry("name", 12);
        assertThat(pathEntry.getName()).isEqualTo("path");
        assertThat(fileEntry.getSize()).isEqualTo(2);
        assertThat(detailed.getLength()).isEqualTo(12);
        assertThat(detailed.getUserId()).isEqualTo(3);
        assertThat(detailed.getGroupId()).isEqualTo(4);
        assertThat(detailed.getMode()).isEqualTo(0100644);
        assertThat(detailed.getLastModified()).isEqualTo(5);
        assertThat(detailed.getLastModifiedDate()).isNotNull();
        assertThat(detailed.isDirectory()).isFalse();
        assertThat(detailed).isEqualTo(simple);
        assertThat(detailed.hashCode()).isEqualTo(simple.hashCode());
        final ArjArchiveEntry arj = new ArjArchiveEntry();
        assertThat(arj.getHostOs()).isGreaterThanOrEqualTo(0);
        assertThat(arj.isHostOsUnix()).isFalse();
        assertThat(arj.getUnixMode()).isEqualTo(0);
        assertThat(arj.getMode()).isEqualTo(0);
        assertThat(arj.getName()).isNull();
        assertThat(arj.getSize()).isZero();
        assertThat(arj.getLastModifiedDate()).isNotNull();
        assertThat(arj.isDirectory()).isFalse();
        assertThat(arj).isEqualTo(new ArjArchiveEntry());
        assertThat(arj.hashCode()).isEqualTo(new ArjArchiveEntry().hashCode());
        assertThat(new ArjArchiveEntry.HostOs()).isNotNull();
        Files.deleteIfExists(file);
    }

    @Test
    void utilityModelsPreserveUsefulValues() throws Exception {
        final MemoryLimitException memory = new MemoryLimitException(123, 456);
        final Exception cause = new Exception("cause");
        final MemoryLimitException withCause = new MemoryLimitException(789, 12, cause);
        assertThat(memory.getMemoryNeededInKb()).isEqualTo(123);
        assertThat(memory.getMemoryLimitInKb()).isEqualTo(456);
        assertThat(withCause.getMemoryNeededInKb()).isEqualTo(789);
        assertThat(withCause.getMemoryLimitInKb()).isEqualTo(12);
        assertThat(new PasswordRequiredException("secret")).hasMessageContaining("secret");
        assertThat(new StreamingNotSupportedException("zip").getFormat()).isEqualTo("zip");
        assertThat(new ArchiveException("bad")).hasMessage("bad");
        assertThat(new ArchiveException("bad", cause).getCause()).isSameAs(cause);
        assertThat(new DumpArchiveException()).isNotNull();
        assertThat(new DumpArchiveException("bad")).hasMessage("bad");
        assertThat(new DumpArchiveException("bad", cause).getCause()).isSameAs(cause);
        assertThat(new DumpArchiveException(cause).getCause()).isSameAs(cause);

        assertThat(BZip2Utils.isCompressedFilename("archive.tar.bz2")).isTrue();
        assertThat(BZip2Utils.getUncompressedFilename("archive.tar.bz2")).isEqualTo("archive.tar");
        assertThat(BZip2Utils.getCompressedFilename("archive.tar")).isEqualTo("archive.tar.bz2");
        final byte[] data = {1, 2, 3};
        final CountingInputStream input = new CountingInputStream(new ByteArrayInputStream(data));
        assertThat(input.read(data)).isEqualTo(3);
        assertThat(input.getBytesRead()).isEqualTo(3);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final CountingOutputStream output = new CountingOutputStream(bytes);
        output.write(data);
        assertThat(output.getBytesWritten()).isEqualTo(3);
        assertThat(bytes.toByteArray()).containsExactly(data);
        final Iterator<String> iterator = Arrays.asList("a", "b").iterator();
        assertThat(Lists.newArrayList(iterator)).containsExactly("a", "b");
        assertThat(TimeUtils.toNtfsTime(new Date(1_000))).isPositive();
        assertThat(ArchiveUtils.toString(new ArArchiveEntry("a", 1))).contains("a");
    }
}
