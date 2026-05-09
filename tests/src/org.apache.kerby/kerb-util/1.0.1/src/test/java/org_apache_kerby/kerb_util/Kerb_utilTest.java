/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.kerby.kerberos.kerb.ccache.CredCacheInputStream;
import org.apache.kerby.kerberos.kerb.ccache.CredCacheOutputStream;
import org.apache.kerby.kerberos.kerb.ccache.Credential;
import org.apache.kerby.kerberos.kerb.ccache.CredentialCache;
import org.apache.kerby.kerberos.kerb.ccache.Tag;
import org.apache.kerby.kerberos.kerb.keytab.Keytab;
import org.apache.kerby.kerberos.kerb.keytab.KeytabEntry;
import org.apache.kerby.kerberos.kerb.keytab.KeytabInputStream;
import org.apache.kerby.kerberos.kerb.keytab.KeytabOutputStream;
import org.apache.kerby.kerberos.kerb.type.KerberosTime;
import org.apache.kerby.kerberos.kerb.type.ad.AuthorizationData;
import org.apache.kerby.kerberos.kerb.type.ad.AuthorizationDataEntry;
import org.apache.kerby.kerberos.kerb.type.ad.AuthorizationType;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType;
import org.apache.kerby.kerberos.kerb.type.base.HostAddress;
import org.apache.kerby.kerberos.kerb.type.base.HostAddresses;
import org.apache.kerby.kerberos.kerb.type.base.HostAddrType;
import org.apache.kerby.kerberos.kerb.type.base.NameType;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Kerb_utilTest {
    private static final String REALM = "EXAMPLE.COM";

    @TempDir
    Path temporaryDirectory;

    @Test
    void keytabRoundTripsEntriesThroughStreamsAndFiles() throws IOException {
        PrincipalName primary = principal("alice/admin@" + REALM, NameType.NT_PRINCIPAL);
        PrincipalName service = principal("HTTP/host.example.com@" + REALM, NameType.NT_SRV_HST);
        KeytabEntry primaryEntry = keytabEntry(primary, EncryptionType.AES128_CTS_HMAC_SHA1_96, 7, 1L,
                new byte[] {1, 2, 3, 4});
        KeytabEntry serviceEntry = keytabEntry(service, EncryptionType.AES256_CTS_HMAC_SHA1_96, 9, 2L,
                new byte[] {5, 6, 7, 8, 9});

        Keytab keytab = new Keytab();
        keytab.addKeytabEntries(Arrays.asList(primaryEntry, serviceEntry));

        assertThat(keytab.getPrincipals()).containsExactlyInAnyOrder(primary, service);
        assertThat(keytab.getKeytabEntries(primary)).containsExactly(primaryEntry);
        assertThat(keytab.getKey(primary, EncryptionType.AES128_CTS_HMAC_SHA1_96))
                .isEqualTo(primaryEntry.getKey());
        assertThat(keytab.getKey(primary, EncryptionType.AES256_CTS_HMAC_SHA1_96)).isNull();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        keytab.store(outputStream);
        byte[] encoded = outputStream.toByteArray();

        assertThat(encoded).startsWith((byte) 0x05, (byte) 0x02);

        Keytab loadedFromStream = Keytab.loadKeytab(new ByteArrayInputStream(encoded));
        assertThat(loadedFromStream.getPrincipals()).containsExactlyInAnyOrder(primary, service);
        assertThat(loadedFromStream.getKeytabEntries(primary)).containsExactly(primaryEntry);
        assertThat(loadedFromStream.getKeytabEntries(service)).containsExactly(serviceEntry);

        loadedFromStream.removeKeytabEntries(primary, 7);
        assertThat(loadedFromStream.getKeytabEntries(primary)).isEmpty();
        assertThat(loadedFromStream.getKeytabEntries(service)).containsExactly(serviceEntry);

        loadedFromStream.removeKeytabEntry(serviceEntry);
        assertThat(loadedFromStream.getKeytabEntries(service)).isEmpty();

        File keytabFile = temporaryDirectory.resolve("round-trip.keytab").toFile();
        keytab.store(keytabFile);
        Keytab loadedFromFile = Keytab.loadKeytab(keytabFile);
        assertThat(loadedFromFile.getKeytabEntries(primary)).containsExactly(primaryEntry);

        loadedFromFile.removeKeytabEntries(primary);
        assertThat(loadedFromFile.getKeytabEntries(primary)).isEmpty();
    }

    @Test
    void keytabStreamsReadAndWriteKerberosStructures() throws IOException {
        PrincipalName service = principal("HTTP/host.example.com@" + REALM, NameType.NT_SRV_HST);
        KerberosTime timestamp = new KerberosTime(3_000L);
        EncryptionKey key = new EncryptionKey(EncryptionType.AES256_CTS_HMAC_SHA1_96, new byte[] {10, 11, 12, 13}, 5);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        KeytabOutputStream keytabOutputStream = new KeytabOutputStream(outputStream);
        keytabOutputStream.writePrincipal(service, Keytab.V502);
        keytabOutputStream.writeTime(timestamp);
        keytabOutputStream.writeKey(key, Keytab.V502);
        keytabOutputStream.writeCountedString("kerby-snowman");
        keytabOutputStream.writeCountedOctets(new byte[] {42, 43, 44});
        keytabOutputStream.flush();

        KeytabInputStream inputStream = new KeytabInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
        assertThat(inputStream.readPrincipal(Keytab.V502)).isEqualTo(service);
        assertThat(inputStream.readTime()).isEqualTo(timestamp);
        assertThat(inputStream.readKey()).isEqualTo(key);
        assertThat(inputStream.readCountedString()).isEqualTo("kerby-snowman");
        assertThat(inputStream.readCountedOctets()).containsExactly((byte) 42, (byte) 43, (byte) 44);
        assertThat(inputStream.available()).isZero();
    }

    @Test
    void keytabRejectsNullStreamsAndTruncatedOctets() throws IOException {
        Keytab keytab = new Keytab();
        assertThatThrownBy(() -> keytab.load((ByteArrayInputStream) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keytab.store((ByteArrayOutputStream) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Keytab.loadKeytab(temporaryDirectory.resolve("missing.keytab").toFile()))
                .isInstanceOf(IllegalArgumentException.class);

        ByteArrayOutputStream truncated = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(truncated);
        dataOutputStream.writeShort(4);
        dataOutputStream.writeByte(1);
        dataOutputStream.flush();

        KeytabInputStream inputStream = new KeytabInputStream(new ByteArrayInputStream(truncated.toByteArray()));
        assertThatThrownBy(inputStream::readCountedOctets).isInstanceOf(IOException.class)
                .hasMessageContaining("Unexpected octets len");
    }

    @Test
    void credentialCacheRoundTripsPrimaryPrincipalAndManagesCredentials() throws IOException {
        PrincipalName client = principal("alice@" + REALM, NameType.NT_PRINCIPAL);
        Credential firstCredential = new Credential();
        Credential secondCredential = new Credential();
        Tag deltaTime = new Tag(CredentialCache.FCC_TAG_DELTATIME, 123, 456);

        CredentialCache cache = new CredentialCache();
        cache.setPrimaryPrincipal(client);
        cache.setVersion(CredentialCache.FCC_FVNO_4);
        cache.setTags(Collections.singletonList(deltaTime));
        cache.addCredential(null);
        cache.addCredential(firstCredential);
        cache.addCredentials(Collections.singletonList(secondCredential));

        assertThat(cache.getTags()).containsExactly(deltaTime);
        assertThat(cache.getCredentials()).containsExactly(firstCredential, secondCredential);

        cache.removeCredential(firstCredential);
        cache.removeCredentials(Collections.singletonList(secondCredential));
        cache.removeCredential(null);
        cache.removeCredentials(null);

        assertThat(cache.getCredentials()).isEmpty();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        cache.store(outputStream);
        CredentialCache loadedFromStream = new CredentialCache();
        loadedFromStream.load(new ByteArrayInputStream(outputStream.toByteArray()));

        assertThat(loadedFromStream.getVersion()).isEqualTo(CredentialCache.FCC_FVNO_3);
        assertThat(loadedFromStream.getPrimaryPrincipal()).isEqualTo(client);
        assertThat(loadedFromStream.getCredentials()).isEmpty();

        File cacheFile = temporaryDirectory.resolve("round-trip.ccache").toFile();
        cache.store(cacheFile);
        CredentialCache loadedFromFile = new CredentialCache();
        loadedFromFile.load(cacheFile);

        assertThat(loadedFromFile.getPrimaryPrincipal()).isEqualTo(client);
        assertThat(loadedFromFile.getCredentials()).isEmpty();
    }

    @Test
    void credentialCacheStreamsReadAndWriteKerberosStructures() throws IOException {
        PrincipalName client = principal("alice@" + REALM, NameType.NT_PRINCIPAL);
        EncryptionKey key = new EncryptionKey(EncryptionType.AES128_CTS_HMAC_SHA1_96, new byte[] {1, 3, 5, 7});
        KerberosTime[] times = new KerberosTime[] {
                new KerberosTime(1_000L),
                new KerberosTime(2_000L),
                new KerberosTime(3_000L),
                new KerberosTime(4_000L)
        };

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CredCacheOutputStream cacheOutputStream = new CredCacheOutputStream(outputStream);
        cacheOutputStream.writePrincipal(client, CredentialCache.FCC_FVNO_4);
        cacheOutputStream.writeKey(key, CredentialCache.FCC_FVNO_3);
        cacheOutputStream.writeTimes(times);
        cacheOutputStream.writeIsSkey(true);
        cacheOutputStream.writeIsSkey(false);
        cacheOutputStream.writeAddresses(null);
        cacheOutputStream.writeAuthzData(null);
        cacheOutputStream.writeTicket(null);
        cacheOutputStream.flush();

        CredCacheInputStream inputStream = new CredCacheInputStream(
                new ByteArrayInputStream(outputStream.toByteArray()));
        assertThat(inputStream.readPrincipal(CredentialCache.FCC_FVNO_4)).isEqualTo(client);
        assertThat(inputStream.readKey(CredentialCache.FCC_FVNO_3)).isEqualTo(key);
        assertThat(inputStream.readTimes()).containsExactly(times);
        assertThat(inputStream.readIsSkey()).isTrue();
        assertThat(inputStream.readIsSkey()).isFalse();
        assertThat(inputStream.readAddr()).isNull();
        assertThat(inputStream.readAuthzData()).isNull();
        assertThat(inputStream.readTicket()).isNull();
        assertThat(inputStream.available()).isZero();
    }

    @Test
    void credentialCacheInputStreamReadsAddressesAuthorizationDataAndTicketFlags() throws IOException {
        byte[] ipv4Address = new byte[] {127, 0, 0, 1};
        byte[] ipv6Address = new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        byte[] authorizationPayload = new byte[] {11, 22, 33, 44};
        int ticketFlags = 0x40000000;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        dataOutputStream.writeInt(2);
        writeAddress(dataOutputStream, HostAddrType.ADDRTYPE_INET, ipv4Address);
        writeAddress(dataOutputStream, HostAddrType.ADDRTYPE_INET6, ipv6Address);
        dataOutputStream.writeInt(1);
        writeAuthorizationDataEntry(dataOutputStream, AuthorizationType.AD_IF_RELEVANT, authorizationPayload);
        dataOutputStream.writeInt(ticketFlags);
        dataOutputStream.flush();

        CredCacheInputStream inputStream = new CredCacheInputStream(
                new ByteArrayInputStream(outputStream.toByteArray()));
        HostAddresses addresses = inputStream.readAddr();
        assertThat(addresses.getElements()).hasSize(2);
        HostAddress inetAddress = addresses.getElements().get(0);
        assertThat(inetAddress.getAddrType()).isEqualTo(HostAddrType.ADDRTYPE_INET);
        assertThat(inetAddress.getAddress()).containsExactly(ipv4Address);
        HostAddress inet6Address = addresses.getElements().get(1);
        assertThat(inet6Address.getAddrType()).isEqualTo(HostAddrType.ADDRTYPE_INET6);
        assertThat(inet6Address.getAddress()).containsExactly(ipv6Address);

        AuthorizationData authorizationData = inputStream.readAuthzData();
        assertThat(authorizationData.getElements()).hasSize(1);
        AuthorizationDataEntry authorizationDataEntry = authorizationData.getElements().get(0);
        assertThat(authorizationDataEntry.getAuthzType()).isEqualTo(AuthorizationType.AD_IF_RELEVANT);
        assertThat(authorizationDataEntry.getAuthzData()).containsExactly(authorizationPayload);
        assertThat(inputStream.readTicketFlags().getFlags()).isEqualTo(ticketFlags);
        assertThat(inputStream.available()).isZero();
    }

    @Test
    void credentialCacheRejectsNullStreamsAndTruncatedOctets() throws IOException {
        CredentialCache cache = new CredentialCache();
        assertThatThrownBy(() -> cache.load((ByteArrayInputStream) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.store((ByteArrayOutputStream) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.load(temporaryDirectory.resolve("missing.ccache").toFile()))
                .isInstanceOf(IllegalArgumentException.class);

        ByteArrayOutputStream truncated = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(truncated);
        dataOutputStream.writeInt(6);
        dataOutputStream.writeByte(1);
        dataOutputStream.flush();

        CredCacheInputStream inputStream = new CredCacheInputStream(new ByteArrayInputStream(truncated.toByteArray()));
        assertThatThrownBy(inputStream::readCountedOctets).isInstanceOf(IOException.class)
                .hasMessageContaining("Unexpected octets len");
    }

    private static PrincipalName principal(String principalName, NameType nameType) {
        return new PrincipalName(principalName, nameType);
    }

    private static void writeAddress(DataOutputStream dataOutputStream, HostAddrType addressType, byte[] address)
            throws IOException {
        dataOutputStream.writeShort(addressType.getValue());
        dataOutputStream.writeInt(address.length);
        dataOutputStream.write(address);
    }

    private static void writeAuthorizationDataEntry(DataOutputStream dataOutputStream,
            AuthorizationType authorizationType, byte[] payload) throws IOException {
        dataOutputStream.writeShort(authorizationType.getValue());
        dataOutputStream.writeInt(payload.length);
        dataOutputStream.write(payload);
    }

    private static KeytabEntry keytabEntry(PrincipalName principal, EncryptionType encryptionType, int kvno,
            long timestampInSeconds, byte[] keyData) {
        EncryptionKey key = new EncryptionKey(encryptionType, keyData, kvno);
        return new KeytabEntry(principal, new KerberosTime(timestampInSeconds * 1_000L), kvno, key);
    }
}
