/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_f4b6a3.uuid_creator;

import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.alt.GUID;
import com.github.f4b6a3.uuid.codec.StandardBinaryCodec;
import com.github.f4b6a3.uuid.codec.StandardStringCodec;
import com.github.f4b6a3.uuid.codec.UriCodec;
import com.github.f4b6a3.uuid.codec.UrnCodec;
import com.github.f4b6a3.uuid.codec.UuidCodec;
import com.github.f4b6a3.uuid.codec.base.Base16Codec;
import com.github.f4b6a3.uuid.codec.base.Base32Codec;
import com.github.f4b6a3.uuid.codec.base.Base58BtcCodec;
import com.github.f4b6a3.uuid.codec.base.Base62Codec;
import com.github.f4b6a3.uuid.codec.base.Base64Codec;
import com.github.f4b6a3.uuid.codec.base.Base64UrlCodec;
import com.github.f4b6a3.uuid.codec.base.BaseNCodec;
import com.github.f4b6a3.uuid.codec.other.DotNetGuid1Codec;
import com.github.f4b6a3.uuid.codec.other.DotNetGuid4Codec;
import com.github.f4b6a3.uuid.codec.other.NcnameCodec;
import com.github.f4b6a3.uuid.codec.other.SlugCodec;
import com.github.f4b6a3.uuid.codec.other.TimeOrderedCodec;
import com.github.f4b6a3.uuid.enums.UuidLocalDomain;
import com.github.f4b6a3.uuid.enums.UuidNamespace;
import com.github.f4b6a3.uuid.enums.UuidVariant;
import com.github.f4b6a3.uuid.exception.InvalidUuidException;
import com.github.f4b6a3.uuid.factory.nonstandard.PrefixCombFactory;
import com.github.f4b6a3.uuid.factory.nonstandard.SuffixCombFactory;
import com.github.f4b6a3.uuid.factory.standard.RandomBasedFactory;
import com.github.f4b6a3.uuid.factory.standard.TimeOrderedEpochFactory;
import com.github.f4b6a3.uuid.util.CombUtil;
import com.github.f4b6a3.uuid.util.MachineId;
import com.github.f4b6a3.uuid.util.UuidBuilder;
import com.github.f4b6a3.uuid.util.UuidComparator;
import com.github.f4b6a3.uuid.util.UuidTime;
import com.github.f4b6a3.uuid.util.UuidUtil;
import com.github.f4b6a3.uuid.util.UuidValidator;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UuidCreatorTest {
    private static final Instant SAMPLE_INSTANT = Instant.parse("2022-02-22T22:22:22.222Z");
    private static final UUID SAMPLE_UUID = UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210");

    @Test
    void standardRepresentationsRoundTripAndValidationRejectsMalformedInput() {
        byte[] bytes = UuidCreator.toBytes(SAMPLE_UUID);
        String canonical = UuidCreator.toString(SAMPLE_UUID);

        assertThat(bytes).hasSize(16);
        assertThat(UuidCreator.fromBytes(bytes)).isEqualTo(SAMPLE_UUID);
        assertThat(canonical).isEqualTo("d9428888-122b-11e1-b85c-61cd3cbb3210");
        assertThat(UuidCreator.fromString(canonical.replace("-", ""))).isEqualTo(SAMPLE_UUID);
        assertThat(UuidCreator.fromString("{" + canonical.toUpperCase() + "}")).isEqualTo(SAMPLE_UUID);
        assertThat(UuidCreator.fromString("urn:uuid:" + canonical)).isEqualTo(SAMPLE_UUID);
        assertThat(UuidCreator.getNil()).isEqualTo(new UUID(0L, 0L));
        assertThat(UuidCreator.getMax()).isEqualTo(new UUID(-1L, -1L));

        assertThat(UuidValidator.isValid(SAMPLE_UUID)).isTrue();
        assertThat(UuidValidator.isValid(SAMPLE_UUID, 1)).isTrue();
        assertThat(UuidValidator.isValid(bytes, 1)).isTrue();
        assertThat(UuidValidator.isValid(canonical, 1)).isTrue();
        assertThat(UuidValidator.isValid(canonical.toCharArray(), 1)).isTrue();
        assertThat(UuidValidator.isValid("not-a-uuid")).isFalse();
        assertThatThrownBy(() -> UuidValidator.validate("not-a-uuid"))
                .isInstanceOf(InvalidUuidException.class);
    }

    @Test
    void facadeGeneratesEveryStandardVersionAndExposesItsComponents() {
        int clockSequence = 0x1234;
        long nodeIdentifier = 0xabcdef123456L;
        int localIdentifier = 4242;
        UUID timeBased = UuidCreator.getTimeBased(SAMPLE_INSTANT, clockSequence, nodeIdentifier);
        UUID dceSecurity = UuidCreator.getDceSecurity(UuidLocalDomain.LOCAL_DOMAIN_GROUP, localIdentifier);
        UUID nameBasedMd5 = UuidCreator.getNameBasedMd5(UuidNamespace.NAMESPACE_DNS, "www.example.com");
        UUID randomBased = UuidCreator.getRandomBased();
        UUID nameBasedSha1 = UuidCreator.getNameBasedSha1(UuidNamespace.NAMESPACE_DNS, "www.example.com");
        UUID timeOrdered = UuidCreator.getTimeOrdered(SAMPLE_INSTANT, clockSequence, nodeIdentifier);
        UUID timeOrderedEpoch = UuidCreator.getTimeOrderedEpoch(SAMPLE_INSTANT);
        Map<Integer, UUID> generatedByVersion = Map.of(
                1, timeBased,
                2, dceSecurity,
                3, nameBasedMd5,
                4, randomBased,
                5, nameBasedSha1,
                6, timeOrdered,
                7, timeOrderedEpoch);

        generatedByVersion.forEach((version, uuid) -> {
            assertThat(uuid.version()).isEqualTo(version);
            assertThat(uuid.variant()).isEqualTo(2);
            assertThat(UuidValidator.isValid(uuid, version)).isTrue();
            assertThat(UuidUtil.getVersion(uuid).getValue()).isEqualTo(version);
            assertThat(UuidUtil.getVariant(uuid)).isEqualTo(UuidVariant.VARIANT_STANDARD);
        });
        assertThat(UuidUtil.getInstant(timeBased)).isEqualTo(SAMPLE_INSTANT);
        assertThat(UuidUtil.getClockSequence(timeBased)).isEqualTo(clockSequence);
        assertThat(UuidUtil.getNodeIdentifier(timeBased)).isEqualTo(nodeIdentifier);
        assertThat(UuidUtil.getInstant(timeOrdered)).isEqualTo(SAMPLE_INSTANT);
        assertThat(UuidUtil.getClockSequence(timeOrdered)).isEqualTo(clockSequence);
        assertThat(UuidUtil.getNodeIdentifier(timeOrdered)).isEqualTo(nodeIdentifier);
        assertThat(UuidUtil.getInstant(timeOrderedEpoch)).isEqualTo(SAMPLE_INSTANT);
        assertThat(UuidUtil.getLocalDomain(dceSecurity)).isEqualTo(UuidLocalDomain.LOCAL_DOMAIN_GROUP.getValue());
        assertThat(UuidUtil.getLocalIdentifier(dceSecurity)).isEqualTo(localIdentifier);
        assertThat(UuidUtil.isNil(UuidCreator.getNil())).isTrue();
        assertThat(UuidUtil.isMax(UuidCreator.getMax())).isTrue();
    }

    @Test
    void convenienceGeneratorsRetainTheirVersionAndVariantContracts() {
        List<UUID> version1 = List.of(
                UuidCreator.getTimeBased(),
                UuidCreator.getTimeBasedWithMac(),
                UuidCreator.getTimeBasedWithRandom());
        List<UUID> version2 = List.of(
                UuidCreator.getDceSecurity((byte) 7, 123),
                UuidCreator.getDceSecurityWithMac((byte) 7, 123),
                UuidCreator.getDceSecurityWithRandom((byte) 7, 123));
        List<UUID> version4 = List.of(
                UuidCreator.getRandomBasedFast(),
                UuidCreator.getPrefixComb(),
                UuidCreator.getSuffixComb(),
                UuidCreator.getShortPrefixComb(),
                UuidCreator.getShortSuffixComb());
        List<UUID> version6 = List.of(
                UuidCreator.getTimeOrdered(),
                UuidCreator.getTimeOrderedWithMac(),
                UuidCreator.getTimeOrderedWithRandom());
        List<UUID> version7 = List.of(
                UuidCreator.getTimeOrderedEpoch(),
                UuidCreator.getTimeOrderedEpochFast(),
                UuidCreator.getTimeOrderedEpochPlus1(),
                UuidCreator.getTimeOrderedEpochPlusN());

        assertVersion(version1, 1);
        assertVersion(version2, 2);
        assertVersion(version4, 4);
        assertVersion(version6, 6);
        assertVersion(version7, 7);
    }

    @Test
    void nameBasedFactoriesMatchRfcVectorsForStringAndByteInputs() {
        byte[] dnsName = "www.example.com".getBytes(StandardCharsets.UTF_8);
        UUID expectedMd5 = UUID.fromString("5df41881-3aed-3515-88a7-2f4a814cf09e");
        UUID expectedSha1 = UUID.fromString("2ed6657d-e927-568b-95e1-2665a8aea6a2");

        assertThat(UuidCreator.getNameBasedMd5(UuidNamespace.NAMESPACE_DNS, "www.example.com"))
                .isEqualTo(expectedMd5);
        assertThat(UuidCreator.getNameBasedMd5(UuidCreator.NAMESPACE_DNS.getValue(), dnsName))
                .isEqualTo(expectedMd5);
        assertThat(UuidCreator.getNameBasedMd5(UuidCreator.NAMESPACE_DNS.getValue().toString(), dnsName))
                .isEqualTo(expectedMd5);
        assertThat(UuidCreator.getNameBasedSha1(UuidNamespace.NAMESPACE_DNS, "www.example.com"))
                .isEqualTo(expectedSha1);
        assertThat(UuidCreator.getNameBasedSha1(UuidCreator.NAMESPACE_DNS.getValue(), dnsName))
                .isEqualTo(expectedSha1);
        assertThat(UuidCreator.getNameBasedSha1(UuidCreator.NAMESPACE_DNS.getValue().toString(), dnsName))
                .isEqualTo(expectedSha1);
    }

    @Test
    void timestampBoundsAndCombFactoriesPreserveRequestedInstants() {
        UUID timeMin = UuidCreator.getTimeBasedMin(SAMPLE_INSTANT);
        UUID timeMax = UuidCreator.getTimeBasedMax(SAMPLE_INSTANT);
        UUID orderedMin = UuidCreator.getTimeOrderedMin(SAMPLE_INSTANT);
        UUID orderedMax = UuidCreator.getTimeOrderedMax(SAMPLE_INSTANT);
        UUID epochMin = UuidCreator.getTimeOrderedEpochMin(SAMPLE_INSTANT);
        UUID epochMax = UuidCreator.getTimeOrderedEpochMax(SAMPLE_INSTANT);
        UUID prefixMin = UuidCreator.getPrefixCombMin(SAMPLE_INSTANT);
        UUID prefixMax = UuidCreator.getPrefixCombMax(SAMPLE_INSTANT);
        UUID suffixMin = UuidCreator.getSuffixCombMin(SAMPLE_INSTANT);
        UUID suffixMax = UuidCreator.getSuffixCombMax(SAMPLE_INSTANT);

        assertThat(timeMin).hasToString("e7a1c2e0-942d-11ec-8000-000000000000");
        assertThat(timeMax).hasToString("e7a1c2e0-942d-11ec-bfff-ffffffffffff");
        assertThat(orderedMin).hasToString("1ec942de-7a1c-62e0-8000-000000000000");
        assertThat(orderedMax).hasToString("1ec942de-7a1c-62e0-bfff-ffffffffffff");
        assertThat(epochMin).hasToString("017f2387-460e-7000-8000-000000000000");
        assertThat(epochMax).hasToString("017f2387-460e-7fff-bfff-ffffffffffff");
        assertThat(prefixMin).hasToString("017f2387-460e-4000-8000-000000000000");
        assertThat(prefixMax).hasToString("017f2387-460e-4fff-bfff-ffffffffffff");
        assertThat(suffixMin).hasToString("00000000-0000-4000-8000-017f2387460e");
        assertThat(suffixMax).hasToString("ffffffff-ffff-4fff-bfff-017f2387460e");
        assertThat(UuidComparator.defaultCompare(timeMin, timeMax)).isNegative();
        assertThat(UuidComparator.getOpaqueInstance().compare(epochMin, epochMax)).isNegative();

        Clock clock = Clock.fixed(SAMPLE_INSTANT, ZoneOffset.UTC);
        UUID prefix = new PrefixCombFactory(new Random(7L), clock).create();
        UUID suffix = new SuffixCombFactory(new Random(7L), clock).create();
        assertThat(CombUtil.getPrefixInstant(prefix)).isEqualTo(SAMPLE_INSTANT);
        assertThat(CombUtil.getSuffixInstant(suffix)).isEqualTo(SAMPLE_INSTANT);
    }

    @Test
    void codecsRoundTripCanonicalCompactAndPlatformSpecificFormats() {
        assertCodecRoundTrip(StandardBinaryCodec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(StandardStringCodec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(Base16Codec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(Base32Codec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(Base58BtcCodec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(Base62Codec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(Base64Codec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(Base64UrlCodec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(BaseNCodec.newInstance("a-z"), SAMPLE_UUID);
        assertCodecRoundTrip(new SlugCodec(), SAMPLE_UUID);
        assertCodecRoundTrip(new SlugCodec(Base32Codec.INSTANCE), SAMPLE_UUID);
        assertCodecRoundTrip(new NcnameCodec(), SAMPLE_UUID);
        assertCodecRoundTrip(new NcnameCodec(Base32Codec.INSTANCE), SAMPLE_UUID);
        assertCodecRoundTrip(DotNetGuid1Codec.INSTANCE, SAMPLE_UUID);
        UUID randomBased = UUID.fromString("d9428888-122b-41e1-b85c-61cd3cbb3210");
        assertCodecRoundTrip(DotNetGuid4Codec.INSTANCE, randomBased);
        assertCodecRoundTrip(TimeOrderedCodec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(UrnCodec.INSTANCE, SAMPLE_UUID);
        assertCodecRoundTrip(UriCodec.INSTANCE, SAMPLE_UUID);

        String urn = UrnCodec.INSTANCE.encode(SAMPLE_UUID);
        URI uri = UriCodec.INSTANCE.encode(SAMPLE_UUID);
        assertThat(urn).isEqualTo("urn:uuid:" + SAMPLE_UUID);
        assertThat(UrnCodec.isUuidUrn(urn)).isTrue();
        assertThat(uri).isEqualTo(URI.create(urn));
        assertThat(UriCodec.isUuidUri(uri)).isTrue();
        assertThat(SlugCodec.INSTANCE.encode(SAMPLE_UUID)).hasSizeLessThan(SAMPLE_UUID.toString().length());
        assertThat(NcnameCodec.INSTANCE.encode(SAMPLE_UUID)).startsWith("B");
    }

    @Test
    void buildersAndDeterministicFactoriesProduceOrderedValidIdentifiers() {
        UUID built = new UuidBuilder(4)
                .put(0x0123456789abcdefL)
                .put(0xfedcba9876543210L)
                .build();
        RandomBasedFactory randomFactory = new RandomBasedFactory(new Random(29L));
        UUID random1 = randomFactory.create();
        UUID random2 = randomFactory.create();
        Clock fixedClock = Clock.fixed(SAMPLE_INSTANT, ZoneOffset.UTC);
        TimeOrderedEpochFactory orderedFactory = TimeOrderedEpochFactory.builder()
                .withClock(fixedClock)
                .withRandom(new Random(31L))
                .withIncrementPlus1()
                .build();
        UUID ordered1 = orderedFactory.create();
        UUID ordered2 = orderedFactory.create();

        assertThat(built.version()).isEqualTo(4);
        assertThat(built.variant()).isEqualTo(2);
        assertThat(random1).isNotEqualTo(random2);
        assertThat(random1.version()).isEqualTo(4);
        assertThat(random2.version()).isEqualTo(4);
        assertThat(ordered1.version()).isEqualTo(7);
        assertThat(ordered2.version()).isEqualTo(7);
        assertThat(UuidComparator.opaqueCompare(ordered1, ordered2)).isNegative();
        assertThat(UuidUtil.getInstant(ordered1)).isEqualTo(SAMPLE_INSTANT);
        assertThat(UuidUtil.getInstant(ordered2)).isEqualTo(SAMPLE_INSTANT);
        assertThat(UuidTime.fromUnixTimestamp(UuidTime.toUnixTimestamp(SAMPLE_INSTANT)))
                .isEqualTo(SAMPLE_INSTANT);
        assertThat(UuidTime.fromGregTimestamp(UuidTime.toGregTimestamp(SAMPLE_INSTANT)))
                .isEqualTo(SAMPLE_INSTANT);
    }

    @Test
    void alternativeGuidApiGeneratesConvertsAndComparesAllVersions() {
        Random random = new Random(37L);
        GUID version1 = GUID.v1(SAMPLE_INSTANT, random);
        GUID version2 = GUID.v2(GUID.LOCAL_DOMAIN_PERSON, 501, SAMPLE_INSTANT, random);
        GUID version3 = GUID.v3(GUID.NAMESPACE_DNS, "www.example.com");
        GUID version4 = GUID.v4(random);
        GUID version5 = GUID.v5(GUID.NAMESPACE_DNS, "www.example.com");
        GUID version6 = GUID.v6(SAMPLE_INSTANT, random);
        GUID version7 = GUID.v7(SAMPLE_INSTANT, random);
        List<GUID> generated = List.of(version1, version2, version3, version4, version5, version6, version7);

        for (int index = 0; index < generated.size(); index++) {
            GUID guid = generated.get(index);
            assertThat(guid.version()).isEqualTo(index + 1);
            assertThat(new GUID(guid.toBytes())).isEqualTo(guid);
            assertThat(new GUID(guid.toString())).isEqualTo(guid);
            assertThat(new GUID(guid.toUUID())).isEqualTo(guid);
            assertThat(GUID.valid(guid.toString())).isTrue();
        }
        assertThat(version3.toString()).isEqualTo("5df41881-3aed-3515-88a7-2f4a814cf09e");
        assertThat(version5.toString()).isEqualTo("2ed6657d-e927-568b-95e1-2665a8aea6a2");
        assertThat(GUID.NIL.compareTo(GUID.MAX)).isNegative();
        assertThat(GUID.MAX.compareTo(GUID.NIL)).isPositive();
        assertThat(GUID.valid("not-a-guid")).isFalse();
    }

    @Test
    void machineFingerprintExposesConsistentHashNumberAndUuidRepresentations()
            throws NoSuchAlgorithmException {
        String machineString = MachineId.getMachineString();
        byte[] machineHash = MachineId.getMachineHash();
        byte[] expectedHash = MessageDigest.getInstance("SHA-256")
                .digest(machineString.getBytes(StandardCharsets.UTF_8));
        ByteBuffer hashBytes = ByteBuffer.wrap(machineHash);
        long expectedMostSignificantBits = (hashBytes.getLong() & 0xffffffffffff0fffL) | 0x4000L;
        long expectedLeastSignificantBits = (hashBytes.getLong() & 0x3fffffffffffffffL)
                | 0x8000000000000000L;

        assertThat(machineString).isNotBlank();
        assertThat(machineHash).containsExactly(expectedHash);
        assertThat(MachineId.getMachineHexa()).isEqualTo(HexFormat.of().formatHex(machineHash));
        assertThat(MachineId.getMachineId()).isEqualTo(ByteBuffer.wrap(machineHash).getLong());
        assertThat(MachineId.getMachineUuid())
                .isEqualTo(new UUID(expectedMostSignificantBits, expectedLeastSignificantBits));
    }

    private static void assertVersion(List<UUID> uuids, int version) {
        assertThat(uuids).allSatisfy(uuid -> {
            assertThat(uuid.version()).isEqualTo(version);
            assertThat(uuid.variant()).isEqualTo(2);
        });
    }

    private static <T> void assertCodecRoundTrip(UuidCodec<T> codec, UUID uuid) {
        T encoded = codec.encode(uuid);
        assertThat(codec.decode(encoded)).isEqualTo(uuid);
    }
}
