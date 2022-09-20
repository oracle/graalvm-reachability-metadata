/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ecwid.consul;

import com.ecwid.consul.v1.ConsistencyMode;
import com.ecwid.consul.v1.QueryParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Test copied over from https://github.com/Ecwid/consul-api/blob/master/src/test/java/com/ecwid/consul/v1/QueryParamsTest.java and refactored
class QueryParamsTest {

	@Test
	public void shouldReturnQueryParamsWithCorrectValuesApplied() {
		String expectedDatacenter = "testDC";
		ConsistencyMode expectedMode = ConsistencyMode.CONSISTENT;
		long expectedIndex = 100;
		long expectedWaitTime = 10000;
		String expectedNear = "_agent";

		QueryParams actual = QueryParams.Builder.builder()
				.setDatacenter(expectedDatacenter)
				.setConsistencyMode(expectedMode)
				.setWaitTime(expectedWaitTime)
				.setIndex(expectedIndex)
				.setNear(expectedNear)
				.build();

		assertThat(actual.getDatacenter()).isEqualTo(expectedDatacenter);
		assertThat(actual.getConsistencyMode()).isEqualTo(expectedMode);
		assertThat(actual.getIndex()).isEqualTo(expectedIndex);
		assertThat(actual.getWaitTime()).isEqualTo(expectedWaitTime);
		assertThat(actual.getNear()).isEqualTo(expectedNear);
	}
}

