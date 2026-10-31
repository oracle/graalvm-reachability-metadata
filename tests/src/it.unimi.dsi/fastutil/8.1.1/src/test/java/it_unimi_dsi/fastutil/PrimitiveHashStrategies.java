/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.bytes.ByteHash;
import it.unimi.dsi.fastutil.chars.CharHash;
import it.unimi.dsi.fastutil.doubles.DoubleHash;
import it.unimi.dsi.fastutil.floats.FloatHash;
import it.unimi.dsi.fastutil.ints.IntHash;
import it.unimi.dsi.fastutil.longs.LongHash;
import it.unimi.dsi.fastutil.shorts.ShortHash;

final class PrimitiveHashStrategies {
    private PrimitiveHashStrategies() {
    }

    enum ByteStrategy implements ByteHash.Strategy {
        INSTANCE;

        @Override
        public int hashCode(byte key) {
            return 1;
        }

        @Override
        public boolean equals(byte first, byte second) {
            return first == second;
        }
    }

    enum CharStrategy implements CharHash.Strategy {
        INSTANCE;

        @Override
        public int hashCode(char key) {
            return 1;
        }

        @Override
        public boolean equals(char first, char second) {
            return first == second;
        }
    }

    enum DoubleStrategy implements DoubleHash.Strategy {
        INSTANCE;

        @Override
        public int hashCode(double key) {
            return 1;
        }

        @Override
        public boolean equals(double first, double second) {
            return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
        }
    }

    enum FloatStrategy implements FloatHash.Strategy {
        INSTANCE;

        @Override
        public int hashCode(float key) {
            return 1;
        }

        @Override
        public boolean equals(float first, float second) {
            return Float.floatToIntBits(first) == Float.floatToIntBits(second);
        }
    }

    enum IntStrategy implements IntHash.Strategy {
        INSTANCE;

        @Override
        public int hashCode(int key) {
            return 1;
        }

        @Override
        public boolean equals(int first, int second) {
            return first == second;
        }
    }

    enum LongStrategy implements LongHash.Strategy {
        INSTANCE;

        @Override
        public int hashCode(long key) {
            return 1;
        }

        @Override
        public boolean equals(long first, long second) {
            return first == second;
        }
    }

    enum ShortStrategy implements ShortHash.Strategy {
        INSTANCE;

        @Override
        public int hashCode(short key) {
            return 1;
        }

        @Override
        public boolean equals(short first, short second) {
            return first == second;
        }
    }
}
