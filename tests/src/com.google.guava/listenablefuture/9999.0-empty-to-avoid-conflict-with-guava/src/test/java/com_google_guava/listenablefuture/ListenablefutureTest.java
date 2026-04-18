/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.listenablefuture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListenablefutureTest {

    @Test
    void placeholderArtifactDoesNotPublishListenableFutureTypes() {
        assertThat(classResource("com/google/common/util/concurrent/ListenableFuture.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/AbstractFuture.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/Futures.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/SettableFuture.class")).isNull();
    }

    @Test
    void placeholderArtifactDoesNotPublishCoreGuavaTypes() {
        assertThat(classResource("com/google/common/base/Optional.class")).isNull();
        assertThat(classResource("com/google/common/collect/ImmutableList.class")).isNull();
        assertThat(classResource("com/google/common/hash/Hashing.class")).isNull();
        assertThat(classResource("com/google/common/io/Files.class")).isNull();
    }

    @Test
    void placeholderArtifactDoesNotDefineCommonGuavaPackages() {
        assertThat(classResource("com/google/common/base/package-info.class")).isNull();
        assertThat(classResource("com/google/common/collect/package-info.class")).isNull();
        assertThat(classResource("com/google/common/hash/package-info.class")).isNull();
        assertThat(classResource("com/google/common/util/concurrent/package-info.class")).isNull();
    }

    private java.net.URL classResource(final String resourcePath) {
        return getClass().getClassLoader().getResource(resourcePath);
    }
}
