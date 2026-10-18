/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.RsaAlgorithm;
import org.springframework.security.crypto.encrypt.RsaRawEncryptor;
import org.springframework.security.crypto.encrypt.RsaSecretEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Self-keyed RSA encryption through the no-argument `RsaSecretEncryptor` and `RsaRawEncryptor`
 * constructors, which generate their own key pair, and the block chunking and text charset that
 * `RsaRawEncryptor` applies.
 *
 * A payment relay sits between card terminals and the acquirer. Each time it starts it creates
 * a fresh RSA key pair in memory, publishes the public half to the terminals, and decrypts the
 * PIN blocks and receipts they send. Nothing is persisted, so a restart invalidates whatever was
 * sealed for the previous key. Older terminals still speak ISO-8859-1, so the relay keeps a raw
 * encryptor configured with that charset for them.
 */
public class PaymentTerminalPinRelayTest {

    /// The size of every ciphertext block under the 1024-bit keys the encryptors generate.
    private static final int SELF_GENERATED_BLOCK = 128;

    /// The largest plaintext chunk PKCS#1 v1.5 padding fits into one 1024-bit block.
    private static final int PKCS1_CHUNK = SELF_GENERATED_BLOCK - 11;

    @Test
    void terminalsSealPinBlocksWithThePublishedKeyOfTheRunningRelay() {
        Relay relay = Relay.start();
        String published = relay.publicKey();
        assertTrue(published.startsWith("ssh-rsa AAAAB3NzaC1yc2E"), published);

        Terminal lobby = new Terminal("lobby-01", published);
        Terminal driveThrough = new Terminal("drive-02", published);
        assertFalse(lobby.encryptor.canDecrypt(), "a terminal only holds the public key");

        String lobbyPin = lobby.sealPin("4921");
        String drivePin = driveThrough.sealPin("4921");
        assertNotEquals(lobbyPin, drivePin, "each seal uses a fresh session key");
        assertFalse(lobbyPin.contains("4921"));

        assertEquals("lobby-01:4921", relay.open(lobbyPin));
        assertEquals("drive-02:4921", relay.open(drivePin));
        assertThrows(IllegalStateException.class, () -> lobby.encryptor.decrypt(drivePin));

        Relay other = Relay.start();
        assertNotEquals(published, other.publicKey(), "every relay generates its own key pair");
    }

    @Test
    void aRestartedRelayCannotOpenWhatWasSealedForThePreviousKey() {
        Relay beforeRestart = Relay.start();
        Terminal terminal = new Terminal("lobby-01", beforeRestart.publicKey());
        String inFlight = terminal.sealPin("7302");
        assertEquals("lobby-01:7302", beforeRestart.open(inFlight));

        Relay afterRestart = Relay.start();
        assertThrows(IllegalStateException.class, () -> afterRestart.open(inFlight));

        terminal = new Terminal("lobby-01", afterRestart.publicKey());
        assertEquals("lobby-01:7302", afterRestart.open(terminal.sealPin("7302")));

        TextEncryptor defaults = new RsaSecretEncryptor();
        assertEquals("settlement-batch-118", defaults.decrypt(defaults.encrypt("settlement-batch-118")));
        RsaSecretEncryptor gcm = new RsaSecretEncryptor(RsaAlgorithm.OAEP, "0a1b2c3d", true);
        assertEquals("settlement-batch-119", gcm.decrypt(gcm.encrypt("settlement-batch-119")));
    }

    @Test
    void rawEncryptionSplitsLongReceiptsIntoKeySizedBlocks() {
        RsaRawEncryptor relay = new RsaRawEncryptor();
        Map<Integer, Integer> blocksByLength = new LinkedHashMap<>();
        for (int length : new int[] {1, PKCS1_CHUNK, PKCS1_CHUNK + 1, 3 * PKCS1_CHUNK, 3 * PKCS1_CHUNK + 40}) {
            String receipt = receipt(length);
            String sealed = relay.encrypt(receipt);
            byte[] raw = Base64.getDecoder().decode(sealed);
            assertEquals(0, raw.length % SELF_GENERATED_BLOCK, "whole blocks only");
            blocksByLength.put(length, raw.length / SELF_GENERATED_BLOCK);
            assertEquals(receipt, relay.decrypt(sealed));
        }
        assertEquals(Map.of(1, 1, PKCS1_CHUNK, 1, PKCS1_CHUNK + 1, 2, 3 * PKCS1_CHUNK, 3,
                3 * PKCS1_CHUNK + 40, 4), blocksByLength);

        byte[] journal = receipt(500).getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(journal, relay.decrypt(relay.encrypt(journal)));

        RsaRawEncryptor oaep = new RsaRawEncryptor(RsaAlgorithm.OAEP);
        String sealed = oaep.encrypt(receipt(300));
        assertEquals(receipt(300), oaep.decrypt(sealed));
        assertThrows(IllegalStateException.class, () -> new RsaRawEncryptor(RsaAlgorithm.OAEP).decrypt(sealed));
    }

    @Test
    void legacyTerminalsSendLatinOneTextThatOnlyALatinOneEncryptorReadsBack() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        assertEquals(256, RsaRawEncryptor.getByteLength((RSAPublicKey) keyPair.getPublic()));

        String merchant = "Caf\u00e9 Z\u00fcrich";
        RsaRawEncryptor legacyTerminal = new RsaRawEncryptor("ISO-8859-1", keyPair.getPublic(), null);
        RsaRawEncryptor latinRelay = new RsaRawEncryptor("ISO-8859-1", keyPair.getPublic(), keyPair.getPrivate(),
                RsaAlgorithm.OAEP);
        RsaRawEncryptor oaepTerminal = new RsaRawEncryptor("ISO-8859-1", keyPair.getPublic(), null,
                RsaAlgorithm.OAEP);
        RsaRawEncryptor utf8Relay = new RsaRawEncryptor(keyPair);

        String sealed = legacyTerminal.encrypt(merchant);
        assertEquals(256, Base64.getDecoder().decode(sealed).length);
        assertThrows(IllegalStateException.class, () -> legacyTerminal.decrypt(sealed));
        assertArrayEquals(merchant.getBytes(StandardCharsets.ISO_8859_1),
                utf8Relay.decrypt(Base64.getDecoder().decode(sealed)), "one byte per accented letter");

        String misread = utf8Relay.decrypt(sealed);
        assertNotEquals(merchant, misread);
        assertTrue(misread.contains("\ufffd"), misread);
        assertEquals(merchant, utf8Relay.decrypt(utf8Relay.encrypt(merchant)));

        assertEquals(merchant, latinRelay.decrypt(oaepTerminal.encrypt(merchant)));

        RsaSecretEncryptor latinSecrets = new RsaSecretEncryptor("ISO-8859-1", keyPair.getPublic(),
                keyPair.getPrivate(), RsaAlgorithm.OAEP);
        RsaSecretEncryptor latinSecretTerminal = new RsaSecretEncryptor("ISO-8859-1", keyPair.getPublic(), null);
        assertEquals(merchant, latinSecrets.decrypt(latinSecrets.encrypt(merchant)));
        assertFalse(latinSecretTerminal.canDecrypt());
        assertTrue(latinSecrets.canDecrypt());
    }

    private static String receipt(int length) {
        StringBuilder text = new StringBuilder(length);
        String line = "ITEM 0042 ESPRESSO 2.80 EUR;";
        while (text.length() < length) {
            text.append(line);
        }
        return text.substring(0, length);
    }

    /// The relay process: generates its key pair on start and opens what terminals seal.
    private static final class Relay {
        private final RsaSecretEncryptor encryptor;

        private Relay(RsaSecretEncryptor encryptor) {
            this.encryptor = encryptor;
        }

        static Relay start() {
            return new Relay(new RsaSecretEncryptor(RsaAlgorithm.OAEP));
        }

        String publicKey() {
            return encryptor.getPublicKey();
        }

        String open(String sealed) {
            return encryptor.decrypt(sealed);
        }
    }

    /// A card terminal provisioned with the relay's published `ssh-rsa` public key.
    private static final class Terminal {
        private final String id;
        private final RsaSecretEncryptor encryptor;

        Terminal(String id, String publishedKey) {
            this.id = id;
            this.encryptor = new RsaSecretEncryptor(publishedKey, RsaAlgorithm.OAEP);
        }

        String sealPin(String pin) {
            return encryptor.encrypt(id + ":" + pin);
        }
    }
}
