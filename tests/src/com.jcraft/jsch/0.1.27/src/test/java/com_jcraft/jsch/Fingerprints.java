/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// Computes the colon-separated MD5 fingerprint OpenSSH prints for a public key blob.
final class Fingerprints {
    private Fingerprints() {
    }

    static String md5(byte[] publicKeyBlob) throws NoSuchAlgorithmException {
        return HexFormat.ofDelimiter(":").formatHex(MessageDigest.getInstance("MD5").digest(publicKeyBlob));
    }
}
