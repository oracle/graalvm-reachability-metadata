/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

public final class CircularServices {
    private CircularServices() {
    }

    static Injector newInjector() {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Service.class).to(ServiceImpl.class).in(Singleton.class);
                bind(Peer.class).in(Singleton.class);
            }
        });
    }

    public interface Service {
        String greet(String name);
        Peer peer();
    }

    static final class ServiceImpl implements Service {
        private final Peer peer;

        @Inject
        private ServiceImpl(Peer peer) {
            this.peer = peer;
        }

        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }

        @Override
        public Peer peer() {
            return peer;
        }
    }

    public static final class Peer {
        final Service service;

        @Inject
        private Peer(Service service) {
            this.service = service;
        }
    }
}
