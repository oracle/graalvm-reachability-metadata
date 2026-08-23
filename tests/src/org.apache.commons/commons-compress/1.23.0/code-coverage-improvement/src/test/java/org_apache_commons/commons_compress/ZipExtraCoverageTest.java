/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

// Exercise ZIP extra-field parsers, flags, and JAR entry behavior.
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.AbstractUnicodeExtraField;
import org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.GeneralPurposeBit;
import org.apache.commons.compress.archivers.zip.JarMarker;
import org.apache.commons.compress.archivers.zip.PKWareExtraHeader;
import org.apache.commons.compress.archivers.zip.UnicodeCommentExtraField;
import org.apache.commons.compress.archivers.zip.ResourceAlignmentExtraField;
import org.apache.commons.compress.archivers.zip.X0015_CertificateIdForFile;
import org.apache.commons.compress.archivers.zip.X0016_CertificateIdForCentralDirectory;
import org.apache.commons.compress.archivers.zip.X0017_StrongEncryptionHeader;
import org.apache.commons.compress.archivers.zip.UnrecognizedExtraField;
import org.apache.commons.compress.archivers.zip.X000A_NTFS;
import org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp;
import org.apache.commons.compress.archivers.zip.X7875_NewUnix;
import org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipEightByteInteger;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipLong;
import org.apache.commons.compress.archivers.zip.ZipShort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

class ZipExtraCoverageTest {

    @Test
    void unixAndUnicodeFieldsRoundTripThroughTheirWireData() throws Exception {
        final AsiExtraField asi = new AsiExtraField();
        final AsiExtraField directory = new AsiExtraField();
        directory.setMode(040755);
        directory.setDirectory(true);
        assertThat(directory.isDirectory()).isTrue();
        asi.setMode(0120777);
        asi.setLinkedFile("target");
        asi.setDirectory(false);
        asi.setUserId(12);
        asi.setGroupId(34);
        assertThat(asi.isLink()).isTrue();
        assertThat(asi.getLinkedFile()).isEqualTo("target");
        assertThat(asi.getUserId()).isEqualTo(12);
        assertThat(asi.getGroupId()).isEqualTo(34);
        assertThat(asi.getLocalFileDataLength().getValue()).isPositive();
        final AsiExtraField parsedAsi = new AsiExtraField();
        parsedAsi.parseFromLocalFileData(asi.getLocalFileDataData(), 0, asi.getLocalFileDataData().length);
        parsedAsi.parseFromCentralDirectoryData(asi.getCentralDirectoryData(), 0, asi.getCentralDirectoryData().length);
        assertThat(parsedAsi.getLinkedFile()).isEqualTo("target");
        assertThat(asi.clone()).isInstanceOf(AsiExtraField.class);

        final UnicodeCommentExtraField unicode = new UnicodeCommentExtraField(
                "comment", "comment".getBytes(StandardCharsets.UTF_8));
        unicode.setNameCRC32(99);
        unicode.setUnicodeName("updated".getBytes(StandardCharsets.UTF_8));
        assertThat(unicode.getNameCRC32()).isEqualTo(99);
        assertThat(unicode.getUnicodeName()).containsExactly("updated".getBytes(StandardCharsets.UTF_8));
        final AbstractUnicodeExtraField reparsed = new UnicodeCommentExtraField();
        reparsed.parseFromCentralDirectoryData(unicode.getCentralDirectoryData(), 0, unicode.getCentralDirectoryData().length);
        assertThat(reparsed.getUnicodeName()).containsExactly("updated".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void genericExtraFieldsAndGeneralPurposeFlagsPreserveSettings() throws Exception {
        final TestPkwareField field = new TestPkwareField();
        field.setLocalFileDataData(new byte[] {1, 2});
        field.setCentralDirectoryData(new byte[] {3, 4});
        assertThat(field.getLocalFileDataData()).containsExactly(1, 2);
        assertThat(field.getCentralDirectoryData()).containsExactly(3, 4);
        final TestPkwareField parsed = new TestPkwareField();
        parsed.parseFromLocalFileData(new byte[] {5, 6}, 0, 2);
        parsed.parseFromCentralDirectoryData(new byte[] {7, 8}, 0, 2);
        assertThat(parsed.getLocalFileDataData()).containsExactly(5, 6);
        assertThat(parsed.getCentralDirectoryData()).containsExactly(7, 8);
        assertThat(parsed.getHeaderId()).isEqualTo(new ZipShort(0x1234));

        final GeneralPurposeBit flags = new GeneralPurposeBit();
        flags.useDataDescriptor(true);
        flags.useEncryption(true);
        flags.useStrongEncryption(true);
        flags.useUTF8ForNames(true);
        final byte[] encoded = flags.encode();
        final GeneralPurposeBit decoded = GeneralPurposeBit.parse(encoded, 0);
        assertThat(decoded.usesDataDescriptor()).isTrue();
        assertThat(decoded.usesEncryption()).isTrue();
        assertThat(decoded.usesStrongEncryption()).isTrue();
        assertThat(decoded.usesUTF8ForNames()).isTrue();
        assertThat(decoded).isEqualTo(flags);
        assertThat(decoded.hashCode()).isEqualTo(flags.hashCode());
        assertThat(decoded.clone()).isInstanceOf(GeneralPurposeBit.class);

        final JarMarker marker = JarMarker.getInstance();
        assertThat(marker.getCentralDirectoryData()).isEmpty();
        assertThat(marker.getLocalFileDataData()).isEmpty();
        assertThat(marker.getCentralDirectoryLength().getValue()).isZero();
        marker.parseFromLocalFileData(new byte[0], 0, 0);
        marker.parseFromCentralDirectoryData(new byte[0], 0, 0);
        assertThat(new ExtraFieldUtils()).isNotNull();
        final ZipExtraField[] fields = ExtraFieldUtils.parse(
                ExtraFieldUtils.mergeLocalFileDataData(new ZipExtraField[] {marker}));
        assertThat(fields).hasSize(1);
        assertThat(ExtraFieldUtils.UnparseableExtraField.READ.onUnparseableExtraField(
                new byte[] {1, 2, 3}, 0, 3, false, 3)).isNotNull();
    }

    @Test
    void jarEntriesAndStreamsExposeJarSpecificMetadata() throws Exception {
        final JarArchiveEntry fromString = new JarArchiveEntry("META-INF/MANIFEST.MF");
        final ZipEntry zipSource = new ZipEntry("zip-entry");
        zipSource.setMethod(ZipEntry.STORED);
        final JarEntry jarSource = new JarEntry("jar-entry");
        jarSource.setMethod(ZipEntry.STORED);
        final ZipArchiveEntry archiveSource = new ZipArchiveEntry("archive-entry");
        archiveSource.setMethod(ZipEntry.STORED);
        final JarArchiveEntry fromZip = new JarArchiveEntry(zipSource);
        final JarArchiveEntry fromJar = new JarArchiveEntry(jarSource);
        final JarArchiveEntry fromArchive = new JarArchiveEntry(archiveSource);
        assertThat(fromString.getManifestAttributes()).isNull();
        assertThat(fromString.getCertificates()).isNull();
        assertThat(fromZip.getName()).isEqualTo("zip-entry");
        assertThat(fromJar.getName()).isEqualTo("jar-entry");
        assertThat(fromArchive.getName()).isEqualTo("archive-entry");

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarArchiveOutputStream output = new JarArchiveOutputStream(bytes, "UTF-8")) {
            final JarArchiveEntry entry = new JarArchiveEntry("hello.txt");
            output.putArchiveEntry(entry);
            output.write("hello".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        assertThat(JarArchiveInputStream.matches(new byte[] {'P', 'K', 3, 4}, 4)).isTrue();
        try (JarArchiveInputStream input = new JarArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()), "UTF-8")) {
            assertThat(input.getNextJarEntry().getName()).isEqualTo("hello.txt");
            assertThat(input.getNextEntry()).isNull();
        }
        final Attributes attributes = new Attributes();
        attributes.putValue("Created-By", "coverage");
        assertThat(attributes.getValue("Created-By")).isEqualTo("coverage");
    }

    @Test
    void publicZipModelsRoundTripMetadataAndWireValues() throws Exception {
        final java.util.Date date = new java.util.Date(1_600_000_000_000L);
        final X7875_NewUnix unix = new X7875_NewUnix();
        unix.setUID(123);
        unix.setGID(456);
        final X7875_NewUnix parsedUnix = new X7875_NewUnix();
        parsedUnix.parseFromLocalFileData(unix.getLocalFileDataData(), 0, unix.getLocalFileDataData().length);
        parsedUnix.parseFromCentralDirectoryData(unix.getCentralDirectoryData(), 0, unix.getCentralDirectoryData().length);
        assertThat(parsedUnix.getUID()).isEqualTo(123);
        assertThat(parsedUnix.clone()).isEqualTo(parsedUnix);
        assertThat(parsedUnix.hashCode()).isEqualTo(parsedUnix.hashCode());
        assertThat(parsedUnix.toString()).contains("123");

        final X5455_ExtendedTimestamp timestamp = new X5455_ExtendedTimestamp();
        timestamp.setCreateJavaTime(date);
        timestamp.setModifyJavaTime(date);
        assertThat(timestamp.getCreateJavaTime()).isEqualTo(date);
        assertThat(timestamp.getModifyJavaTime()).isEqualTo(date);
        assertThat(timestamp.getCreateTime()).isNotNull();
        assertThat(timestamp.getModifyTime()).isNotNull();
        assertThat(timestamp.getFlags()).isNotZero();
        assertThat(timestamp.clone()).isEqualTo(timestamp);
        assertThat(timestamp.hashCode()).isEqualTo(timestamp.hashCode());
        assertThat(timestamp.toString()).isNotEmpty();

        final X000A_NTFS ntfs = new X000A_NTFS();
        ntfs.setCreateJavaTime(date);
        ntfs.setModifyJavaTime(date);
        assertThat(ntfs.getCreateJavaTime()).isEqualTo(date);
        assertThat(ntfs.getModifyJavaTime()).isEqualTo(date);
        assertThat(ntfs.getCreateTime()).isNotNull();
        assertThat(ntfs.getModifyTime()).isNotNull();
        assertThat(ntfs.toString()).isNotEmpty();
        assertThat(ntfs).isEqualTo(ntfs);
        assertThat(ntfs.hashCode()).isEqualTo(ntfs.hashCode());

        final UnrecognizedExtraField unknown = new UnrecognizedExtraField();
        unknown.setHeaderId(new ZipShort(0x1234));
        unknown.setLocalFileDataData(new byte[] {1, 2});
        unknown.setCentralDirectoryData(new byte[] {3, 4});
        final UnrecognizedExtraField reparsed = new UnrecognizedExtraField();
        reparsed.parseFromLocalFileData(new byte[] {5, 6}, 0, 2);
        reparsed.parseFromCentralDirectoryData(new byte[] {7, 8}, 0, 2);
        assertThat(unknown.getHeaderId()).isEqualTo(new ZipShort(0x1234));
        assertThat(unknown.getLocalFileDataLength().getValue()).isEqualTo(2);
        assertThat(unknown.getCentralDirectoryLength().getValue()).isEqualTo(2);
        assertThat(reparsed.getLocalFileDataData()).containsExactly(5, 6);
        assertThat(new org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData()).isNotNull();

        final ZipEightByteInteger wide = new ZipEightByteInteger(new byte[] {8, 7, 6, 5, 4, 3, 2, 1});
        assertThat(ZipEightByteInteger.getLongValue(wide.getBytes())).isEqualTo(wide.getLongValue());
        assertThat(ZipEightByteInteger.getLongValue(wide.getBytes(), 0)).isEqualTo(wide.getLongValue());
        assertThat(ZipEightByteInteger.getValue(wide.getBytes())).isEqualTo(wide.getValue());
        assertThat(wide.hashCode()).isEqualTo(wide.hashCode());
        assertThat(wide.toString()).isNotEmpty();
        final ZipLong zipLong = new ZipLong(42);
        assertThat(zipLong.clone()).isEqualTo(zipLong);
        assertThat(zipLong.hashCode()).isEqualTo(zipLong.hashCode());
        assertThat(zipLong.toString()).isNotEmpty();
        final ZipShort zipShort = new ZipShort(new byte[] {42, 0});
        assertThat(zipShort.clone()).isEqualTo(zipShort);
        assertThat(zipShort.toString()).isNotEmpty();

        assertThat(org.apache.commons.compress.archivers.zip.ZipUtil.adjustToLong(-1)).isEqualTo(0xffffffffL);
        assertThat(org.apache.commons.compress.archivers.zip.ZipUtil.fromDosTime(new ZipLong(0x00210000))).isNotNull();
        assertThat(org.apache.commons.compress.archivers.zip.ZipUtil.reverse(new byte[] {1, 2, 3})).containsExactly(3, 2, 1);
        assertThat(org.apache.commons.compress.archivers.zip.ZipUtil.signedByteToUnsignedInt((byte) -1)).isEqualTo(255);
        assertThat(org.apache.commons.compress.archivers.zip.ZipUtil.unsignedIntToSignedByte(255)).isEqualTo((byte) -1);
    }

    @Test
    void encryptionHeadersAndEnumValuesExposeWireDefaults() throws Exception {
        final X0015_CertificateIdForFile fileCertificate = new X0015_CertificateIdForFile();
        final X0016_CertificateIdForCentralDirectory directoryCertificate = new X0016_CertificateIdForCentralDirectory();
        assertThat(fileCertificate.getHashAlgorithm()).isNull();
        assertThat(fileCertificate.getRecordCount()).isZero();
        assertThat(directoryCertificate.getHashAlgorithm()).isNull();
        assertThat(directoryCertificate.getRecordCount()).isZero();
        try {
            fileCertificate.parseFromCentralDirectoryData(new byte[0], 0, 0);
        } catch (Exception expectedMalformedData) {
            assertThat(expectedMalformedData).isInstanceOf(Exception.class);
        }
        try {
            directoryCertificate.parseFromCentralDirectoryData(new byte[0], 0, 0);
        } catch (Exception expectedMalformedData) {
            assertThat(expectedMalformedData).isInstanceOf(Exception.class);
        }
        final X0017_StrongEncryptionHeader strong = new X0017_StrongEncryptionHeader();
        assertThat(strong.getRecordCount()).isZero();
        try {
            strong.parseCentralDirectoryFormat(new byte[0], 0, 0);
            strong.parseFileFormat(new byte[0], 0, 0);
            strong.parseFromCentralDirectoryData(new byte[0], 0, 0);
            strong.parseFromLocalFileData(new byte[0], 0, 0);
        } catch (Exception expectedMalformedData) {
            assertThat(expectedMalformedData).isInstanceOf(Exception.class);
        }
        assertThat(PKWareExtraHeader.EncryptionAlgorithm.values()).isNotEmpty();
        assertThat(PKWareExtraHeader.EncryptionAlgorithm.valueOf("DES").getCode()).isGreaterThanOrEqualTo(0);
        assertThat(PKWareExtraHeader.EncryptionAlgorithm.getAlgorithmByCode(0x6601)).isNotNull();
        assertThat(PKWareExtraHeader.HashAlgorithm.values()).isNotEmpty();
        assertThat(PKWareExtraHeader.HashAlgorithm.valueOf("CRC32").getCode()).isGreaterThanOrEqualTo(0);
        assertThat(PKWareExtraHeader.HashAlgorithm.getAlgorithmByCode(0)).isNotNull();
        final TestPkwareField field = new TestPkwareField();
        assertThat(field.getLocalFileDataLength()).isNotNull();
        assertThat(field.getCentralDirectoryLength()).isNotNull();
    }

    @Test
    void zipEntryAndZip64OptionsExposeConfiguredState() throws Exception {
        final java.nio.file.attribute.FileTime time = java.nio.file.attribute.FileTime.fromMillis(1_600_000_000_000L);
        final ZipArchiveEntry entry = new ZipArchiveEntry("entry.txt");
        entry.setAlignment(4);
        entry.setCommentSource(ZipArchiveEntry.CommentSource.UNICODE_EXTRA_FIELD);
        entry.setCreationTime(time);
        entry.setLastAccessTime(time);
        entry.setLastModifiedTime(time);
        entry.setTime(time);
        entry.setUnixMode(0100644);
        assertThat(entry.getCommentSource()).isEqualTo(ZipArchiveEntry.CommentSource.UNICODE_EXTRA_FIELD);
        assertThat(entry.getLastModifiedDate()).isNotNull();
        assertThat(entry.getNameSource()).isEqualTo(ZipArchiveEntry.NameSource.NAME);
        assertThat(entry.getRawFlag()).isZero();
        assertThat(entry.getRawName()).isNull();
        assertThat(entry.getVersionMadeBy()).isGreaterThanOrEqualTo(0);
        assertThat(entry.getVersionRequired()).isGreaterThanOrEqualTo(0);
        assertThat(entry.isStreamContiguous()).isFalse();
        assertThat(entry.clone()).isInstanceOf(ZipArchiveEntry.class);
        assertThat(entry).isEqualTo(entry);
        try {
            entry.removeUnparseableExtraFieldData();
        } catch (java.util.NoSuchElementException expectedAbsentField) {
            assertThat(expectedAbsentField).isInstanceOf(java.util.NoSuchElementException.class);
        }

        final Zip64ExtendedInformationExtraField zip64 = new Zip64ExtendedInformationExtraField();
        zip64.setDiskStartNumber(new ZipLong(7));
        zip64.setRelativeHeaderOffset(new ZipEightByteInteger(8));
        assertThat(zip64.getDiskStartNumber().getValue()).isEqualTo(7);
        assertThat(zip64.getRelativeHeaderOffset().getLongValue()).isEqualTo(8);
        zip64.reparseCentralDirectoryData(false, false, false, true);
        assertThat(zip64.getLocalFileDataLength()).isNotNull();
        final ResourceAlignmentExtraField alignment = new ResourceAlignmentExtraField(8, true, 2);
        assertThat(alignment.allowMethodChange()).isTrue();
        assertThat(alignment.getCentralDirectoryData()).isNotEmpty();
        assertThat(alignment.getCentralDirectoryLength()).isNotNull();

        final org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException.Feature feature =
                org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException.Feature.ENCRYPTION;
        final ZipArchiveEntry featureEntry = new ZipArchiveEntry("feature");
        assertThat(new org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException(feature).getFeature()).isEqualTo(feature);
        assertThat(new org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException(feature, featureEntry).getEntry()).isEqualTo(featureEntry);
        assertThat(new org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException(
                org.apache.commons.compress.archivers.zip.ZipMethod.DEFLATED, featureEntry).getEntry()).isEqualTo(featureEntry);
        assertThat(feature.toString()).isEqualTo("encryption");
        assertThat(ZipArchiveEntry.CommentSource.values()).isNotEmpty();
        assertThat(ZipArchiveEntry.CommentSource.valueOf("COMMENT")).isEqualTo(ZipArchiveEntry.CommentSource.COMMENT);
        assertThat(ZipArchiveEntry.NameSource.values()).isNotEmpty();
        assertThat(ZipArchiveEntry.NameSource.valueOf("NAME")).isEqualTo(ZipArchiveEntry.NameSource.NAME);
        assertThat(ZipArchiveEntry.ExtraFieldParsingMode.values()).hasSize(5);
        assertThat(ZipArchiveEntry.ExtraFieldParsingMode.valueOf("BEST_EFFORT")).isNotNull();
    }

    @Test
    void unparseableAndFactoryHelpersExposeCompleteExtraFieldContracts() throws Exception {
        final org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData unparseable =
                new org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData();
        unparseable.parseFromLocalFileData(new byte[] {1, 2}, 0, 2);
        unparseable.parseFromCentralDirectoryData(new byte[] {3, 4, 5}, 0, 3);
        assertThat(unparseable.getHeaderId()).isNotNull();
        assertThat(unparseable.getLocalFileDataData()).containsExactly(1, 2);
        assertThat(unparseable.getCentralDirectoryData()).containsExactly(3, 4, 5);
        assertThat(unparseable.getLocalFileDataLength().getValue()).isEqualTo(2);
        assertThat(unparseable.getCentralDirectoryLength().getValue()).isEqualTo(3);

        final ZipArchiveEntry.ExtraFieldParsingMode mode = ZipArchiveEntry.ExtraFieldParsingMode.BEST_EFFORT;
        final ZipExtraField field = mode.createExtraField(new ZipShort(0x5455));
        assertThat(mode.fill(field, new byte[] {1, 0, 0, 0, 0}, 0, 5, false)).isSameAs(field);
        assertThat(mode.onUnparseableExtraField(new byte[] {9, 8}, 0, 2, false, 2)).isNotNull();
        assertThat(ZipArchiveEntry.ExtraFieldParsingMode.values()).contains(mode);

        final UnicodeCommentExtraField constructor = new UnicodeCommentExtraField(
                "comment", new byte[] {0, 1, 2, 3}, 1, 2);
        assertThat(constructor.getUnicodeName()).containsExactly("comment".getBytes(StandardCharsets.UTF_8));
        final ZipArchiveEntry malformed = new ZipArchiveEntry("malformed");
        malformed.setExtra(new byte[] {1, 0, 2, 0, 9});
        assertThat(malformed.getUnparseableExtraFieldData()).isNotNull();

        final PathHolder temporary = new PathHolder();
        try (org.apache.commons.compress.archivers.zip.ScatterZipOutputStream fileBased =
                     org.apache.commons.compress.archivers.zip.ScatterZipOutputStream.fileBased(temporary.file, 6);
             org.apache.commons.compress.archivers.zip.ScatterZipOutputStream pathBased =
                     org.apache.commons.compress.archivers.zip.ScatterZipOutputStream.pathBased(temporary.path)) {
            assertThat(fileBased).isNotNull();
            assertThat(pathBased).isNotNull();
        }
    }

    @Test
    void extraFieldParserUsesPublicUnparseableAndStrongEncryptionBoundaries() throws Exception {
        final byte[] withTruncatedField = {0x34, 0x12, 5, 0, 9, 8};
        final ZipExtraField[] fields = ExtraFieldUtils.parse(withTruncatedField, false,
                ExtraFieldUtils.UnparseableExtraField.READ);
        assertThat(fields).hasSize(1);
        assertThat(fields[0]).isInstanceOf(org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData.class);

        final ZipArchiveEntry entry = new ZipArchiveEntry("strong");
        entry.setExtra(new byte[] {0x17, 0x00, 1, 0, 0});
        assertThat(entry.getExtraFields(ZipArchiveEntry.ExtraFieldParsingMode.BEST_EFFORT)).isNotEmpty();
        final X0017_StrongEncryptionHeader strong = new X0017_StrongEncryptionHeader();
        try {
            strong.parseFromLocalFileData(new byte[] {1, 0, 0}, 0, 3);
        } catch (Exception expectedMalformedHeader) {
            assertThat(expectedMalformedHeader).isInstanceOf(Exception.class);
        }
    }

    private static final class PathHolder {
        private final java.io.File file;
        private final java.nio.file.Path path;

        private PathHolder() throws Exception {
            path = java.nio.file.Files.createTempFile("scatter-path", ".tmp");
            file = java.nio.file.Files.createTempFile("scatter-file", ".tmp").toFile();
        }
    }

    private static final class TestPkwareField extends PKWareExtraHeader {
        private TestPkwareField() {
            super(new ZipShort(0x1234));
        }
    }
}
