/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.entity;

import java.io.Serializable;

public record TOrderPOJO(
        long id,
        String location,
        TableStatus tableStatus

) implements Serializable {
}
