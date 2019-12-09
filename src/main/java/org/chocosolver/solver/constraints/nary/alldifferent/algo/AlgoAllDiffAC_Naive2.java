package org.chocosolver.solver.constraints.nary.alldifferent.algo;

import amtf.TimeCount;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.NaiveBitSet;
//import org.chocosolver.util.objects.NaiveSparseBitSet;
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
        freeNode.set();
        System.out.println(freeNode);
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
        for (int x = 0; x < arity; x++) {
            v = vars[x];
            // !! 这里可以修改一下 已赋值 就不参与修改了
            // 绑定
            if (v.getDomainSize() == 1) {
                int valueIndex = val2Idx.get(v.getValue());
                varMask[x].clear();
                varMask[x].set(valueIndex);
                if (val2Var[valueIndex] == -1) {
                    val2Var[valueIndex] = x;
                    var2Val[x] = valueIndex;
                }

                // 对于已经绑定的值，不再纳入A和gamma，SCC查找
                notGamma.remove(x);
                notA.remove(valueIndex);
                freeNode.clear(valueIndex);
                // 记录
                notGammaMask.clear(x);
            } else {
                // 生成VarMask和valMask
                sSup.add(x);
                if (lastSize[x] != v.getDomainSize()) {
                    lastSize[x] = v.getDomainSize();
                    sVal.add(x);
                }

//                prev_matching_[x] = variable_to_value_[x];
                varMask[x].clear();
                varMask[x].clear();

                // 检查原匹配是否失效
                int oldMatchingIndex = var2Val[x];
                if (oldMatchingIndex != -1 && !v.contains(idx2Val[oldMatchingIndex])) {
                    // 如果oldMatchingValue无效，并且不为-1
                    val2Var[oldMatchingIndex] = -1;
                    var2Val[x] = -1;
                    freeNode.set(oldMatchingIndex);
                    System.out.println(oldMatchingIndex + " is free");
                }

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

        // Compute max matching.
        for (int x = 0; x < arity; x++) {
            if (var2Val[x] == -1) {
                value_visited_.clear();
                variable_visited_.clear();
                MakeAugmentingPath(x);
            }
            if (var2Val[x] == -1) return false;  // No augmenting path exists.
        }


        System.out.println("-----matching-----");
        System.out.println(Arrays.toString(var2Val));
        System.out.println(Arrays.toString(val2Var));
        System.out.println(freeNode);

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
            System.out.println(i);
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

        // 重置两个矩阵
        // 只重置notGamma的变量
        // !! 重置的内容也除去gamma的中的变量
        notGamma.iterateValid();
        while (notGamma.hasNextValid()) {
            // 拿到变量id
            int varIdx = notGamma.next();
            // 从变量id拿到匹配值再拿到该值所能到达的变量mask，但只加入gamma中的变量，即mask&gamma
            graphLinkedMatrix[varIdx].setAfterAnd(valMask[var2Val[varIdx]], gammaMask);
            // 记录前沿
            graphLinkedFrontier[varIdx].setAfterAnd(valMask[var2Val[varIdx]], gammaMask);
        }
//
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
        filterDomains();

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
                        if (isSCCUm(varIdx, valIdx)) {

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
                filter |= v.removeValue(k, aCause);
                --lastSize[varIdx];
            }
        }
        return filter;
    }


    //***********************************************************************************
    // Initialization
    // 1 确定修改的变量，
    // 2 适当初始化一些数据结构
    //***********************************************************************************

    //***********************************************************************************
    // PRUNING
    //***********************************************************************************
//
//    //  新函数从自由点出发，寻找交替路，区分论文中的四个集合
//    private void distinguish() {
//        searchEdge.clear();
//        // 寻找从自由值出发的所有交替路
//        // 首先将与自由值相连的边并入允许边
//        for (int i = free.nextSetBit(n); i >= n && i < n2; i = free.nextSetBit(i + 1)) {
//            int valIdx = i - n; // 因为构造函数中建立map时是从n开始的，所以这里需要减去n
//            notA.remove(valIdx);
//            searchEdge.or(valEdge[valIdx]);
//        }
//        searchEdge.and(leftEdge);
//        // 然后看是否能继续扩展
//        boolean extended;
//        do {
//            extended = false;
//            notGamma.iterateValid();
//            while (notGamma.hasNextValid()) {
//                int varIdx = notGamma.next();
//                if (searchEdge.isIntersect(varEdge[varIdx])) {
//                    extended = true;
//                    searchEdge.set(varMatchedEdge[varIdx]);
//                    notGamma.remove();
//                    // 把与匹配值相连的边并入
//                    int valIdx = matching[varIdx] - n;
////                    searchEdge.or(valEdge[valIdx]);
////                    searchEdge.and(leftEdge);
//                    searchEdge.setThenAnd(valEdge[valIdx], leftEdge);
//                    notA.remove(valIdx);
//                }
//            }
//        } while (extended);
//
//        // out.println("-----notGamma-----");
//        // out.println(notGamma.toString());
//        // out.println("-----notA-----");
//        // out.println(notA.toString());
////        out.println("-----searchEdge-----");
////        out.println(searchEdge.toString());
//    }
//
//    // 过滤第一种类型的冗余边
//    private boolean filterFirstPart() throws ContradictionException {
//        boolean filter = false;
//        int varIdx, valIdx;
//        IntVar v;
//        int k;
//        notA.iterateValid();
//        while (notA.hasNextValid()) {
//            valIdx = notA.next();
//            notGamma.iterateInvalid();
//            while (notGamma.hasNextInvalid()) {
//                varIdx = notGamma.next();
//                v = vars[varIdx];
//                k = idToVal.get(valIdx + n);
//                filter |= v.removeValue(k, aCause);
//                leftEdge.clear(varIdx * numValue + valIdx);
//            }
//        }
//        return filter;
//    }
//
//    // 寻找第二种类型的冗余边
//    private boolean filterSecondPart() throws ContradictionException {
//        boolean filter = false;
//        int varIdx, valIdx;
//        IntVar v;
//        int k;
//        int edgeIdx;
//
//        // 从leftEdge中去掉searchEdge，即是需要检查SCC的边
//        leftEdge.clear(searchEdge);
////        out.println("-----leftEdge-----");
////        out.println(leftEdge.toString());
//
//        // out.println("-----SCC-----");
//        // 记录当前limit
//        notGamma.record();
//        sccEdge.clear();
//
//        // -------------------放在一起检查-------------------
//        edgeIdx = leftEdge.nextSetBit(0);
//        while (edgeIdx != -1) {
//            if (vars[edgeIdx / numValue].getDomainSize() > 1 && !sccEdge.get(edgeIdx)) {
//                if (checkSCC(edgeIdx)) {
//                    // 进一步的还可以回溯路径，从checkEdge中删除
////                //out.println(edgeIdx + " is in SCC");
////                    v = vars[varIdx];
//////                    k = idToVal.get(edgeIdx % numValue + n);
//                    if (matchedEdge.get(edgeIdx)) { // 如果edge是匹配边
////                        filter |= v.instantiateTo(k, aCause);
//////                        out.println(v.getName() + " instantiate to " + k);
////                        // 从leftEdge中去掉被删的边
////                        leftEdge.clear(varEdge[varIdx]);
////                        varEdge[varIdx]
////                        System.out.println("----------" + edgeIdx + "----------");
////                        System.out.println(searchEdge);
////                        sccEdge.set(edgeIdx);
////                        System.out.println(sccEdge);
////                        notGamma.iterateLimit();
////                        while (notGamma.hasNextLimit()) {
////                            varIdx = notGamma.next();
//////                            if (sccEdge.isIntersect(varEdge[varIdx])) {
//////                                sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
//////                                System.out.println(sccEdge);
//////                                notGamma.addLimit();
//////                                // 把与匹配值相连的边并入
//////                                valIdx = matching[varIdx] - n;
//////                                sccEdge.setThenAnd(valEdge[valIdx], searchEdge);
//////                                System.out.println(sccEdge);
//////
//////                            }
////                        }
////
////                        varIdx = edgeIdx / numValue;
////                        sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
////                        if (vars[2].getValue() == 5) {
////                            System.out.println("----------" + edgeIdx + "----------");
////                            System.out.println(sccEdge);
////                        }
//                    } else { // 如果edge是非匹配边
////                        valIdx = edgeIdx % numValue;
////                        varIdx = valMatchedEdge[valIdx] / numValue;
////                        sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
////                        sccEdge.set(valMatchedEdge[valIdx]);
////                        notGamma.iterateLimit();
//////                        notGamma.addLimit();
////                        while (notGamma.hasNextLimit()) {
////                            varIdx = notGamma.next();
//////                            if (sccEdge.isIntersect(varEdge[varIdx])) {
////                                sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
////                                System.out.println(sccEdge);
////                                notGamma.addLimit();
////                                // 把与匹配值相连的边并入
////                                valIdx = matching[varIdx] - n;
////                                sccEdge.setThenAnd(valEdge[valIdx], searchEdge);
////                                System.out.println(sccEdge);
////
//////                            }
////                        }
////                        if (vars[2].getValue() == 5) {
////                            System.out.println("----------" + edgeIdx + "----------");
////                            System.out.println(sccEdge);
////                        }
////                        System.out.println("----------"+edgeIdx+"----------");
////                        System.out.println(searchEdge);
////                        sccEdge.set(edgeIdx);
////                        System.out.println(sccEdge);
////
////
////                        notGamma.iterateLimit();
////                        while (notGamma.hasNextLimit()) {
////                            varIdx = notGamma.next();
////                            if (sccEdge.isIntersect(varEdge[varIdx])) {
////                                sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
////                                System.out.println(sccEdge);
////                                notGamma.addLimit();
////                                // 把与匹配值相连的边并入
////                                valIdx = matching[varIdx] - n;
////                                sccEdge.setThenAnd(valEdge[valIdx], leftEdge);
////                                System.out.println(sccEdge);
////
////                            }
////                        }
//                    }
//
//                    // 回溯
//
//
////                    // 回溯路径，添加到leftEdge中
////                    int valNewIdx = edgeIdx % numValue;
////                    int tmpNewIdx = valNewIdx;
////                    do {
////                        int backEdgeIdx = valMatchedEdge[tmpNewIdx];
//////                        out.println(backEdgeIdx + " is in SCC");
////                        sccEdge.set(backEdgeIdx);
////                        varIdx = backEdgeIdx / numValue;
////                        backEdgeIdx = father[varIdx];
//////                        out.println(backEdgeIdx + " is in SCC");
////                        sccEdge.set(backEdgeIdx);
////                        tmpNewIdx = backEdgeIdx % numValue;
////                    } while (tmpNewIdx != valNewIdx);
//
//
//                } else {
//                    // 根据边索引得到对应的变量和取值
//                    varIdx = edgeIdx / numValue;
//                    v = vars[varIdx];
//                    k = idToVal.get(edgeIdx % numValue + n);
//                    if (matchedEdge.get(edgeIdx)) { // 如果edge是匹配边
//                        filter |= v.instantiateTo(k, aCause);
////                        out.println(v.getName() + " instantiate to " + k);
//                        // 从leftEdge中去掉被删的边
//                        leftEdge.clear(varEdge[varIdx]);
//                    } else { // 如果edge是非匹配边
//                        filter |= v.removeValue(k, aCause);
////                        out.println(v.getName() + " remove " + k);
//                        // 从leftEdge中去掉被删的边
//                        leftEdge.clear(edgeIdx);
//                    }
//                }
//            }
//            edgeIdx = leftEdge.nextSetBit(edgeIdx + 1);
//        }
//        return filter;
//    }
//
//    // 判断边是否在SCC中
//    private boolean checkSCC(int edgeIdx) {
//        // 先根据是否是匹配边初始化
//        int valIdx = edgeIdx % numValue;
//        int matchedEdgeIdx;
//        searchEdge.clear();
//        if (matchedEdge.get(edgeIdx)) { // 如果edge是匹配边
//            matchedEdgeIdx = edgeIdx;
//            searchEdge.or(valEdge[valIdx]);
//            searchEdge.clear(edgeIdx);
//        } else { // 如果edge是非匹配边
//            matchedEdgeIdx = valMatchedEdge[valIdx];
//            searchEdge.set(edgeIdx);
//        }
//
//        // 开始搜索
//        boolean extended;
//        notGamma.restore();
//        do {
//            extended = false;
//            // 头部扩展，匹配变量
//            notGamma.iterateValid();
//            while (notGamma.hasNextValid()) {
//                int varIdx = notGamma.next();
//                if (searchEdge.isIntersect(varEdge[varIdx])) {
//                    extended = true;
//                    searchEdge.set(varMatchedEdge[varIdx]);
//                    notGamma.remove();
//                    if (searchEdge.get(matchedEdgeIdx)) {
//                        return true;
//                    }
//                    // 把与匹配值相连的边并入
//                    valIdx = matching[varIdx] - n;
////                    searchEdge.or(valEdge[valIdx]);
////                    searchEdge.and(leftEdge);
//                    searchEdge.setThenAnd(valEdge[valIdx], leftEdge);
//                }
//            }
//        } while (extended);
//
//        return false;
//    }
//
//    private boolean filter() throws ContradictionException {
//        boolean filter = false;
//        distinguish();
//        filter |= filterFirstPart();
//        filter |= filterSecondPart();
////        out.println("after vars: ");
////        for (IntVar x : vars) {
////            System.out.println(x.toString());
////        }
//        TimeCount.filterTime += System.nanoTime() - TimeCount.startTime;
//        return filter;
//    }
}
