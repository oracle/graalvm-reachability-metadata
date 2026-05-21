/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_waffle.waffle_jna;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import waffle.jaas.RolePrincipal;
import waffle.jaas.UserPrincipal;
import waffle.jaas.WindowsLoginModule;
import waffle.servlet.WindowsPrincipal;
import waffle.util.NtlmMessage;
import waffle.util.SPNegoMessage;
import waffle.util.WaffleInfo;
import waffle.util.cache.Cache;
import waffle.util.cache.CacheSupplier;
import waffle.util.cache.CaffeineCache;
import waffle.util.cache.CaffeineCacheSupplier;
import waffle.windows.auth.IWindowsAccount;
import waffle.windows.auth.IWindowsAuthProvider;
import waffle.windows.auth.IWindowsComputer;
import waffle.windows.auth.IWindowsDomain;
import waffle.windows.auth.IWindowsIdentity;
import waffle.windows.auth.IWindowsImpersonationContext;
import waffle.windows.auth.IWindowsSecurityContext;
import waffle.windows.auth.PrincipalFormat;
import waffle.windows.auth.WindowsAccount;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;
import waffle.windows.auth.impl.WindowsSecurityContextImpl;

import java.util.Arrays;
import java.util.Map;
import java.util.ServiceLoader;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.parsers.DocumentBuilderFactory;

public class Waffle_jnaTest {
    @Test
    void ntlmAndSpnegoMessagesAreRecognizedFromWireBytes() {
        byte[] ntlmTypeOne = new byte[] {'N', 'T', 'L', 'M', 'S', 'S', 'P', 0, 1, 0, 0, 0 };
        byte[] nonNtlm = new byte[] {'N', 'T', 'L', 'M', 'x' };
        byte[] spnegoNegTokenInit = new byte[] {0x60, 0x08, 0x06, 0x06, 0x2b, 0x06, 0x01, 0x05, 0x05, 0x02 };
        byte[] spnegoNegTokenArg = new byte[] {(byte) 0xa1, 0x03, 0x01, 0x02, 0x03 };

        assertThat(NtlmMessage.isNtlmMessage(ntlmTypeOne)).isTrue();
        assertThat(NtlmMessage.getMessageType(ntlmTypeOne)).isEqualTo(1);
        assertThat(NtlmMessage.isNtlmMessage(nonNtlm)).isFalse();
        assertThat(NtlmMessage.isNtlmMessage(null)).isFalse();

        assertThat(SPNegoMessage.isNegTokenInit(spnegoNegTokenInit)).isTrue();
        assertThat(SPNegoMessage.isNegTokenInit(new byte[] {0x60, 0x01 })).isFalse();
        assertThat(SPNegoMessage.isNegTokenArg(spnegoNegTokenArg)).isTrue();
        assertThat(SPNegoMessage.isNegTokenArg(new byte[] {(byte) 0xa1, 0x04, 0x01 })).isFalse();
    }

    @Test
    void principalsAndAccountSnapshotsUseStableNamesAndIdentifiers() {
        UserPrincipal user = new UserPrincipal("DOMAIN\\alice");
        RolePrincipal role = new RolePrincipal("DOMAIN\\Admins");
        WindowsAccount admins = new WindowsAccount(new TestWindowsAccount("S-1-5-32-544", "DOMAIN\\Admins", "Admins", "DOMAIN"));
        WindowsAccount duplicateAdmins = new WindowsAccount(
                new TestWindowsAccount("S-1-5-32-544", "OTHER\\Administrators", "Administrators", "OTHER"));
        WindowsAccount users = new WindowsAccount(new TestWindowsAccount("S-1-5-32-545", "DOMAIN\\Users", "Users", "DOMAIN"));

        assertThat(user.getName()).isEqualTo("DOMAIN\\alice");
        assertThat(user).isEqualTo(new UserPrincipal("DOMAIN\\alice"));
        assertThat(user).isNotEqualTo(new UserPrincipal("DOMAIN\\bob"));

        assertThat(role.getName()).isEqualTo("DOMAIN\\Admins");
        assertThat(role).isEqualTo(new RolePrincipal("DOMAIN\\Admins"));
        assertThat(role).isNotEqualTo(new RolePrincipal("DOMAIN\\Users"));

        assertThat(admins.getSidString()).isEqualTo("S-1-5-32-544");
        assertThat(admins.getFqn()).isEqualTo("DOMAIN\\Admins");
        assertThat(admins.getName()).isEqualTo("Admins");
        assertThat(admins.getDomain()).isEqualTo("DOMAIN");
        assertThat(admins).isEqualTo(duplicateAdmins);
        assertThat(admins).isNotEqualTo(users);
    }

    @Test
    void windowsPrincipalBuildsRolesForRequestedPrincipalAndGroupFormats() {
        TestWindowsAccount admins = new TestWindowsAccount("S-1-5-32-544", "DOMAIN\\Admins", "Admins", "DOMAIN");
        TestWindowsAccount users = new TestWindowsAccount("S-1-5-32-545", "DOMAIN\\Users", "Users", "DOMAIN");
        TestWindowsIdentity identity = new TestWindowsIdentity("S-1-5-21-1000", new byte[] {1, 2, 3, 4 },
                "DOMAIN\\alice", admins, users);

        WindowsPrincipal principal = new WindowsPrincipal(identity, PrincipalFormat.BOTH, PrincipalFormat.SID);

        assertThat(principal.getName()).isEqualTo("DOMAIN\\alice");
        assertThat(principal.toString()).isEqualTo("DOMAIN\\alice");
        assertThat(principal.getSidString()).isEqualTo("S-1-5-21-1000");
        assertThat(principal.getSid()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        assertThat(principal.getSid()).isNotSameAs(identity.getSid());
        assertThat(principal.getIdentity()).isSameAs(identity);
        assertThat(principal.getGroups()).containsOnlyKeys("DOMAIN\\Admins", "DOMAIN\\Users");
        assertThat(principal.hasRole("DOMAIN\\alice")).isTrue();
        assertThat(principal.hasRole("S-1-5-21-1000")).isTrue();
        assertThat(principal.hasRole("S-1-5-32-544")).isTrue();
        assertThat(principal.hasRole("DOMAIN\\Admins")).isFalse();
        assertThat(principal.getRolesString()).contains("DOMAIN\\alice", "S-1-5-21-1000", "S-1-5-32-544");
        assertThat(principal).isEqualTo(new WindowsPrincipal(identity));
    }

    @Test
    void windowsSecurityContextStoresNegotiationStateAndDefensivelyCopiesToken() {
        WindowsSecurityContextImpl context = new WindowsSecurityContextImpl();
        byte[] token = new byte[] {1, 2, 3, 4 };

        context.setPrincipalName("DOMAIN\\alice");
        context.setSecurityPackage("Negotiate");
        context.setToken(token);
        context.setContinue(true);
        token[0] = 9;

        assertThat(context.getPrincipalName()).isEqualTo("DOMAIN\\alice");
        assertThat(context.getSecurityPackage()).isEqualTo("Negotiate");
        assertThat(context.isContinue()).isTrue();
        assertThat(context.getToken()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);

        byte[] returnedToken = context.getToken();
        returnedToken[1] = 9;
        assertThat(context.getToken()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    @Test
    void cacheImplementationsStoreRemoveAndLoadThroughServiceProvider() {
        Cache<String, String> directCache = new CaffeineCache<>(60);
        directCache.put("connection", "token");
        assertThat(directCache.get("connection")).isEqualTo("token");
        assertThat(directCache.size()).isEqualTo(1);
        directCache.remove("connection");
        assertThat(directCache.get("connection")).isNull();
        assertThat(directCache.size()).isZero();

        Cache<String, Integer> suppliedCache = new CaffeineCacheSupplier().newCache(60);
        suppliedCache.put("step", 1);
        assertThat(suppliedCache.get("step")).isEqualTo(1);

        ServiceLoader<CacheSupplier> cacheSuppliers = ServiceLoader.load(CacheSupplier.class);
        assertThat(cacheSuppliers).anySatisfy(supplier -> assertThat(supplier).isInstanceOf(CaffeineCacheSupplier.class));

        Cache<String, String> serviceLoadedCache = Cache.newCache(60);
        serviceLoadedCache.put("service", "loaded");
        assertThat(serviceLoadedCache.get("service")).isEqualTo("loaded");
    }

    @Test
    void windowsAuthProviderStartsWithAnEmptyBoundedContinueContextCache() {
        WindowsAuthProviderImpl authProvider = new WindowsAuthProviderImpl(60);

        authProvider.resetSecurityToken("missing-connection");
        assertThat(authProvider.getContinueContextsSize()).isZero();
    }

    @Test
    void windowsLoginModuleAuthenticatesWithCallbackCredentialsAndPopulatesSubjectPrincipals() throws Exception {
        TrackingWindowsIdentity identity = new TrackingWindowsIdentity("S-1-5-21-1000", new byte[] {1, 2, 3, 4 },
                "DOMAIN\\alice",
                new TestWindowsAccount("S-1-5-32-544", "DOMAIN\\Admins", "Admins", "DOMAIN"),
                new TestWindowsAccount("S-1-5-32-545", "DOMAIN\\Users", "Users", "DOMAIN"));
        TestWindowsAuthProvider authProvider = new TestWindowsAuthProvider(identity);
        Subject subject = new Subject();
        WindowsLoginModule loginModule = new WindowsLoginModule();
        CallbackHandler callbackHandler = callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName("DOMAIN\\alice");
                } else if (callback instanceof PasswordCallback passwordCallback) {
                    passwordCallback.setPassword("secret".toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };

        loginModule.setAuth(authProvider);
        loginModule.setAllowGuestLogin(false);
        loginModule.initialize(subject, callbackHandler, Map.of(), Map.of("debug", "true", "principalFormat", "both",
                "roleFormat", "sid"));

        assertThat(loginModule.isDebug()).isTrue();
        assertThat(loginModule.isAllowGuestLogin()).isFalse();
        assertThat(loginModule.getAuth()).isSameAs(authProvider);
        assertThat(loginModule.login()).isTrue();
        assertThat(authProvider.getLastUsername()).isEqualTo("DOMAIN\\alice");
        assertThat(authProvider.getLastPassword()).isEqualTo("secret");
        assertThat(identity.isDisposed()).isTrue();

        assertThat(loginModule.commit()).isTrue();
        assertThat(subject.getPrincipals()).contains(new UserPrincipal("DOMAIN\\alice"),
                new UserPrincipal("S-1-5-21-1000"), new RolePrincipal("S-1-5-32-544"),
                new RolePrincipal("S-1-5-32-545"));
        assertThat(subject.getPrincipals()).doesNotContain(new RolePrincipal("DOMAIN\\Admins"));

        assertThat(loginModule.logout()).isTrue();
        assertThat(subject.getPrincipals()).isEmpty();
    }

    @Test
    void waffleInfoFormatsExceptionDetailsAsXml() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        IllegalArgumentException failure = new IllegalArgumentException("bad account");

        Element exception = WaffleInfo.getException(document, failure);
        document.appendChild(exception);
        String xml = WaffleInfo.toPrettyXML(document);

        assertThat(exception.getAttribute("class")).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(xml).contains("<exception class=\"java.lang.IllegalArgumentException\">");
        assertThat(xml).contains("<message>bad account</message>");
        assertThat(xml).contains("<trace>");
    }

    private static final class TestWindowsAccount implements IWindowsAccount {
        private final String sidString;
        private final String fqn;
        private final String name;
        private final String domain;

        private TestWindowsAccount(String sidString, String fqn, String name, String domain) {
            this.sidString = sidString;
            this.fqn = fqn;
            this.name = name;
            this.domain = domain;
        }

        @Override
        public String getSidString() {
            return sidString;
        }

        @Override
        public String getFqn() {
            return fqn;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDomain() {
            return domain;
        }
    }

    private static final class TestWindowsAuthProvider implements IWindowsAuthProvider {
        private final IWindowsIdentity identity;
        private String lastUsername;
        private String lastPassword;

        private TestWindowsAuthProvider(IWindowsIdentity identity) {
            this.identity = identity;
        }

        @Override
        public IWindowsIdentity logonUser(String username, String password) {
            this.lastUsername = username;
            this.lastPassword = password;
            return identity;
        }

        @Override
        public IWindowsIdentity logonDomainUser(String username, String domain, String password) {
            throw new UnsupportedOperationException("Domain logon is not used by this test.");
        }

        @Override
        public IWindowsIdentity logonDomainUserEx(String username, String domain, String password, int logonType,
                int logonProvider) {
            throw new UnsupportedOperationException("Extended domain logon is not used by this test.");
        }

        @Override
        public IWindowsAccount lookupAccount(String accountName) {
            throw new UnsupportedOperationException("Account lookup is not used by this test.");
        }

        @Override
        public IWindowsComputer getCurrentComputer() {
            throw new UnsupportedOperationException("Computer lookup is not used by this test.");
        }

        @Override
        public IWindowsDomain[] getDomains() {
            throw new UnsupportedOperationException("Domain enumeration is not used by this test.");
        }

        @Override
        public IWindowsSecurityContext acceptSecurityToken(String connectionId, byte[] token, String securityPackage) {
            throw new UnsupportedOperationException("Security token acceptance is not used by this test.");
        }

        @Override
        public void resetSecurityToken(String connectionId) {
            throw new UnsupportedOperationException("Security token reset is not used by this test.");
        }

        private String getLastUsername() {
            return lastUsername;
        }

        private String getLastPassword() {
            return lastPassword;
        }
    }

    private static final class TrackingWindowsIdentity implements IWindowsIdentity {
        private final String sidString;
        private final byte[] sid;
        private final String fqn;
        private final IWindowsAccount[] groups;
        private boolean disposed;

        private TrackingWindowsIdentity(String sidString, byte[] sid, String fqn, IWindowsAccount... groups) {
            this.sidString = sidString;
            this.sid = Arrays.copyOf(sid, sid.length);
            this.fqn = fqn;
            this.groups = Arrays.copyOf(groups, groups.length);
        }

        @Override
        public String getSidString() {
            return sidString;
        }

        @Override
        public byte[] getSid() {
            return Arrays.copyOf(sid, sid.length);
        }

        @Override
        public String getFqn() {
            return fqn;
        }

        @Override
        public IWindowsAccount[] getGroups() {
            return Arrays.copyOf(groups, groups.length);
        }

        @Override
        public IWindowsImpersonationContext impersonate() {
            return () -> {
                // Test identity does not hold a native impersonation context.
            };
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isGuest() {
            return false;
        }

        private boolean isDisposed() {
            return disposed;
        }
    }

    private static final class TestWindowsIdentity implements IWindowsIdentity {
        private final String sidString;
        private final byte[] sid;
        private final String fqn;
        private final IWindowsAccount[] groups;

        private TestWindowsIdentity(String sidString, byte[] sid, String fqn, IWindowsAccount... groups) {
            this.sidString = sidString;
            this.sid = Arrays.copyOf(sid, sid.length);
            this.fqn = fqn;
            this.groups = Arrays.copyOf(groups, groups.length);
        }

        @Override
        public String getSidString() {
            return sidString;
        }

        @Override
        public byte[] getSid() {
            return Arrays.copyOf(sid, sid.length);
        }

        @Override
        public String getFqn() {
            return fqn;
        }

        @Override
        public IWindowsAccount[] getGroups() {
            return Arrays.copyOf(groups, groups.length);
        }

        @Override
        public IWindowsImpersonationContext impersonate() {
            return () -> {
                // Test identity does not hold a native impersonation context.
            };
        }

        @Override
        public void dispose() {
            // Test identity does not hold native resources.
        }

        @Override
        public boolean isGuest() {
            return false;
        }
    }
}
