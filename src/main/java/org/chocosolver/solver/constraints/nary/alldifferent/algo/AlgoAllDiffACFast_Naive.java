package org.chocosolver.solver.constraints.nary.alldifferent.algo;

import amtf.Measurer;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.NaiveBitSet;
import org.chocosolver.util.objects.SparseSet;

import java.util.Arrays;

/**
 * Algorithm of Alldifferent with AC
 * <p>
 * Uses Zhang algorithm in the paper of IJCAI-18
 * "A Fast Algorithm for Generalized Arc Consistency of the Alldifferent Constraint"
 * <p>
 * We try to use the bit to speed up.
 * <p>
 * <p>
 *
 * @author Jean-Guillaume Fages, Zhe Li, Jia'nan Chen
 */
public class AlgoAllDiffACFast_Naive {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************
    // 约束的个数
    static public int num = 0;
    // 约束的编号
    private int id;

    private int arity;
    private IntVar[] vars;
    private ICause aCause;

    // 自由值集合
    private NaiveBitSet freeNode;

    // 以下是bit版本所需数据结构========================
    // numValue是二部图中取值编号的个数，numBit是二部图的最大边数
    private int numValue;
    // 值到索引
    private int[] idx2Val;
    // 索引到值
    private TIntIntHashMap val2Idx;

    // 已访问过的变量和值
    private NaiveBitSet variable_visited_;
    private NaiveBitSet value_visited_;

    // matching
    private int[] val2Var;
    private int[] var2Val;

    // 记录队列
    private int[] visiting_;
    private int[] variable_visited_from_;

    // 变量的论域
    private NaiveBitSet[] varMask;
    private NaiveBitSet[] valMask;

    private int[] fifo;

    // SCC
    private int n, nbSCC;
    private int[] nodeSCC;

    // util
    private int[] stack, p, inf, dfn;
    private NaiveBitSet inStack, distinction, restriction;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************
    public AlgoAllDiffACFast_Naive(IntVar[] variables, ICause cause) {
        id = num++;

        this.vars = variables;
        aCause = cause;
        arity = vars.length;
        val2Idx = new TIntIntHashMap();
        // 统计所有变量论域中不同值的个数
        for (int i = 0; i < arity; ++i) {
            IntVar v = vars[i];
            for (int j = v.getLB(), ub = v.getUB(); j <= ub; j = v.nextValue(j)) {
                if (!val2Idx.containsKey(j)) {
                    val2Idx.put(j, val2Idx.size());
                }
            }
        }

        numValue = val2Idx.size();
        idx2Val = new int[numValue];
        TIntIntIterator it = val2Idx.iterator();
        while (it.hasNext()) {
            it.advance();
            idx2Val[it.value()] = it.key();
        }

//        System.out.println("-----------idx2Val-----------");
//        System.out.println(Arrays.toString(idx2Val));

        varMask = new NaiveBitSet[arity];
        valMask = new NaiveBitSet[numValue];
        for (int i = 0; i < arity; ++i) {
            varMask[i] = new NaiveBitSet(numValue);
        }
        for (int i = 0; i < numValue; ++i) {
            valMask[i] = new NaiveBitSet(arity);
        }

        // 记录访问过的变量
        visiting_ = new int[arity];
        variable_visited_ = new NaiveBitSet(arity);
        // 变量的前驱变量，若前驱变量是-1，则表示无前驱变量，就是第一个变量
        variable_visited_from_ = new int[arity];
        value_visited_ = new NaiveBitSet(numValue);

        var2Val = new int[arity];
        val2Var = new int[numValue];
        for (int i = 0; i < arity; ++i) {
            var2Val[i] = -1;
        }
        for (int i = 0; i < numValue; ++i) {
            val2Var[i] = -1;
        }

        // freeNode区分匹配点和非匹配点（true表示非匹配点，false表示匹配点）
        freeNode = new NaiveBitSet(numValue);

        fifo = new int[arity];

        // SCC
        n = arity + numValue;
        nodeSCC = new int[n];

        stack = new int[n];
        p = new int[n];
        inf = new int[n];
        dfn = new int[n];
        inStack = new NaiveBitSet(n);
        distinction = new NaiveBitSet(n);
        restriction = new NaiveBitSet(n);
    }

    //***********************************************************************************
    // PROPAGATION
    //***********************************************************************************

    public boolean propagate() throws ContradictionException {
//        System.out.println("----------------" + id + " propagate----------------");
//        if (id == 2) {
//            System.out.println("vars: ");
//            for (IntVar v : vars) {
//                System.out.println(v.toString());
//            }
//        }
        long startTime = System.nanoTime();
        findMaximumMatching();
        Measurer.matchingTime += System.nanoTime() - startTime;

        startTime = System.nanoTime();
        boolean filter = filter();
        Measurer.filterTime += System.nanoTime() - startTime;
        return filter;
    }

    //***********************************************************************************
    // Initialization
    //***********************************************************************************

    private void MakeAugmentingPath(int start) {
        // Do a BFS and use visiting_ as a queue, with num_visited pointing
        // at its begin() and num_to_visit its end().
        // To switch to the augmenting path once a nonmatched value was found,
        // we remember the BFS tree in variable_visited_from_.

        // start传入的是变量
        // 执行一个BFS并使用visiting_作为一个队列，num_visited指向它的begin()，
        // num_to_visit指向它的end()。要在发现不匹配的值时切换到扩展路径，
        // 我们需要记住variable_visited_from_中的BFS树
        //
        int num_to_visit = 0;
        int num_visited = 0;
        // Enqueue start.
        // visit 里存的是变量
        visiting_[num_to_visit++] = start;
//        variable_visited_[start] = true;
        variable_visited_.set(start);
        variable_visited_from_[start] = -1;

        while (num_visited < num_to_visit) {
            // Dequeue node to visit.
            int node = visiting_[num_visited++];

            for (int value = varMask[node].nextSetBit(0); value != -1; value = varMask[node].nextSetBit(value + 1)) {
                if (value_visited_.get(value)) continue;
                value_visited_.set(value);
                if (val2Var[value] == -1) {
                    // value_to_variable_[value] ， value这个值未分配到变量，即是一个free
                    // !! 这里可以改用bitSet 求原数据bitDom (successor_)
                    // 与matching的余集(matching_bitVector[a]，表示a是否已matching出去了) 再按1取未匹配值，
                    // 可以惰性取值，即先算两个集合的在特定位置的交：以matching_bv为长度foreach
                    // （一般不会特别长两个数据结构可以用NaiveBitSet，如400皇后，|D|=400，只需要7个，
                    // 做&后会得到一个或NaiveBitSet, LargeBitSet）
                    // value is not matched: change path from node to start, and return.
                    // 未匹配值

                    // !! 路线回溯怎么用bit表示。
                    // !! 这里可以提前记一些scc或是路径
                    int path_node = node;
                    int path_value = value;
                    while (path_node != -1) {
                        // 旧变量拿到旧匹配值
                        int old_value = var2Val[path_node];
                        // 旧变量拿到新匹配值
                        var2Val[path_node] = path_value;
                        val2Var[path_value] = path_node;

                        // 回溯到上一个变量
                        path_node = variable_visited_from_[path_node];
                        // 由于这个变量传递下去是连贯的，可以检查连通生，做为下一个阶段的记录
                        path_value = old_value;
                    }

                    freeNode.clear(value);
//                    System.out.println(value + " is not free");
                    return;
                } else {
                    // Enqueue node matched to value.
                    // 若没有该值已经有匹配，但变量没有匹配

                    // 先拿到这个值的匹配变量
                    int next_node = val2Var[value];
                    variable_visited_.set(next_node);
//                    System.out.println(num_to_visit + "," + next_node);
                    // 把这个变量加入队列中
                    visiting_[num_to_visit++] = next_node;
                    variable_visited_from_[next_node] = node;
                    freeNode.clear(value);
                }
            }
        }
    }

    private void findMaximumMatching() throws ContradictionException {
        for (int i = 0; i < numValue; ++i) {
            valMask[i].clear();
        }
        freeNode.set();
        // 增量检查
        // matching 有效性检查
        for (int varIdx = 0; varIdx < arity; varIdx++) {
            varMask[varIdx].clear();
            IntVar v = vars[varIdx];
            if (v.getDomainSize() == 1) {
                // 取出变量的唯一值
                int valIdx = val2Idx.get(v.getValue());
                varMask[varIdx].set(valIdx);
                valMask[valIdx].set(varIdx);
//                System.out.println(v.getName() + " : " + varIdx + " is singleton = " + v.getValue() + " : " + valIdx);

                int oldValIdx = var2Val[varIdx];
                int oldVarIdx = val2Var[valIdx];

                if (oldValIdx != -1 && oldValIdx != valIdx) {
                    val2Var[oldValIdx] = -1;
                }
                if (oldVarIdx != -1 && oldVarIdx != varIdx) {
                    var2Val[oldVarIdx] = -1;
                }

                val2Var[valIdx] = varIdx;
                var2Val[varIdx] = valIdx;
                freeNode.clear(valIdx);
            } else {
                // 检查原匹配是否失效
                int oldMatchingIndex = var2Val[varIdx];
                if (oldMatchingIndex != -1) {
                    // 如果oldMatchingValue无效
                    if (!v.contains(idx2Val[oldMatchingIndex])) {
                        val2Var[oldMatchingIndex] = -1;
                        var2Val[varIdx] = -1;
                    } else {
                        freeNode.clear(oldMatchingIndex);
//                    System.out.println(oldMatchingIndex + " is free");
                    }
                }

                for (int value = v.getLB(), ub = v.getUB(); value <= ub; value = v.nextValue(value)) {
                    int valIdx = val2Idx.get(value);
                    // Forward-checking should propagate xsu != value.
                    varMask[varIdx].set(valIdx);
                    valMask[valIdx].set(varIdx);
                }
            }
        }

        // Compute max matching.
        for (int varIdx = 0; varIdx < arity; varIdx++) {
            if (var2Val[varIdx] == -1) {
                value_visited_.clear();
                variable_visited_.clear();
                MakeAugmentingPath(varIdx);
            }
            if (var2Val[varIdx] == -1) {
                // No augmenting path exists.
                vars[0].instantiateTo(vars[0].getLB() - 1, aCause);
            }
        }

        for (int varIdx = 0; varIdx < arity; varIdx++) {
            valMask[var2Val[varIdx]].clear(varIdx);
        }

//        if (id == 2) {
//            System.out.println("-----final matching-----");
//            for (int i = 0; i < arity; i++) {
//                System.out.println(vars[i].getName() + " match " + idx2Val[var2Val[i]]);
//            }
//            System.out.println("------------------");
//        }
//        System.out.println(Arrays.toString(var2Val));
//        System.out.println(Arrays.toString(val2Var));
//        System.out.println(freeNode);
    }

    //***********************************************************************************
    // PRUNING
    //***********************************************************************************

    private void distinguish() {
        distinction.clear();
        int valIdx, varIdx;
        int indexFirst = 0, indexLast = 0;
        for (valIdx = freeNode.nextSetBit(0); valIdx != -1; valIdx = freeNode.nextSetBit(valIdx + 1)) {
            distinction.set(valIdx + arity);
            // 首先把与自由值相连的变量入队列
            for (varIdx = valMask[valIdx].nextSetBit(0); varIdx != -1; varIdx = valMask[valIdx].nextSetBit(varIdx + 1)) {
                if (!distinction.get(varIdx)) {
                    fifo[indexLast++] = varIdx;
                    distinction.set(varIdx);
                }
            }
            // 然后，对队列中每个变量的匹配值，把与该值相连的非匹配变量入队
            while (indexFirst != indexLast) {
                varIdx = fifo[indexFirst++];
                valIdx = var2Val[varIdx];
                distinction.set(valIdx + arity);
                for (varIdx = valMask[valIdx].nextSetBit(0); varIdx != -1; varIdx = valMask[valIdx].nextSetBit(varIdx + 1)) {
                    if (!distinction.get(varIdx)) {
                        fifo[indexLast++] = varIdx;
                        distinction.set(varIdx);
                    }
                }
            }
        }
    }

    private void buildSCC() {
        restriction.set(distinction);
        restriction.flip();
        inStack.clear();
        for (int i = 0; i < n; i++) {
            dfn[i] = 0;
            inf[i] = n + 2;
            nodeSCC[i] = -1;
        }
        nbSCC = 0;
    }

    private boolean filter() throws ContradictionException {
        distinguish();
        buildSCC();
        // 这里判断一下，如果notGamma为空则不用进行如下步骤
        boolean filter = false;
        for (int varIdx = 0; varIdx < arity; varIdx++) {
            IntVar v = vars[varIdx];
            if (!v.isInstantiated()) {
                int ub = v.getUB();
                for (int k = v.getLB(); k <= ub; k = v.nextValue(k)) {
                    int valIdx = val2Idx.get(k);
                    int valNewIdx = valIdx + arity;
                    if (distinction.get(varIdx) && !distinction.get(valNewIdx)) {
                        filter |= v.removeValue(k, aCause);
                        //                System.out.println("first delete: " + v.getName() + ", " + k);
                    } else if (!distinction.get(varIdx) && !distinction.get(valNewIdx)) {
                        if (nodeSCC[varIdx] != nodeSCC[valNewIdx]) {
                            if (valIdx == var2Val[varIdx]) {
                                filter |= v.instantiateTo(k, aCause);
//                            System.out.println("instantiate  : " + v.getName() + ", " + k);
                            } else {
                                filter |= v.removeValue(k, aCause);
//                            System.out.println("second delete: " + v.getName() + ", " + k);
                            }
                        }
                    }
                }
            }
        }
        return filter;
    }
}