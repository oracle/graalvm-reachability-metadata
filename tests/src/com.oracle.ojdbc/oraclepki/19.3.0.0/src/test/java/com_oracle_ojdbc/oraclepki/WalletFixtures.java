/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import oracle.security.pki.OraclePKIProvider;
import oracle.security.pki.OracleWallet;

final class WalletFixtures {
    static final String PASSWORD = "Wallet-Secret-42";
    static final X500Principal SUBJECT = new X500Principal("CN=Wallet Test,O=Metadata Tests");

    private WalletFixtures() {
    }

    static OracleWallet emptyWallet() throws Exception {
        OracleWallet wallet = new OracleWallet();
        wallet.create(PASSWORD.toCharArray());
        return wallet;
    }

    static OracleWallet identityWallet() throws Exception {
        OracleWallet wallet = emptyWallet();
        wallet.setCertSignAlgorithm("sha256");
        wallet.createSelfSigned(SUBJECT, 2048, 30);
        return wallet;
    }

    static X509Certificate certificate() throws Exception {
        return identityWallet().getCert(0, 0);
    }

    static KeyStore trustStore(X509Certificate certificate) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12", new OraclePKIProvider());
        store.load(null, PASSWORD.toCharArray());
        store.setCertificateEntry("trusted", certificate);
        return store;
    }
}
