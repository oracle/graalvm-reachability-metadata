/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.IAppIdentityServiceFactory;
import com.google.appengine.spi.FactoryProvider;

public final class AppIdentityServiceFactoryProvider
        extends FactoryProvider<IAppIdentityServiceFactory> {
    private final IAppIdentityServiceFactory factory = new TestAppIdentityServiceFactory();

    public AppIdentityServiceFactoryProvider() {
        super(IAppIdentityServiceFactory.class);
    }

    @Override
    protected IAppIdentityServiceFactory getFactoryInstance() {
        return factory;
    }

    private static final class TestAppIdentityServiceFactory implements IAppIdentityServiceFactory {
        private final AppIdentityService service = new TestAppIdentityService();

        @Override
        public AppIdentityService getAppIdentityService() {
            return service;
        }
    }
}
