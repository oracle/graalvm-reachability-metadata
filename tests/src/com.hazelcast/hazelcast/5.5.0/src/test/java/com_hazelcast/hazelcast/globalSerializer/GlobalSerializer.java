/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast.globalSerializer;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

@SuppressWarnings("DataFlowIssue")
public class GlobalSerializer implements StreamSerializer<Object> {
    @Override
    public int getTypeId() {
        return 20;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void write(ObjectDataOutput out, Object object) {
    }

    @Override
    public Object read(ObjectDataInput in) {
        return null;
    }
}
