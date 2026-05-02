/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.requestfactory.vm.impl.Deobfuscator;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class ClassComparatorTest {
    private static final String DOMAIN_TYPE = Object.class.getName();

    @Test
    void mergedClientMappingsPreferMostSpecificAssignableClass() {
        Deobfuscator base = new Deobfuscator.Builder()
                .withClientToDomainMappings(
                        DOMAIN_TYPE,
                        Collections.singletonList(Number.class.getName()))
                .build();

        Deobfuscator merged = new Deobfuscator.Builder()
                .withClientToDomainMappings(
                        DOMAIN_TYPE,
                        Collections.singletonList(Integer.class.getName()))
                .merge(base)
                .build();

        List<String> clientProxies = merged.getClientProxies(DOMAIN_TYPE);

        assertThat(clientProxies).containsExactly(Integer.class.getName(), Number.class.getName());
    }
}
