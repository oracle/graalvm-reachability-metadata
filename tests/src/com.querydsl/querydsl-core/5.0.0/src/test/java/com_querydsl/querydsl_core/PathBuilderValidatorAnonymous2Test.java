/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.PathType;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.PathBuilderValidator;

import org.junit.jupiter.api.Test;

public class PathBuilderValidatorAnonymous2Test {

    @Test
    void getUsesFieldsValidatorToResolveDeclaredFieldType() {
        PathBuilder<CustomerAccount> account = new PathBuilder<CustomerAccount>(
                CustomerAccount.class,
                "account",
                PathBuilderValidator.FIELDS);

        PathBuilder<Object> displayName = account.get("displayName");

        assertThat(displayName.getType()).isEqualTo(String.class);
        assertThat(displayName.getMetadata().getParent()).isSameAs(account);
        assertThat(displayName.getMetadata().getName()).isEqualTo("displayName");
        assertThat(displayName.getMetadata().getPathType()).isEqualTo(PathType.PROPERTY);
    }

    public static final class CustomerAccount {

        private String displayName;
    }
}
