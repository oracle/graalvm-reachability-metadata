/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_format_structures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.parquet.format.NullType;
import org.junit.jupiter.api.Test;

import shaded.parquet.org.apache.thrift.TFieldIdEnum;
import shaded.parquet.org.apache.thrift.meta_data.FieldMetaData;

public class FieldMetaDataTest {
    @Test
    void loadsThriftStructMetadataForClassOnDemand() {
        Map<? extends TFieldIdEnum, FieldMetaData> metadata = FieldMetaData.getStructMetaDataMap(NullType.class);

        assertThat(metadata)
                .isSameAs(NullType.metaDataMap)
                .isEmpty();
    }
}
