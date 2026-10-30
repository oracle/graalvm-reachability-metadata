/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.spec.DHParameterSpec;

import org.bouncycastle.crypto.agreement.DHStandardGroups;
import org.bouncycastle.crypto.params.DHParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ElGamalParameterSpec;
import org.bouncycastle.jce.spec.GOST3410ParameterSpec;
import org.bouncycastle.pqc.crypto.lms.LMOtsParameters;
import org.bouncycastle.pqc.crypto.lms.LMSigParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.BIKEParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.CMCEParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FrodoParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.HQCParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRULPRimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRUParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.PicnicParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.RainbowParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SABERParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SNTRUPrimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.XMSSMTParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec;

final class KeyPairFixtures {
    static final Provider BC = new BouncyCastleProvider();
    static final Provider PQC = new BouncyCastlePQCProvider();
    private static final Map<String, KeyPair> PAIRS = new HashMap<>();

    private KeyPairFixtures() {
    }

    static synchronized KeyPair classical(String algorithm) throws GeneralSecurityException {
        AlgorithmParameterSpec parameters = switch (algorithm) {
            case "DH" -> dhParameters();
            case "ElGamal" -> new ElGamalParameterSpec(dhParameters().getP(), dhParameters().getG());
            case "EC" -> new ECGenParameterSpec("secp256r1");
            case "DSTU4145" -> new ECGenParameterSpec("1.2.804.2.1.1.1.1.3.1.1.2.0");
            case "ECGOST3410" -> new ECGenParameterSpec("GostR3410-2001-CryptoPro-A");
            case "ECGOST3410-2012" -> new ECGenParameterSpec("Tc26-Gost-3410-12-256-paramSetA");
            case "GOST3410" -> new GOST3410ParameterSpec("GostR3410-94-CryptoPro-A");
            default -> null;
        };
        return generate(algorithm, parameters, BC);
    }

    static synchronized KeyPair postQuantum(String algorithm) throws GeneralSecurityException {
        AlgorithmParameterSpec parameters = switch (algorithm) {
            case "BIKE" -> BIKEParameterSpec.bike128;
            case "CMCE" -> CMCEParameterSpec.mceliece348864;
            case "Dilithium" -> DilithiumParameterSpec.dilithium2;
            case "Falcon" -> FalconParameterSpec.falcon_512;
            case "Frodo" -> FrodoParameterSpec.frodokem640aes;
            case "HQC" -> HQCParameterSpec.hqc128;
            case "Kyber" -> KyberParameterSpec.kyber512;
            case "LMS" -> new LMSKeyGenParameterSpec(LMSigParameters.lms_sha256_n32_h5,
                    LMOtsParameters.sha256_n32_w4);
            case "NTRU" -> NTRUParameterSpec.ntruhps2048509;
            case "NTRULPRime" -> NTRULPRimeParameterSpec.ntrulpr653;
            case "SNTRUPrime" -> SNTRUPrimeParameterSpec.sntrup653;
            case "Picnic" -> PicnicParameterSpec.picnicl1fs;
            case "Rainbow" -> RainbowParameterSpec.rainbowIIIclassic;
            case "SABER" -> SABERParameterSpec.lightsaberkem128r3;
            case "SPHINCS256" -> new SPHINCS256KeyGenParameterSpec();
            case "SPHINCSPLUS" -> SPHINCSPlusParameterSpec.sha2_128f_robust;
            case "XMSS" -> new XMSSParameterSpec(4, XMSSParameterSpec.SHA256);
            case "XMSSMT" -> new XMSSMTParameterSpec(4, 2, XMSSMTParameterSpec.SHA256);
            default -> null;
        };
        return generate(algorithm, parameters, PQC);
    }

    private static KeyPair generate(String algorithm, AlgorithmParameterSpec parameters, Provider provider)
            throws GeneralSecurityException {
        KeyPair existing = PAIRS.get(algorithm);
        if (existing != null) {
            return existing;
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm, provider);
        if (parameters != null) {
            generator.initialize(parameters, new SecureRandom());
        } else if (algorithm.equals("DSA") || algorithm.equals("RSASSA-PSS")) {
            generator.initialize(1024, new SecureRandom());
        } else if (algorithm.equals("NH")) {
            generator.initialize(1024, new SecureRandom());
        }
        KeyPair pair = generator.generateKeyPair();
        PAIRS.put(algorithm, pair);
        return pair;
    }

    private static DHParameterSpec dhParameters() {
        DHParameters group = DHStandardGroups.rfc3526_2048;
        return new DHParameterSpec(group.getP(), group.getG());
    }
}
