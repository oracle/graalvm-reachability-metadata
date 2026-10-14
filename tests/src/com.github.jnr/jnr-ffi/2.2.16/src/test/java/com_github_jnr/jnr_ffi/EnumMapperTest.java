/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_ffi;

import jnr.ffi.util.EnumMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumMapperTest {
    public enum Status {
        READY(12),
        DONE(24);

        private final int nativeValue;

        Status(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int intValue() {
            return nativeValue;
        }
    }

    @Test
    void mapsEnumsUsingTheirNumericMethod() {
        EnumMapper mapper = EnumMapper.getInstance(Status.class);

        assertThat(mapper.intValue(Status.READY)).isEqualTo(12);
        assertThat(mapper.valueOf(24)).isEqualTo(Status.DONE);
    }
}
