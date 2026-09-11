/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_compress;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Date;

import org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.PKWareExtraHeader;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.ScatterStatistics;
import org.apache.commons.compress.archivers.zip.UnrecognizedExtraField;
import org.apache.commons.compress.archivers.zip.X000A_NTFS;
import org.apache.commons.compress.archivers.zip.X0015_CertificateIdForFile;
import org.apache.commons.compress.archivers.zip.X0016_CertificateIdForCentralDirectory;
import org.apache.commons.compress.archivers.zip.X0017_StrongEncryptionHeader;
import org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp;
import org.apache.commons.compress.archivers.zip.X7875_NewUnix;
import org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipMethod;
import org.apache.commons.compress.archivers.zip.ZipShort;
import org.apache.commons.compress.utils.MultiReadOnlySeekableByteChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipMetadataCoverageTest {

    @Test
    void extraFieldsExposeConfiguredTimesIdsAndLengths() throws Exception {
        final X7875_NewUnix unix = new X7875_NewUnix();
        unix.setUID(1001);
        unix.setGID(1002);
        assertThat(unix.getCentralDirectoryLength().getValue()).isGreaterThanOrEqualTo(0);
        assertThat(unix.getLocalFileDataLength().getValue()).isGreaterThanOrEqualTo(0);

        final X000A_NTFS ntfs = new X000A_NTFS();
        final FileTime time = FileTime.fromMillis(1_700_000_000_000L);
        ntfs.setAccessFileTime(time);
        ntfs.setCreateFileTime(time);
        ntfs.setModifyFileTime(time);
        assertThat(ntfs.getAccessTime()).isNotNull();
        assertThat(ntfs.getAccessJavaTime()).isEqualTo(new Date(time.toMillis()));

        final X5455_ExtendedTimestamp timestamp = new X5455_ExtendedTimestamp();
        timestamp.setAccessFileTime(time);
        timestamp.setModifyFileTime(time);
        assertThat(timestamp.getAccessTime()).isNotNull();
        assertThat(timestamp.getCentralDirectoryLength().getValue()).isGreaterThan(0);

        final AsiExtraField asi = new AsiExtraField();
        asi.setUserId(11);
        asi.setGroupId(12);
        asi.setLinkedFile("target");
        asi.setDirectory(true);
        assertThat(asi.getCentralDirectoryLength().getValue()).isGreaterThan(0);

        final UnrecognizedExtraField unknown = new UnrecognizedExtraField();
        unknown.setHeaderId(new ZipShort(0xCAFE));
        unknown.setCentralDirectoryData(new byte[] {1, 2, 3});
        unknown.setLocalFileDataData(new byte[] {4, 5});
        assertThat(unknown.getCentralDirectoryData()).containsExactly(1, 2, 3);
        assertThat(unknown.getCentralDirectoryLength().getValue()).isEqualTo(3);
    }

    @Test
    void zip64AndCertificateFieldsParseTheirCentralDirectoryForms() throws Exception {
        final Zip64ExtendedInformationExtraField zip64 = new Zip64ExtendedInformationExtraField();
        zip64.setSize(new org.apache.commons.compress.archivers.zip.ZipEightByteInteger(12));
        zip64.setCompressedSize(new org.apache.commons.compress.archivers.zip.ZipEightByteInteger(8));
        final byte[] central = zip64.getCentralDirectoryData();
        zip64.parseFromCentralDirectoryData(central, 0, central.length);
        assertThat(zip64.getSize().getLongValue()).isEqualTo(12);
        assertThat(zip64.getCompressedSize().getLongValue()).isEqualTo(8);

        final X0015_CertificateIdForFile fileCertificate = new X0015_CertificateIdForFile();
        final X0016_CertificateIdForCentralDirectory directoryCertificate =
                new X0016_CertificateIdForCentralDirectory();
        assertThatThrownBy(() -> fileCertificate.parseFromCentralDirectoryData(new byte[0], 0, 0))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> directoryCertificate.parseFromCentralDirectoryData(new byte[0], 0, 0))
                .isInstanceOf(Exception.class);
        final X0017_StrongEncryptionHeader encryption = new X0017_StrongEncryptionHeader();
        assertThatThrownBy(() -> encryption.parseCentralDirectoryFormat(new byte[0], 0, 0))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> encryption.parseFileFormat(new byte[0], 0, 0))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> encryption.parseFromCentralDirectoryData(new byte[0], 0, 0))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> encryption.parseFromLocalFileData(new byte[0], 0, 0))
                .isInstanceOf(Exception.class);
        assertThat(encryption.getEncryptionAlgorithm()).isNull();
        assertThat(encryption.getHashAlgorithm()).isNull();
    }

    @Test
    void extraFieldParsingModesAndZipEnumsAreStable() throws Exception {
        assertThat(ZipArchiveEntry.ExtraFieldParsingMode.values()).isNotEmpty();
        assertThat(ZipArchiveEntry.ExtraFieldParsingMode.valueOf("BEST_EFFORT")).isNotNull();
        assertThat(ExtraFieldUtils.parse(new byte[0], true)).isEmpty();
        final ScatterStatistics statistics = new ParallelScatterZipCreator().getStatisticsMessage();
        assertThat(statistics.getCompressionElapsed()).isNotNull();
        assertThat(statistics.getMergingElapsed()).isNotNull();
        assertThat(statistics.toString()).contains("compression");
        assertThat(ZipMethod.values()).contains(ZipMethod.STORED);
        assertThat(ZipMethod.valueOf("STORED")).isEqualTo(ZipMethod.STORED);
        assertThat(PKWareExtraHeader.EncryptionAlgorithm.values()).isNotEmpty();
        assertThat(PKWareExtraHeader.EncryptionAlgorithm.valueOf("DES")).isEqualTo(
                PKWareExtraHeader.EncryptionAlgorithm.DES);
        assertThat(PKWareExtraHeader.HashAlgorithm.values()).isNotEmpty();
        assertThat(PKWareExtraHeader.HashAlgorithm.valueOf("SHA1")).isEqualTo(
                PKWareExtraHeader.HashAlgorithm.SHA1);
    }

    @Test
    void multiReadChannelPresentsConcatenatedFilesAsOneReadOnlyChannel() throws Exception {
        final Path first = Files.createTempFile("zip-channel-one", ".bin");
        final Path second = Files.createTempFile("zip-channel-two", ".bin");
        Files.write(first, new byte[] {1, 2});
        Files.write(second, new byte[] {3, 4, 5});
        try (SeekableByteChannel channel = MultiReadOnlySeekableByteChannel.forPaths(first, second)) {
            assertThat(channel.size()).isEqualTo(5);
            final ByteBuffer bytes = ByteBuffer.allocate(5);
            assertThat(channel.read(bytes)).isEqualTo(5);
            assertThat(bytes.array()).containsExactly(1, 2, 3, 4, 5);
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
        }
    }
}
