package org.chocosolver.solver.constraints.nary.alldifferent.algo;

import amtf.TimeCount;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import org.chocosolver.solver.Cause;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.NaiveBitSet;
//import org.chocosolver.util.objects.NaiveSparseBitSet;
import org.chocosolver.util.objects.SparseSet;

import java.util.Arrays;

import static java.lang.System.out;

/**
 * Algorithm of Alldifferent with AC
 * <p>
 * Uses Zhang algorithm in the paper of IJCAI-18
 * "A Fast Algorithm for Generalized Arc Consistency of the Alldifferent Constraint"
 * <p>
 * We try to use the bit to speed up.
 * <p>
 * The version of Fastbit1 can normally run.
 * We try to optimize it from three aspects.
 * <p>
 * 1. initialization
 * 2. bit operation
 * 3. scc father path
 *
 * @author Jean-Guillaume Fages, Zhe Li, Jia'nan Chen
 */
public class AlgoAllDiffAC_Naive2 {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    //    private int n, n2;
    private int arity;
    private IntVar[] vars;
    private ICause aCause;
//    // 原map是取值到取值编号的映射，一对一
//    private TIntIntHashMap map;
    //    private DirectedGraph digraph;
//    private int[] matching;

    // 自由值集合
    private NaiveBitSet freeNode;
    // for augmenting matching (BFS)
//    private int[] father;
//    private int[] fifo;
//    private BitSet in;

    // 以下是bit版本所需数据结构========================
    // numValue是二部图中取值编号的个数，numBit是二部图的最大边数
    private int numValue;
    // 需要新增一个取值编号到取值的映射，也是一对一
//    private TIntIntHashMap idToVal;
    // 值到索引
    private int[] idx2Val;
    // 索引到值
    private TIntIntHashMap val2Idx;

    // Xc-Γ(A)
    private SparseSet notGamma;
    // Dc-A
    private SparseSet notA;


    // 记录每个变量的有效值， 可以用BitSet代替，同时生成两个数据结构
//    private NaiveBitSet[] varMask;
    // 已访问过的变量和值
    private NaiveBitSet variable_visited_;
    private NaiveBitSet value_visited_;

    // matching
    private int[] val2Var;
    private int[] var2Val;

    //    // 记录之前的匹配
    // !! 暂时删掉
//    private int[] prev_matching_;
    // 记录队列
    private int[] visiting_;
    private int[] variable_visited_from_;

    // 变量到变量的连通性
//    private NaiveBitSet[] SCCMatrix;
    // 对于惰性算法，记录是否知道-变量到变量的连通性
//    private NaiveBitSet[] SCCKnown;
    private NaiveBitSet[] graphLinkedMatrix;
    private NaiveBitSet[] graphLinkedFrontier;
    // 记录是否已经扩展过了， 扩展过后就不再扩展
    // !! 这个未来可以用SparseBitSet
//    private NaiveBitSet gammaExtended;
    // !! 记录gamma的前沿
    private NaiveBitSet gammaFrontier;

    // 为每个变量记录其扩展， 可以在initial函数中修改
    // 未来数据结构可以改成 SparseBitSet
//    private NaiveBitSet[] BFSExtended;

    // 变量的论域
    private NaiveBitSet[] varMask;
    private NaiveBitSet[] valMask;

    // 记录gamma的bitset
    private NaiveBitSet gammaMask;

    // 记录排除gamma和bind的变量，即notGamma的变量
    private NaiveBitSet notGammaMask;

    // 两个数据结构
    // 改变的变量
    private SparseSet sVal;
    // 需要过滤的变量，未绑定的变量
    private SparseSet sSup;

    //
    private int[] lastSize;


    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************
    public AlgoAllDiffAC_Naive2(IntVar[] variables, ICause cause) {
        this.vars = variables;
        aCause = cause;
        arity = vars.length;
        val2Idx = new TIntIntHashMap();

        IntVar v;
        // 统计所有变量论域中不同值的个数
        for (int i = 0; i < arity; ++i) {
            v = vars[i];
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

        // freeNode应该区分匹配点和非匹配点（true表示非匹配点，false表示匹配点）
        freeNode = new NaiveBitSet(numValue);
        notGammaMask = new NaiveBitSet(arity);

        graphLinkedMatrix = new NaiveBitSet[arity];
        graphLinkedFrontier = new NaiveBitSet[arity];


//        gammaExtended = new NaiveBitSet(arity);
        gammaFrontier = new NaiveBitSet(arity);

        varMask = new NaiveBitSet[numValue];
        valMask = new NaiveBitSet[numValue];
        gammaMask = new NaiveBitSet(arity);

        for (int i = 0; i < arity; ++i) {
            varMask[i] = new NaiveBitSet(numValue);
        }
        for (int i = 0; i < numValue; ++i) {
            valMask[i] = new NaiveBitSet(arity);
        }

        notGamma = new SparseSet(arity);
        notA = new SparseSet(numValue);

        // 记录访问过的变量
        visiting_ = new int[arity];
//        variable_visited_ = new boolean[n];
        variable_visited_ = new NaiveBitSet(arity);
        // 变量的前驱变量，若前驱变量是-1，则表示无前驱变量，就是第一个变量
        variable_visited_from_ = new int[arity];
        varMask = new NaiveBitSet[arity];

        for (int i = 0; i < arity; ++i) {
            varMask[i] = new NaiveBitSet(numValue);
        }
//        value_visited_ = new boolean[numValue];
        value_visited_ = new NaiveBitSet(numValue);
        var2Val = new int[arity];
//        prev_matching_ = new int[arity];
        val2Var = new int[numValue];

        for (int i = 0; i < arity; ++i) {
//            prev_matching_[i] = -1;
            var2Val[i] = -1;
        }

        for (int i = 0; i < numValue; ++i) {
            val2Var[i] = -1;
        }


        for (int i = 0; i < arity; ++i) {
            graphLinkedMatrix[i] = new NaiveBitSet(arity);
            graphLinkedFrontier[i] = new NaiveBitSet(arity);
        }


        notGamma = new SparseSet(arity);
        notA = new SparseSet(numValue);
        sSup = new SparseSet(arity);
        sVal = new SparseSet(arity);
        lastSize = new int[arity];

    }

    boolean MakeAugmentingPath(int start) {
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
                    return true;
                } else {
                    // Enqueue node matched to value.
                    // 若没有该值已经有匹配，但变量没有匹配

                    // 先拿到这个值的匹配变量
                    int next_node = val2Var[value];
                    variable_visited_.set(next_node);
                    System.out.println(num_to_visit + "," + next_node);
                    // 把这个变量加入队列中
                    visiting_[num_to_visit++] = next_node;
                    variable_visited_from_[next_node] = node;
//                    free.clear(value);
                }
            }
        }

        return false;
    }

    //***********************************************************************************
    // PROPAGATION
    //***********************************************************************************

    public boolean propagate() throws ContradictionException {
        TimeCount.startTime = System.nanoTime();
        out.println("-----------propagate-----------");
        freeNode.set();

        // !! 可做增量
        for (int i = 0; i < numValue; ++i) {
            valMask[i].clear();
        }

        // 初始化两个not集合
        notGamma.fill();
        notGammaMask.set();
        notA.fill();

        sSup.clear();
        sVal.clear();

        // 增量检查
        // matching 有效性检查
        // !! 可以增量修改值
        // 这里先统计一下sVal和sSup
        IntVar v;
        int oldValIdx, oldVarIdx;
        for (int x = 0; x < arity; x++) {
            v = vars[x];
            // !! 这里可以修改一下 已赋值 就不参与修改了
            // 绑定
            if (v.getDomainSize() == 1) {
                // 取出变量的唯一值
                int valueIndex = val2Idx.get(v.getValue());
//                varMask[x].clear();
//                varMask[x].set(valueIndex);
                oldValIdx = var2Val[x];
                oldVarIdx = val2Var[valueIndex];

                if (oldValIdx == valueIndex && oldVarIdx == x) {
                    // 变量值早已匹配好，跳出
                    break;
//                } else if (var2Val[x] != valueIndex) {
                } else {
                    // 若上轮匹配值不是当前唯一有效值，更新一番，
                    // 获取上轮匹配值，
                    // 若上次的匹配值是-1说明上论没匹配上，可能适用于第一次匹配，或上轮匹配失败的情况
                    if (oldValIdx != -1 && oldVarIdx != -1) {
                        // 若之前各有各的匹配
                        // 相当于各自有各自的家庭
                        // 拆散两个家庭
                        // 将值的匹配变量设置为无效
                        val2Var[oldValIdx] = -1;
                        // 将变量的匹配值设置为无效
                        var2Val[oldVarIdx] = -1;

                        break;
                    } else if (oldValIdx == -1 && oldVarIdx != -1) {
                        // x的原配失效，唯一值的原配未失效
                        // 将变量的匹配值设置为无效
                        var2Val[oldVarIdx] = -1;
                    } else if (oldValIdx != -1 && oldVarIdx == -1) {
                        // x的原配未失效，唯一值的原配失效
                        // 将其原配的匹配变量设置为无效
                        val2Var[oldValIdx] = -1;
                    }
                    //若两个都失效了，直接修改就可以了

                    // 建立新的匹配
                    val2Var[valueIndex] = x;
                    var2Val[x] = valueIndex;

                }
//                else if (val2Var[valueIndex] != x){}


//                // 打断唯一值到其它变量的
//                oldValIdx = var2Val[x];
//                if (val2Var[valueIndex] == -1) {
//                    // 释出旧匹配值
////                    int oldValIdx = var2Val[x];
//                    val2Var[oldValIdx] = -1;
//                    val2Var[valueIndex] = x;
//                    var2Val[x] = valueIndex;
//                }

                // 对于已经绑定的值，不再纳入A和gamma，SCC查找
                notGamma.remove(x);
                notGammaMask.clear(x);
                notA.remove(valueIndex);
                freeNode.clear(valueIndex);
                // 记录
            } else {
                // 生成VarMask和valMask

                // !! 利用一下lastSize
//                sSup.add(x);
//                if (lastSize[x] != v.getDomainSize()) {
//                    lastSize[x] = v.getDomainSize();
//                    sVal.add(x);
//                }

//                prev_matching_[x] = variable_to_value_[x];

                // 重新统计一下各种值
//                oldValIdx = var2Val[x];
//                oldVarIdx = val2Var[valueIndex];


                // 检查原匹配是否失效
                // 拿到变量原配
                int oldMatchingIndex = var2Val[x];
//                 // 若变量原配失效，
//                 // 什么都不用改
                // 若oldMatchingIndex == -1则!v.contains(idx2Val[oldMatchingIndex])
//                if (oldMatchingIndex == -1) {
//
//                }

                // 若匹配值已被删除了,但仍记录着原匹配值
                // 即：如果oldMatchingValue无效，并且不为-1
                if (oldMatchingIndex != -1 && !v.contains(idx2Val[oldMatchingIndex])) {
                    val2Var[oldMatchingIndex] = -1;
                    var2Val[x] = -1;
                    freeNode.set(oldMatchingIndex);
//                    System.out.println(oldMatchingIndex + " is free");
                }

                // !! 这里最好拿到论域delta，这样就不用varMask.clear()
                varMask[x].clear();
                for (int value = v.getLB(), ub = v.getUB(); value <= ub; value = v.nextValue(value)) {
//                int offset_value = map.get(value) - n;
//                System.out.println("___________________________");
//                System.out.println("valid value: (" + v.getName() + ", " + (map.get(value) - n) + ")");
//                System.out.println("valid value: (" + v.getName() + ", " + val2Idx.get(value) + ")");
                    int valueIndex = val2Idx.get(value);

                    // Forward-checking should propagate xsu != value.
//                successor_[x].add(offset_value);
                    varMask[x].set(valueIndex);

                    // !! 可以增量修改值
//                    varMask[x].set(valueIndex);
                    valMask[valueIndex].set(x);
                }
//                if (successor_[x].size() == 1) {
////                int offsetIndex = successor_[x].get(0);
//                    int offsetIndex = successor_[x].nextSetBit(0);
//
//                }
            }

        }

//        // Seed with previous matching.
//        for (int x = 0; x < arity; x++) {
//            if (variable_to_value_[x] != -1) continue;
//
//            // 先前匹配
//            int prev_value = prev_matching_[x];
//            if (prev_value == -1 || value_to_variable_[prev_value] != -1) continue;
//            v = vars[x];
////            if (VariableHasPossibleValue(x, prev_matching_[x] + min_all_values_)) {
////            if (v.contains(idToVal.get(prev_matching_[x] + n))) {
//            if (v.contains(idx2Val[prev_matching_[x]])) {
//                variable_to_value_[x] = prev_matching_[x];
//                value_to_variable_[prev_matching_[x]] = x;
//            }
//        }

        System.out.println("-----prematching-----");
        System.out.println(Arrays.toString(var2Val));
        System.out.println(Arrays.toString(val2Var));
        System.out.println("---------------------");

        // Compute max matching.
        for (int x = 0; x < arity; x++) {
            if (var2Val[x] == -1) {
                // !! 这可用稀疏集
                value_visited_.clear();
                variable_visited_.clear();
                MakeAugmentingPath(x);
            }
            if (var2Val[x] == -1) return false;  // No augmenting path exists.
        }


        System.out.println("-----matching-----");
        System.out.println(Arrays.toString(var2Val));
        System.out.println(Arrays.toString(val2Var));
        System.out.println("------------------");
//        System.out.println(freeNode);

        //
        /////////////////////////////////////////////////////////
        //      out.println("-----matching-----");
//        for (int i = 0; i < n; i++) {
////            matching[i] = digraph.getPredOf(i).isEmpty() ? -1 : digraph.getPredOf(i).iterator().next();
//            // 初始化matchedEdge、varMatchedEdge、valMatchedEdge
//            matching[i] = variable_to_value_[i] + n;
//            int valIdx = matching[i] - n; // 因为构造函数中建立map时是从n开始的，所以这里需要减去n
////          out.println(i + " matching " + valIdx);
//            int edgeIdx = i * numValue + valIdx;
//            matchedEdge.set(edgeIdx);
//            varMatchedEdge[i] = edgeIdx;
//            valMatchedEdge[valIdx] = edgeIdx;
//        }

//        searchEdge.clear();
//
//        ////////////////////////////////////////////////
//        // 变量和值合在一起
////        for (int i = 0; i < n; ++i) {
////            SCCMatrix[i] = new NaiveBitSet(n);
////            SCCKnown[i] = new NaiveBitSet(n);
////        }
//
//
//        /////////////////////////////////////////////////////////
//        // 寻找从自由值出发的所有交替路
//        // 首先将与自由值相连的边并入允许边
//        gammaExtended.clear();
        // freeNode转变量
        gammaMask.clear();
        gammaFrontier.clear();
        for (int i = freeNode.nextSetBit(0); i != -1; i = freeNode.nextSetBit(i + 1)) {
            // 每个freeNode的值拿出来
//            System.out.println(i);
            notA.remove(i);
            gammaMask.or(valMask[i]);
            notGammaMask.clear(valMask[i]);
            gammaFrontier.or(valMask[i]);

        }

//
//        // 从gamma向外BFS
//        // visiting_ 用来记录BFS前沿
//        // 也可以记录本次和上次的mask通过位运算算出delta
//        for (int i = gamma.nextSetBit(0); i != -1; i = gamma.nextSetBit(i + 1)) {
//
//            // Enqueue start.
//            // visit 里存的是变量
//            visiting_[num_to_visit++] = i;
////            gamma.or(valMask[i]);
////            notA.remove(i);
////            gamma.or(valMask[i]);
////            notA.remove(i);
//        }
//
//
////        for (int i = NaiveBitSet.NextSetBitAfterMinus(gamma, gammaExtended, 0);
////             i != -1; NaiveBitSet.NextSetBitAfterMinus(gamma, gammaExtended, 0)) {
////            // !! 这里可以将Extended改成Frontier，只记录前沿，记录方法是三个BitSet比较，
////            gamma.or(valMask[i]);
////            gammaExtended.set(i);
////        }
//
//
        // !! 这里可以再优化一下
        // !! Frontier应该用SparseBitSet(largeBitSet)

        for (int i = gammaFrontier.nextSetBit(0);
             i != -1; i = gammaFrontier.nextSetBit(0)) {
            // !! 这里可以将Extended改成Frontier，只记录前沿，记录方法是三个BitSet比较，
            // frontier 扩展，从valMask中去掉gammaMask已记录的变量
            gammaFrontier.orAfterMinus(valMask[var2Val[i]], gammaMask);
            // 除去第i个变量
            gammaFrontier.clear(i);
            // gamma 扩展
            gammaMask.or(valMask[var2Val[i]]);
        }
//
//        // 到这里时 gamma 和gammaExtended都一样了。这时候统计一下notgamma 和notA
//        for (int i = gamma.nextSetBit(0); i != -1; i = gamma.nextSetBit(i + 1)) {
//            notGamma.remove(i);
//            notA.remove(variable_to_value_[i]);
//            notGammaMask.clear(i);
//        }

        // 到这里时 frontier全部遍历完。这时候统计一下notGamma 和notA
        for (int i = gammaMask.nextSetBit(0); i != -1; i = gammaMask.nextSetBit(i + 1)) {
            notGamma.remove(i);
            notGammaMask.clear(i);
            notA.remove(var2Val[i]);
        }


//        System.out.println("------------notGammaMask------------");
//        System.out.println(notGammaMask);

//
//        // 重置两个矩阵
//        // 只重置notGamma的变量
//        // !! 重置的内容也除去gamma的中的变量
//        notGamma.iterateValid();
//        while (notGamma.hasNextValid()) {
//            int varIdx = notGamma.next();
//            graphLinkedMatrix[varIdx].setAfterMinus(valMask[variable_to_value_[varIdx]], notGammaMask);
//            graphLinkedFrontier[varIdx].set(valMask[variable_to_value_[varIdx]]);
//        }


//
//        TimeCount.matchingTime += System.nanoTime() - TimeCount.startTime;
//
//        TimeCount.startTime = System.nanoTime();
//
//
//        System.out.println("-----matching-----");
//        System.out.println(Arrays.toString(variable_to_value_));
//        System.out.println(Arrays.toString(value_to_variable_));
//        System.out.println(Arrays.toString(matching));
////        System.out.println("-----matching-----");
////        System.out.println(Arrays.toString(matching));
////        System.out.println("-----leftEdge-----");
////        System.out.println(leftEdge.toString());
////        System.out.println("-----matchedEdge-----");
////        System.out.println(matchedEdge.toString());
////        System.out.println("---varMatchedEdge---");
////        for (int a : varMatchedEdge) {
////            System.out.println(a);
////        }
////        System.out.println("---valMatchedEdge---");
////        for (int a : valMatchedEdge) {
////            System.out.println(a);
////        }
////        System.out.println("---varEdge---");
////        for (NaiveSparseBitSet a : varEdge) {
////            System.out.println(a.toString());
////        }
////        System.out.println("---valEdge---");
////        for (NaiveSparseBitSet a : valEdge) {
////            System.out.println(a.toString());
////        }

        // 这里判断一下，如果notGamma为空则不用进行如下步骤
        if (!notGamma.empty()) {
            // 重置两个矩阵
            // 只重置notGamma的变量
            // !! 重置的内容也除去gamma的中的变量
            notGamma.iterateValid();
            while (notGamma.hasNextValid()) {
                // 拿到变量id
                int varIdx = notGamma.next();
                // 从变量id拿到匹配值再拿到该值所能到达的变量mask，但只加入gamma中的变量，即mask&gamma
                graphLinkedMatrix[varIdx].setAfterAnd(valMask[var2Val[varIdx]], notGammaMask);
                // 记录前沿
                graphLinkedFrontier[varIdx].setAfterAnd(valMask[var2Val[varIdx]], notGammaMask);
            }

            // 过滤论域
            filterDomains();
        }


        return true;

    }

    private void updateGraph() {

        updateDataStucture();

        match();

        propagateFree();

    }

    private void propagateFree() {
    }

    private void match() {
    }

    private void updateDataStucture() {
    }

    private boolean filterDomains() throws ContradictionException {
        boolean filter = false;
        filter |= filterFirstEdges();
        filter |= filterSecondEdges();
        TimeCount.filterTime += System.nanoTime() - TimeCount.startTime;
        return filter;
    }

    //!! 此处可以优化
    // 1 现在这个集合中gamma有一部分是bind的变量，应该在循环中去掉这部分变量
    // 2 有的情况下没有gamma,如果notA做为外层循环，会浪费。
    // 3 一般而言notA要多于gamma，应小循环套大循环
    private boolean filterFirstEdges() throws ContradictionException {
        boolean filter = false;
        int varIdx, valIdx;
        IntVar v;
        int k;
        notGamma.iterateInvalid();
        while (notGamma.hasNextInvalid()) {
            varIdx = notGamma.next();
            v = vars[varIdx];

            notA.iterateValid();
            while (notA.hasNextValid()) {
                valIdx = notA.next();
                k = idx2Val[valIdx];
//                System.out.println("first delete:" + v + ", " + k);
                filter |= v.removeValue(k, aCause);
                --lastSize[varIdx];
            }
        }
        return filter;
    }

    private boolean filterSecondEdges() throws ContradictionException {
        boolean filter = false;
        int varIdx, valIdx, k, matchingValIdx;
        IntVar x, y;
        notGamma.iterateValid();
        while (notGamma.hasNextValid()) {
            varIdx = notGamma.next();
            x = vars[varIdx];
            matchingValIdx = var2Val[varIdx];
            for (valIdx = varMask[varIdx].nextSetBit(0); valIdx != -1;
                 valIdx = varMask[varIdx].nextSetBit(valIdx + 1)) {
                k = idx2Val[valIdx];
                if (x.contains(k)) {
                    // 检查在不在SCC里
                    // 判断值是否是匹配值
                    if (valIdx == matchingValIdx) {
                        // 该值是匹配值
                    } else {
                        // 该值不是该变量的匹配值
                        if (!isSCCUm(varIdx, valIdx)) {
//                            System.out.println("second delete:" + x + ", " + k);
                            filter |= x.removeValue(k, aCause);
                            --lastSize[varIdx];
                        }
                    }
                }
            }

        }

        return false;
    }

    private boolean isSCCUm(int varIdx, int valIdx) {

        // 如果已经有记录了
        if (graphLinkedMatrix[varIdx].get(val2Var[valIdx])) {
            return true;
        }

        // 若没有 就需要BFS一下Frontier没有，就表示不用扩展了
        // !! 这里可以优化成一直就记录着count
        // 注意一下return退出时frontier正确
        for (int i = graphLinkedFrontier[varIdx].nextSetBit(0);
             i != -1; i = graphLinkedFrontier[varIdx].nextSetBit(0)) {
            // !! 这里可以将Extended改成Frontier，只记录前沿，记录方法是三个BitSet比较，
            // frontier扩张，除掉变量i 因为变量i已被扩展。
            // 向frontier添加 varMask 但不属于
            graphLinkedFrontier[varIdx].orAfterMinus(graphLinkedMatrix[i], graphLinkedMatrix[varIdx]);
            graphLinkedFrontier[varIdx].clear(i);
            // gamma 扩展
            graphLinkedMatrix[varIdx].or(graphLinkedMatrix[i]);
            if (graphLinkedMatrix[varIdx].get(val2Var[valIdx])) {
                return true;
            }
        }

        return false;
    }


}