package org.chocosolver.util.objects;

/**
 * Implementation based on "2013_TRICS_Sparse-Sets for Domain Implementation".
 * <p/>
 * <p/>
 * Created by Jia'nan Chen on 13/11/2019.
 * Project: choco.
 */

public class  SparseSet {
    private int length;
    private int[] sparse;
    private int[] dense;
    private int limit;
    // 用于遍历有效部分或无效部分
    private int iterator;
    // 用于记住原limit
    private int oldLimit;

    public SparseSet(int length) {
        this.length = length;
        this.sparse = new int[length];
        this.dense = new int[length];
        for (int i = 0; i < length; i++) {
            this.sparse[i] = i;
            this.dense[i] = i;
        }
        this.limit = length - 1;
        this.oldLimit = limit;
        this.iterator = -1;
    }

    public void fill() {
        limit = length - 1;
    }

    public void remove(int e) {
        int index = sparse[e];
        if (index <= limit) {
            int tmp = dense[limit];
            sparse[e] = limit;
            sparse[tmp] = index;
            dense[index] = tmp;
            dense[limit] = e;
            limit--;
        }
    }

    // 遍历有效部分（左部集合）
    public void iterateValid() {
        iterator = -1;
    }

    public boolean hasNextValid() {
        return iterator + 1 <= limit;
    }

    // 遍历无效部分（右部集合）
    public void iterateInvalid() {
        iterator = limit;
    }

    public boolean hasNextInvalid() {
        return iterator + 1 < length;
    }

    public int next() {
        return dense[++iterator];
    }

    // 只能用于在迭代过程中删除上一个迭代元素
    public void remove() {
        if (iterator == -1) {
            return;
        }
        int e1 = dense[iterator];
        int e2 = dense[limit];
        dense[iterator] = e2;
        dense[limit] = e1;
        sparse[e1] = limit;
        sparse[e2] = iterator;
        limit--;
        iterator--;
    }

    public void record() {
        oldLimit = limit;
    }

    public void restore() {
        limit = oldLimit;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("dense  = {");
        for (int i = 0; i < sparse.length; i++) {
            if (i == 0) {
                s.append(dense[i]);
            } else {
                s.append(", ").append(dense[i]);
            }
        }
        s.append("}\n");
        s.append("sparse = {");
        for (int i = 0; i < sparse.length; i++) {
            if (i == 0) {
                s.append(sparse[i]);
            } else {
                s.append(", ").append(sparse[i]);
            }
        }
        s.append("}\nlimit = ").append(limit);
        return s.toString();
    }
}
