/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.rocksdb.MutableDBOptions;
import org.rocksdb.MutableDBOptionsInterface;

import static org.assertj.core.api.Assertions.assertThat;

public class MutableDBOptionsBuilderApiTest {

    @Test
    void builderRoundTripsMutableDatabaseSettings() {
        MutableDBOptions.MutableDBOptionsBuilder builder = MutableDBOptions.builder();
        MutableDBOptionsInterface<MutableDBOptions.MutableDBOptionsBuilder> options = builder;

        assertThat(options.setAvoidFlushDuringShutdown(true)).isSameAs(builder);
        assertThat(options.setBytesPerSync(201L)).isSameAs(builder);
        assertThat(options.setCompactionReadaheadSize(202L)).isSameAs(builder);
        assertThat(options.setDailyOffpeakTimeUTC("05:00-06:00")).isSameAs(builder);
        assertThat(options.setDelayedWriteRate(203L)).isSameAs(builder);
        assertThat(options.setDeleteObsoleteFilesPeriodMicros(204L)).isSameAs(builder);
        assertThat(options.setMaxBackgroundCompactions(3)).isSameAs(builder);
        assertThat(options.setMaxBackgroundJobs(4)).isSameAs(builder);
        assertThat(options.setMaxOpenFiles(205)).isSameAs(builder);
        assertThat(options.setMaxTotalWalSize(206L)).isSameAs(builder);
        assertThat(options.setStatsDumpPeriodSec(7)).isSameAs(builder);
        assertThat(options.setStatsHistoryBufferSize(207L)).isSameAs(builder);
        assertThat(options.setStatsPersistPeriodSec(8)).isSameAs(builder);
        assertThat(options.setStrictBytesPerSync(true)).isSameAs(builder);
        assertThat(options.setWalBytesPerSync(208L)).isSameAs(builder);
        assertThat(options.setWritableFileMaxBufferSize(209L)).isSameAs(builder);

        assertThat(builder.avoidFlushDuringShutdown()).isTrue();
        assertThat(builder.bytesPerSync()).isEqualTo(201L);
        assertThat(builder.compactionReadaheadSize()).isEqualTo(202L);
        assertThat(builder.dailyOffpeakTimeUTC()).isEqualTo("05:00-06:00");
        assertThat(builder.delayedWriteRate()).isEqualTo(203L);
        assertThat(builder.deleteObsoleteFilesPeriodMicros()).isEqualTo(204L);
        assertThat(builder.maxBackgroundCompactions()).isEqualTo(3);
        assertThat(builder.maxBackgroundJobs()).isEqualTo(4);
        assertThat(builder.maxOpenFiles()).isEqualTo(205);
        assertThat(builder.maxTotalWalSize()).isEqualTo(206L);
        assertThat(builder.statsDumpPeriodSec()).isEqualTo(7);
        assertThat(builder.statsHistoryBufferSize()).isEqualTo(207L);
        assertThat(builder.statsPersistPeriodSec()).isEqualTo(8);
        assertThat(builder.strictBytesPerSync()).isTrue();
        assertThat(builder.walBytesPerSync()).isEqualTo(208L);
        assertThat(builder.writableFileMaxBufferSize()).isEqualTo(209L);

        MutableDBOptions built = builder.build();
        assertThat(built).isNotNull();
    }
}
