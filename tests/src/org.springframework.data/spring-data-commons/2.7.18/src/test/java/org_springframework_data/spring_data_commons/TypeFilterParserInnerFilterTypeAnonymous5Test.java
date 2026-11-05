/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class TypeFilterParserInnerFilterTypeAnonymous5Test {

    @Test
    void customFilterIsLoadedAndAppliesItsMatchingRule() throws Exception {
        Collection<TypeFilter> filters = TypeFilterTestSupport.parse("custom", CandidateFilter.class);
        CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory();

        assertThat(filters).hasSize(1);
        TypeFilter filter = filters.iterator().next();
        assertThat(filter).isInstanceOf(CandidateFilter.class);
        assertThat(filter.match(readers.getMetadataReader(PageRequest.class.getName()), readers)).isTrue();
        assertThat(filter.match(readers.getMetadataReader(Sort.class.getName()), readers)).isFalse();
    }

    public static class CandidateFilter implements TypeFilter {

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory readers) {
            return reader.getClassMetadata().getClassName().endsWith("PageRequest");
        }
    }

}
