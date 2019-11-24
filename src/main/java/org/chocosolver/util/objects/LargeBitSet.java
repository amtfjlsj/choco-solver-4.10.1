package org.chocosolver.util.objects;

public class LargeBitSet extends NaiveBitSet {
    // 记录非0索引
    int[] nonClearIndex;
    int limit = 0;
    int indexIterator;
    int iterator;

    public LargeBitSet(int nbits) {
        super(nbits);
        nonClearIndex = new int[longSize];
    }

    @Override
    public void clear() {
        for (int i = 0; i < limit; ++i) {
            this.words[nonClearIndex[i]] = 0L;
        }
    }

    //    public int nextSetBit(int index, int fromIndex) {
//        if (fromIndex < 0) {
//            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
//        } else {
//            if (fromIndex >= this.bitSize || index == this.limit) {
//                return -1;
//            } else {
//                long word;
//                for (word = this.words[nonClearIndex[index]] & -1L << fromIndex; word == 0L; word = this.words[nonClearIndex[index]]) {
//                    ++index;
//                    if (index == this.longSize) {
//                        return -1;
//                    }
//                }
//
//                return nonClearIndex[index] * 64 + Long.numberOfTrailingZeros(word);
//            }
//        }
//    }
    public void iterator() {
        indexIterator = 0;
        iterator = -1;
    }

//    public boolean hasNext() {
//        if ((this.words[nonClearIndex[indexIterator]] & -1L << iterator + 1) == 0L && indexIterator + 1 == limit) {
//            return false;
//        } else {
//            return true;
//        }
//    }

    public int next() {
        ++iterator;
        long word;
        for (word = this.words[nonClearIndex[indexIterator]] & -1L << iterator; word == 0L; word = this.words[nonClearIndex[indexIterator]]) {
            ++indexIterator;
            if (indexIterator == limit) {
                return -1;
            }
        }
        iterator = nonClearIndex[indexIterator] * 64 + Long.numberOfTrailingZeros(word);
        return iterator;
    }
}
