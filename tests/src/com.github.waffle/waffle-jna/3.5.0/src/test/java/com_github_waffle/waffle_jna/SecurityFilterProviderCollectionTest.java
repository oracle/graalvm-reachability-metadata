/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_waffle.waffle_jna;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import waffle.servlet.spi.BasicSecurityFilterProvider;
import waffle.servlet.spi.NegotiateSecurityFilterProvider;
import waffle.servlet.spi.SecurityFilterProviderCollection;
import waffle.windows.auth.IWindowsAccount;
import waffle.windows.auth.IWindowsAuthProvider;
import waffle.windows.auth.IWindowsComputer;
import waffle.windows.auth.IWindowsDomain;
import waffle.windows.auth.IWindowsIdentity;
import waffle.windows.auth.IWindowsSecurityContext;

public class SecurityFilterProviderCollectionTest {
    @Test
    void loadsConfiguredProvidersByClassName() throws ClassNotFoundException {
        final String[] providerNames = {
                " " + BasicSecurityFilterProvider.class.getName() + " ",
                NegotiateSecurityFilterProvider.class.getName()
        };

        final SecurityFilterProviderCollection providers = new SecurityFilterProviderCollection(providerNames,
                new UnsupportedWindowsAuthProvider());

        assertThat(providers.size()).isEqualTo(2);
        assertThat(providers.getByClassName(BasicSecurityFilterProvider.class.getName()))
                .isInstanceOf(BasicSecurityFilterProvider.class);
        assertThat(providers.getByClassName(NegotiateSecurityFilterProvider.class.getName()))
                .isInstanceOf(NegotiateSecurityFilterProvider.class);
        assertThat(providers.isSecurityPackageSupported("Basic")).isTrue();
        assertThat(providers.isSecurityPackageSupported("Negotiate")).isTrue();
    }

    private static final class UnsupportedWindowsAuthProvider implements IWindowsAuthProvider {
        @Override
        public IWindowsIdentity logonUser(final String username, final String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IWindowsIdentity logonDomainUser(final String username, final String domain, final String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IWindowsIdentity logonDomainUserEx(final String username, final String domain, final String password,
                final int logonType, final int logonProvider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IWindowsAccount lookupAccount(final String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IWindowsComputer getCurrentComputer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IWindowsDomain[] getDomains() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IWindowsSecurityContext acceptSecurityToken(final String connectionId, final byte[] token,
                final String securityPackage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetSecurityToken(final String connectionId) {
            throw new UnsupportedOperationException();
        }
    }
}
