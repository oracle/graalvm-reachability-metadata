/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Attribute;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.RoleSyntax;
import org.bouncycastle.asn1.x509.Target;
import org.bouncycastle.asn1.x509.TargetInformation;
import org.bouncycastle.asn1.x509.Targets;
import org.bouncycastle.asn1.x509.X509AttributeIdentifiers;
import org.bouncycastle.cert.AttributeCertificateHolder;
import org.bouncycastle.cert.AttributeCertificateIssuer;
import org.bouncycastle.cert.X509AttributeCertificateHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2AttributeCertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.selector.X509AttributeCertificateHolderSelector;
import org.bouncycastle.cert.selector.X509AttributeCertificateHolderSelectorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.util.CollectionStore;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issues and evaluates X.509 attribute certificates with the bcpkix attribute certificate builder,
 * holder and issuer selectors, and attribute certificate store selector.
 *
 * An attribute authority grants employees roles for internal services by issuing attribute
 * certificates bound to their identity certificates and restricted to target services. A
 * service gateway receives the encoded attribute certificates, stores them, and for each request
 * looks up the certificates held by the caller, valid now, and targeted at itself; it then checks
 * the authority signature and reads the granted roles.
 */
class RoleBasedAccessAttributeCertificateTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private static final GeneralName BILLING_SERVICE =
            new GeneralName(GeneralName.dNSName, "billing.internal.acme.test");

    private static final GeneralName REPORTING_SERVICE =
            new GeneralName(GeneralName.dNSName, "reporting.internal.acme.test");

    private static final GeneralName ROLE_AUTHORITY =
            new GeneralName(GeneralName.uniformResourceIdentifier, "https://iam.acme.test/roles");

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void gatewayGrantsRolesFromAttributeCertificatesBoundToTheCaller() throws Exception {
        Organisation acme = new Organisation();
        X509CertificateHolder alice = acme.issueEmployee("Alice Admin", 101);
        X509CertificateHolder bob = acme.issueEmployee("Bob Analyst", 102);

        AttributeAuthority authority = new AttributeAuthority(acme, 50);
        List<byte[]> published = List.of(
                authority.grant(alice, 9001, now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                        List.of(BILLING_SERVICE), "billing-admin", "billing-auditor").getEncoded(),
                authority.grant(alice, 9002, now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                        List.of(REPORTING_SERVICE), "report-viewer").getEncoded(),
                authority.grant(bob, 9003, now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                        List.of(BILLING_SERVICE, REPORTING_SERVICE), "billing-auditor").getEncoded(),
                authority.grant(bob, 9004, now.minus(60, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS),
                        List.of(BILLING_SERVICE), "billing-admin").getEncoded());

        ServiceGateway billing = new ServiceGateway(BILLING_SERVICE, authority.certificate(), published);
        ServiceGateway reporting = new ServiceGateway(REPORTING_SERVICE, authority.certificate(), published);

        assertThat(billing.rolesOf(alice, now)).containsExactlyInAnyOrder("billing-admin", "billing-auditor");
        assertThat(billing.rolesOf(bob, now)).containsExactly("billing-auditor");
        assertThat(reporting.rolesOf(alice, now)).containsExactly("report-viewer");
        assertThat(reporting.rolesOf(bob, now)).containsExactly("billing-auditor");
        assertThat(billing.rolesOf(bob, Date.from(now.minus(45, ChronoUnit.DAYS))))
                .as("the expired grant was valid 45 days ago")
                .containsExactly("billing-admin");
    }

    @Test
    void attributeCertificateCarriesHolderIssuerRolesAndTargets() throws Exception {
        Organisation acme = new Organisation();
        X509CertificateHolder alice = acme.issueEmployee("Alice Admin", 101);
        X509CertificateHolder bob = acme.issueEmployee("Bob Analyst", 102);
        AttributeAuthority authority = new AttributeAuthority(acme, 50);

        X509AttributeCertificateHolder issued = authority.grant(alice, 9001,
                now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                List.of(BILLING_SERVICE, REPORTING_SERVICE), "billing-admin");
        X509AttributeCertificateHolder received = new X509AttributeCertificateHolder(issued.getEncoded());

        assertThat(received.getEncoded()).isEqualTo(issued.getEncoded());
        assertThat(received.getVersion()).isEqualTo(2);
        assertThat(received.getSerialNumber()).isEqualTo(BigInteger.valueOf(9001));
        assertThat(received.getNotAfter()).isEqualTo(Date.from(now.plus(30, ChronoUnit.DAYS)));
        assertThat(received.isValidOn(Date.from(now))).isTrue();
        assertThat(received.isValidOn(Date.from(now.plus(31, ChronoUnit.DAYS)))).isFalse();

        AttributeCertificateHolder holder = received.getHolder();
        assertThat(holder.getSerialNumber()).isEqualTo(alice.getSerialNumber());
        assertThat(holder.getIssuer()).containsExactly(acme.caName());
        assertThat(holder.match(alice)).isTrue();
        assertThat(holder.match(bob)).isFalse();

        assertThat(received.getIssuer().getNames()).containsExactly(authority.certificate().getSubject());
        assertThat(received.getIssuer().match(authority.certificate())).isTrue();
        assertThat(received.getIssuer().match(alice)).isFalse();

        Attribute[] roles = received.getAttributes(X509AttributeIdentifiers.id_at_role);
        assertThat(roles).hasSize(1);
        RoleSyntax role = RoleSyntax.getInstance(roles[0].getAttrValues().getObjectAt(0));
        assertThat(role.getRoleNameAsString()).isEqualTo("billing-admin");
        assertThat(role.getRoleAuthorityAsString()).containsExactly("https://iam.acme.test/roles");

        assertThat(received.getCriticalExtensionOIDs()).containsExactly(Extension.targetInformation);
        TargetInformation targets =
                TargetInformation.getInstance(received.getExtension(Extension.targetInformation).getParsedValue());
        assertThat(targets.getTargetsObjects()[0].getTargets())
                .extracting(Target::getTargetName)
                .containsExactly(BILLING_SERVICE, REPORTING_SERVICE);

        assertThat(received.isSignatureValid(verifierFor(authority.certificate()))).isTrue();
        assertThat(received.isSignatureValid(verifierFor(acme.caCertificate()))).isFalse();
    }

    @Test
    void gatewayIgnoresAttributeCertificatesFromAnUntrustedAuthority() throws Exception {
        Organisation acme = new Organisation();
        X509CertificateHolder alice = acme.issueEmployee("Alice Admin", 101);
        AttributeAuthority trusted = new AttributeAuthority(acme, 50);
        AttributeAuthority rogue = new AttributeAuthority(acme, 51);

        X509AttributeCertificateHolder forged = rogue.grant(alice, 9100,
                now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                List.of(BILLING_SERVICE), "billing-admin");
        X509AttributeCertificateHolder genuine = trusted.grant(alice, 9101,
                now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                List.of(BILLING_SERVICE), "billing-auditor");

        ServiceGateway billing = new ServiceGateway(BILLING_SERVICE, trusted.certificate(),
                List.of(forged.getEncoded(), genuine.getEncoded()));

        assertThat(billing.matching(alice, now)).hasSize(2);
        assertThat(billing.rolesOf(alice, now)).containsExactly("billing-auditor");

        X509v2AttributeCertificateBuilder renewal = new X509v2AttributeCertificateBuilder(genuine);
        renewal.removeExtension(Extension.targetInformation);
        X509AttributeCertificateHolder untargeted = renewal.build(trusted.signer());
        assertThat(untargeted.hasExtensions()).isFalse();
        assertThat(untargeted.getHolder()).isEqualTo(genuine.getHolder());
        assertThat(untargeted.isSignatureValid(verifierFor(trusted.certificate()))).isTrue();
    }

    private static ContentVerifierProvider verifierFor(X509CertificateHolder certificate) throws Exception {
        return new JcaContentVerifierProviderBuilder().build(certificate);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** The identity CA that issues employee certificates. */
    private final class Organisation {

        private final KeyPair caKeys = newKeyPair();

        private final X500Name caName = new X500Name("CN=Acme Identity CA, O=Acme Corp, C=US");

        private final X509CertificateHolder caCertificate =
                issue(caName, caKeys, BigInteger.ONE, true);

        private Organisation() throws Exception {
        }

        X500Name caName() {
            return caName;
        }

        X509CertificateHolder caCertificate() {
            return caCertificate;
        }

        X509CertificateHolder issueEmployee(String commonName, long serial) throws Exception {
            return issue(new X500Name("CN=" + commonName + ", OU=Staff, O=Acme Corp, C=US"),
                    newKeyPair(), BigInteger.valueOf(serial), false);
        }

        private X509CertificateHolder issue(X500Name subject, KeyPair subjectKeys, BigInteger serial, boolean ca)
                throws Exception {
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(caName, serial,
                    Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(365, ChronoUnit.DAYS)),
                    subject, subjectKeys.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
            return builder.build(new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(caKeys.getPrivate()));
        }
    }

    /** The attribute authority that grants roles, with its own identity certificate. */
    private final class AttributeAuthority {

        private final KeyPair keys = newKeyPair();

        private final X509CertificateHolder certificate;

        private AttributeAuthority(Organisation organisation, long serial) throws Exception {
            this.certificate = organisation.issue(
                    new X500Name("CN=Acme Attribute Authority, O=Acme Corp, C=US"),
                    keys, BigInteger.valueOf(serial), false);
        }

        X509CertificateHolder certificate() {
            return certificate;
        }

        ContentSigner signer() throws Exception {
            return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
        }

        X509AttributeCertificateHolder grant(X509CertificateHolder employee, long serial, Instant notBefore,
                Instant notAfter, List<GeneralName> services, String... roleNames) throws Exception {
            X509v2AttributeCertificateBuilder builder = new X509v2AttributeCertificateBuilder(
                    new AttributeCertificateHolder(employee),
                    new AttributeCertificateIssuer(certificate.getSubject()),
                    BigInteger.valueOf(serial), Date.from(notBefore), Date.from(notAfter));
            RoleSyntax[] roles = new RoleSyntax[roleNames.length];
            for (int i = 0; i < roleNames.length; i++) {
                roles[i] = new RoleSyntax(new GeneralNames(ROLE_AUTHORITY),
                        new GeneralName(GeneralName.uniformResourceIdentifier, roleNames[i]));
            }
            builder.addAttribute(X509AttributeIdentifiers.id_at_role, roles);
            Target[] targets = services.stream()
                    .map(service -> new Target(Target.targetName, service))
                    .toArray(Target[]::new);
            builder.addExtension(Extension.targetInformation, true, new TargetInformation(new Targets(targets)));
            return builder.build(signer());
        }
    }

    /** A service that authorizes callers from the attribute certificates it has received. */
    private static final class ServiceGateway {

        private final GeneralName self;

        private final X509CertificateHolder trustedAuthority;

        private final CollectionStore<X509AttributeCertificateHolder> store;

        ServiceGateway(GeneralName self, X509CertificateHolder trustedAuthority, List<byte[]> encoded)
                throws Exception {
            this.self = self;
            this.trustedAuthority = trustedAuthority;
            List<X509AttributeCertificateHolder> received = new ArrayList<>();
            for (byte[] bytes : encoded) {
                received.add(new X509AttributeCertificateHolder(bytes));
            }
            this.store = new CollectionStore<>(received);
        }

        @SuppressWarnings("unchecked")
        Collection<X509AttributeCertificateHolder> matching(X509CertificateHolder caller, Date at) throws Exception {
            X509AttributeCertificateHolderSelectorBuilder builder = new X509AttributeCertificateHolderSelectorBuilder();
            builder.setHolder(new AttributeCertificateHolder(caller));
            builder.setIssuer(new AttributeCertificateIssuer(trustedAuthority.getSubject()));
            builder.setAttributeCertificateValid(at);
            builder.addTargetName(self);
            X509AttributeCertificateHolderSelector selector = builder.build();
            return store.getMatches(selector);
        }

        Collection<X509AttributeCertificateHolder> matching(X509CertificateHolder caller, Instant at)
                throws Exception {
            return matching(caller, Date.from(at));
        }

        Set<String> rolesOf(X509CertificateHolder caller, Instant at) throws Exception {
            return rolesOf(caller, Date.from(at));
        }

        Set<String> rolesOf(X509CertificateHolder caller, Date at) throws Exception {
            ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder().build(trustedAuthority);
            Set<String> roles = new TreeSet<>();
            for (X509AttributeCertificateHolder certificate : matching(caller, at)) {
                if (!certificate.isSignatureValid(verifier)) {
                    continue;
                }
                for (Attribute attribute : certificate.getAttributes(X509AttributeIdentifiers.id_at_role)) {
                    for (int i = 0; i < attribute.getAttrValues().size(); i++) {
                        roles.add(RoleSyntax.getInstance(attribute.getAttrValues().getObjectAt(i))
                                .getRoleNameAsString());
                    }
                }
            }
            return roles;
        }
    }
}
