/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.KeyPair;
import java.security.SecureRandom;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.jcajce.provider.mceliece.BCMcElieceCCA2PrivateKey;
import org.bouncycastle.pqc.jcajce.provider.mceliece.BCMcElieceCCA2PublicKey;
import org.bouncycastle.pqc.legacy.crypto.mceliece.McElieceCCA2KeyGenerationParameters;
import org.bouncycastle.pqc.legacy.crypto.mceliece.McElieceCCA2KeyPairGenerator;
import org.bouncycastle.pqc.legacy.crypto.mceliece.McElieceCCA2Parameters;
import org.bouncycastle.pqc.legacy.crypto.mceliece.McElieceCCA2PrivateKeyParameters;
import org.bouncycastle.pqc.legacy.crypto.mceliece.McElieceCCA2PublicKeyParameters;

final class McElieceFixtures {
    private McElieceFixtures() {
    }

    static KeyPair generate() {
        McElieceCCA2KeyPairGenerator generator = new McElieceCCA2KeyPairGenerator();
        generator.init(new McElieceCCA2KeyGenerationParameters(new SecureRandom(),
                new McElieceCCA2Parameters(9, 20)));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        return new KeyPair(new BCMcElieceCCA2PublicKey((McElieceCCA2PublicKeyParameters) pair.getPublic()),
                new BCMcElieceCCA2PrivateKey((McElieceCCA2PrivateKeyParameters) pair.getPrivate()));
    }
}
