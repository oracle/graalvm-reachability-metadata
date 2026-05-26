/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core.entity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.reactive.id.ReactiveIdentifierGenerator;
import org.hibernate.reactive.session.ReactiveConnectionSupplier;

public class ReactiveOnlyIdentifierGenerator implements ReactiveIdentifierGenerator<Long> {

    private final AtomicLong nextIdentifier = new AtomicLong(1);

    public ReactiveOnlyIdentifierGenerator() {
    }

    @Override
    public CompletionStage<Long> generate(ReactiveConnectionSupplier session, Object entity) {
        return CompletableFuture.completedFuture(nextIdentifier.getAndIncrement());
    }
}
