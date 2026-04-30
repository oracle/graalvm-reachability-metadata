/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

public final class UnsafeUtilAndroid32MemoryAccessorAccess {
    private UnsafeUtilAndroid32MemoryAccessorAccess() {
    }

    public static MapEntryLite<String, Value> loadMapDefaultEntry() {
        @SuppressWarnings("unchecked")
        MapEntryLite<String, Value> defaultEntry =
                (MapEntryLite<String, Value>)
                        SchemaUtil.getMapDefaultEntry(MapEntryContainer.class, "fields");
        return defaultEntry;
    }

    public static final class MapEntryContainer {
        private MapEntryContainer() {
        }

        public static final class FieldsDefaultEntryHolder {
            public static final MapEntryLite<String, Value> defaultEntry =
                    MapEntryLite.newDefaultInstance(
                            WireFormat.FieldType.STRING,
                            "",
                            WireFormat.FieldType.MESSAGE,
                            Value.getDefaultInstance());

            private FieldsDefaultEntryHolder() {
            }
        }
    }
}
