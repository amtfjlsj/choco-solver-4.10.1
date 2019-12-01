//package org.chocosolver.util.objects;
//
//import gnu.trove.list.array.TIntArrayList;
//
//import java.util.BitSet;
//
///**
// * Implementation based on "2013_TRICS_Sparse-Sets for Domain Implementation".
// * <p/>
// * <p/>
// * Created by Jia'nan Chen on 13/11/2019.
// * Project: choco.
// */
//
//
//// 分别为bind区|有效值区|无效值区
//public class NaiveSparseSet {
//    private int length;
//    private int[] sparse;
//    private int[] dense;
//    //limit 是不包括本值之前的值有效
//    private TIntArrayList limit;
//    private int lastLimit;
//    // 用于遍历有效部分或无效部分
////    private int iterator;
//    // 用于记住原limit
////    private int oldLimit;
////    private int iterator2;
//    private static int numMarks = 6;
//    private int lastLevel;
//
//    private int[] mark;
//    private int bind;
//    private int lastPos;
//
//    public NaiveSparseSet(int length) {
//        this.length = length;
//        this.sparse = new int[length];
//        this.dense = new int[length];
//        for (int i = 0; i < length; i++) {
//            this.sparse[i] = i;
//            this.dense[i] = i;
//        }
//
//        //从低层在左向右逐渐升高
//        this.limit = new TIntArrayList(length, 0);
//        this.mark = new int[numMarks];
//    }
//
//    public void reserve(int length) {
//        this.length = length;
//        this.sparse = null;
//        this.dense = null;
//        this.sparse = new int[length];
//        this.dense = new int[length];
//        for (int i = 0; i < length; i++) {
//            this.sparse[i] = i;
//            this.dense[i] = i;
//        }
//        this.limit.resetQuick();
//    }
//
//    public int newLevel() {
//        limit.add(limit.get(lastPos));
//        lastPos = limit.size();
//        return lastPos;
//    }
//
//    public int deleteLevel() {
//        limit.removeAt(lastPos);
//        lastPos = limit.size();
//        return lastPos;
//    }
//
////    public void fill() {
////        limit. = length - 1;
////    }
////
////    public void clear() {
////        limit = -1;
////    }
//
//    public boolean contain(int e, int level) {
//        return sparse[e] < limit.get(level);
//    }
//
//    private void remove(int e) {
//        if (contain(e, lastPos)) {
//            swap(dense[e], limit.get(lastPos) - 1);
//            limit--;
//        }
//    }
//
//    public void add(int e) {
//        if (!contain(e, lastPos)) {
//            swap(dense[e], limit);
//            limit++;
//        }
//    }
//
//    private void swap(int i, int j) {
//        int tmp = dense[i];
//        dense[i] = dense[j];
//        dense[j] = tmp;
//        sparse[dense[i]] = i;
//        sparse[dense[j]] = j;
//    }
//
//    // 遍历有效部分（左部集合）
//    public void iterateValid() {
//        iterator = -1;
//    }
//
//    // 从某个值起，遍历有效部分（左部集合）
//    public void iterateValid(int e) {
//        iterator = sparse[e];
//    }
//
//    public boolean hasNextValid() {
//        return iterator + 1 <= limit;
//    }
//
//    // 遍历无效部分（右部集合）
//    public void iterateInvalid() {
//        iterator = limit;
//    }
//
//    public boolean hasNextInvalid() {
//        return iterator + 1 < length;
//    }
//
//    public int next() {
//        return dense[++iterator];
//    }
//
//    public int current() {
//        return dense[iterator];
//    }
//
//    // 只能用于在迭代过程中删除上一个迭代元素
//    public void remove() {
//        if (iterator == -1) {
//            return;
//        }
//        int e1 = dense[iterator];
//        int e2 = dense[limit];
//        dense[iterator] = e2;
//        dense[limit] = e1;
//        sparse[e1] = limit;
//        sparse[e2] = iterator;
//        limit--;
//        iterator--;
//    }
//
//
//    public void record() {
//        oldLimit = limit;
//    }
//
//    public void restore() {
//        limit = oldLimit;
//    }
//
//
//    // 遍历limit到oldLimit之间的集合
//    public void iterateLimit() {
//        iterator2 = limit;
//    }
//
//    // 只能用于在迭代过程中添加上一个迭代元素
//    public void addLimit() {
//        if (iterator2 == length) {
//            return;
//        }
//        limit++;
//        iterator2++;
//        int e1 = dense[iterator2];
//        int e2 = dense[limit];
//        dense[iterator2] = e2;
//        dense[limit] = e1;
//        sparse[e1] = limit;
//        sparse[e2] = iterator2;
//    }
//
//    public int nextLimit() {
//        return dense[++iterator2];
//    }
//
//    public boolean hasNextLimit() {
//        return iterator2 + 1 <= oldLimit;
//    }
//
//    @Override
//    public String toString() {
//        StringBuilder s = new StringBuilder();
//        s.append("dense  = {");
//        for (int i = 0; i < sparse.length; i++) {
//            if (i == 0) {
//                s.append(dense[i]);
//            } else {
//                s.append(", ").append(dense[i]);
//            }
//        }
//        s.append("}\n");
//        s.append("sparse = {");
//        for (int i = 0; i < sparse.length; i++) {
//            if (i == 0) {
//                s.append(sparse[i]);
//            } else {
//                s.append(", ").append(sparse[i]);
//            }
//        }
//        s.append("}\nlimit = ").append(limit);
//        return s.toString();
//    }
//}
