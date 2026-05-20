/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ReflectionAccessFilter;
import org.junit.jupiter.api.Test;

public class ReflectionAccessFilterHelperInnerAccessCheckerAnonymous1Test {
    @Test
    void blockInaccessibleFilterChecksWhetherPublicConstructorCanAlreadyBeAccessed() {
        Gson gson = new GsonBuilder()
                .addReflectionAccessFilter(type -> type == PublicConstructorType.class
                        ? ReflectionAccessFilter.FilterResult.BLOCK_INACCESSIBLE
                        : ReflectionAccessFilter.FilterResult.INDECISIVE)
                .create();

        PublicConstructorType actual = gson.fromJson("{}", PublicConstructorType.class);

        assertThat(actual.wasConstructed).isTrue();
    }

    public static final class PublicConstructorType {
        final boolean wasConstructed;

        public PublicConstructorType() {
            wasConstructed = true;
        }
    }
}
