package org.chocosolver.solver.constraints.nary.alldifferent.algo;

import amtf.TimeCount;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.NaiveSparseBitSet;
import org.chocosolver.util.objects.NaiveBitSet;
//import org.chocosolver.util.objects.NaiveSparseBitSet;
import org.chocosolver.util.objects.SparseSet;
import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;
import org.chocosolver.util.objects.setDataStructures.SetType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

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

    private int n, n2;
    private IntVar[] vars;
    private ICause aCause;
    // 原map是取值到取值编号的映射，一对一
    private TIntIntHashMap map;
    private DirectedGraph digraph;
    private int[] matching;
    private BitSet free;
    // for augmenting matching (BFS)
    private int[] father;
    private int[] fifo;
    private BitSet in;

    // 以下是bit版本所需数据结构========================
    // numValue是二部图中取值编号的个数，numBit是二部图的最大边数
    private int numValue;
    // 需要新增一个取值编号到取值的映射，也是一对一
    private TIntIntHashMap idToVal;

    // 保留边
    private NaiveBitSet leftEdge;
    // 连通边
    private NaiveBitSet sccEdge;
    // 匹配边
    private NaiveBitSet matchedEdge;
    // 搜索边
    private NaiveBitSet searchEdge;
    // 前沿边
    private NaiveBitSet frontierEdge;

    // 变量、值的匹配边和非匹配边
    private int[] varMatchedEdge;
    private int[] valMatchedEdge;
    private NaiveSparseBitSet[] varEdge;
    private NaiveSparseBitSet[] valEdge;

    // Xc-Γ(A)
    private SparseSet notGamma;
    // Dc-A
    private SparseSet notA;


    private ArrayList<Integer>[] successor_;
    private boolean[] variable_visited_;
    private boolean[] value_visited_;
    private int[] value_to_variable_;
    private int[] variable_to_value_;
    private int[] prev_matching_;
    private int[] visiting_;
    private int[] variable_visited_from_;


    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************
    public AlgoAllDiffAC_Naive2(IntVar[] variables, ICause cause) {
        this.vars = variables;
        aCause = cause;
        n = vars.length;
        // 存储匹配
        matching = new int[n];
        for (int i = 0; i < n; i++) {
            matching[i] = -1;
        }
        map = new TIntIntHashMap();
        idToVal = new TIntIntHashMap();
        IntVar v;
        int ub;
        int idx = n;

        // 统计所有变量论域中不同值的个数
        for (int i = 0; i < n; i++) {
            v = vars[i];
            ub = v.getUB();
            for (int j = v.getLB(); j <= ub; j = v.nextValue(j)) {
                if (!map.containsKey(j)) {
                    map.put(j, idx);
                    idToVal.put(idx, j);
                    idx++;
                }
            }
        }

        n2 = idx;
        numValue = n2 - n;
        int numBit = n * numValue;
        // 用Bitset邻接矩阵的有向图，因为没有辅助点，所以是n2，非n2 + 1
        digraph = new DirectedGraph(n2, SetType.BITSET, false);
        // free应该区分匹配点和非匹配点（true表示非匹配点，false表示匹配点）
        free = new BitSet(n2);
        // 用于回溯增广路径
        father = new int[n2];
        // 使用队列实现非递归广度优先搜索
        fifo = new int[n2];
        // 哪些点在fifo队列中（true表示在，false表示不在）
        in = new BitSet(n2);

        // 构造新增数据结构
        leftEdge = new NaiveBitSet(numBit);
        sccEdge = new NaiveBitSet(numBit);
        matchedEdge = new NaiveBitSet(numBit);
        searchEdge = new NaiveBitSet(numBit);

        varMatchedEdge = new int[n];
        valMatchedEdge = new int[numValue];
        varEdge = new NaiveSparseBitSet[n];
        valEdge = new NaiveSparseBitSet[numValue];


        for (int i = 0; i < n; ++i) {
            varEdge[i] = new NaiveSparseBitSet();
        }
        for (int i = 0; i < numValue; ++i) {
            valEdge[i] = new NaiveSparseBitSet();
        }

        // 只在构造函数中，初始化varUnmatchedEdge、valEdge
        for (int i = 0; i < n; i++) {
            v = vars[i];
            ub = v.getUB();
            for (int k = v.getLB(); k <= ub; k = v.nextValue(k)) {
                int j = map.get(k);
                // Idx是二部图变量、值和边的索引
                int valIdx = j - n; // 因为建立map时是从n开始的，所以这里需要减去n
                int edgeIdx = i * numValue + valIdx;
                varEdge[i].addIndex(edgeIdx);
                valEdge[valIdx].addIndex(edgeIdx);
            }
        }

        for (int i = 0; i < n; ++i) {
            varEdge[i].complete();
        }
        for (int i = 0; i < numValue; ++i) {
            valEdge[i].complete();
        }

        notGamma = new SparseSet(n);
        notA = new SparseSet(numValue);


        visiting_ = new int[n];
        variable_visited_ = new boolean[n];
        variable_visited_from_ = new int[n];
        successor_ = new ArrayList[n];

        for (int i = 0; i < n; ++i) {
            successor_[i] = new ArrayList<>();
        }
        value_visited_ = new boolean[numValue];
        variable_to_value_ = new int[n];
        prev_matching_ = new int[n];
        value_to_variable_ = new int[numValue];
//        prev_matching_ =new int[];
    }

    boolean MakeAugmentingPath(int start) {
        // Do a BFS and use visiting_ as a queue, with num_visited pointing
        // at its begin() and num_to_visit its end().
        // To switch to the augmenting path once a nonmatched value was found,
        // we remember the BFS tree in variable_visited_from_.
        int num_to_visit = 0;
        int num_visited = 0;
        // Enqueue start.
        visiting_[num_to_visit++] = start;
        variable_visited_[start] = true;
        variable_visited_from_[start] = -1;

        while (num_visited < num_to_visit) {
            // Dequeue node to visit.
            int node = visiting_[num_visited++];

            for (int value : successor_[node]) {
                if (value_visited_[value]) continue;
                value_visited_[value] = true;
                if (value_to_variable_[value] == -1) {
                    // value is not matched: change path from node to start, and return.
                    int path_node = node;
                    int path_value = value;
                    while (path_node != -1) {
                        int old_value = variable_to_value_[path_node];
                        variable_to_value_[path_node] = path_value;
                        value_to_variable_[path_value] = path_node;
                        path_node = variable_visited_from_[path_node];
                        path_value = old_value;
                    }
                    return true;
                } else {
                    // Enqueue node matched to value.
                    int next_node = value_to_variable_[value];
                    variable_visited_[next_node] = true;
                    visiting_[num_to_visit++] = next_node;
                    variable_visited_from_[next_node] = node;
                    free.clear(value);
                }
            }
        }

        return false;
    }

    //***********************************************************************************
    // PROPAGATION
    //***********************************************************************************

    public boolean propagate() throws ContradictionException {
//        out.println("before vars: ");
//        for (IntVar v : vars) {
//            out.println(v.toString());
//        }
        TimeCount.startTime = System.nanoTime();
//        findMaximumMatching();
//        System.out.println(Arrays.toString(matching));

        IntVar v;
        for (int i = 0; i < n; ++i) {
            prev_matching_[i] = variable_to_value_[i];
            variable_to_value_[i] = -1;
        }

        for (int i = 0; i < numValue; ++i) {
            value_to_variable_[i] = -1;
        }

        for (int x = 0; x < n; x++) {
            successor_[x].clear();
            v = vars[x];
//            int min_value = v.getLB();
//            int max_value = v.getUB();
//            for (int value = min_value; value <= max_value; value++) {
            for (int value = v.getLB(), ub = v.getUB(); value <= ub; value = v.nextValue(value)) {
                int offset_value = map.get(value) - n;
                // Forward-checking should propagate xsu != value.
                successor_[x].add(offset_value);

                int edgeIdx = x * numValue + offset_value;
                matchedEdge.set(edgeIdx);
            }

            if (successor_[x].size() == 1) {
                int offset_value = successor_[x].get(0);
                if (value_to_variable_[offset_value] == -1) {
                    value_to_variable_[offset_value] = x;
                    variable_to_value_[x] = offset_value;
                }
            }
        }

        // Seed with previous matching.
        for (int x = 0; x < n; x++) {
            if (variable_to_value_[x] != -1) continue;

            // 先前匹配
            int prev_value = prev_matching_[x];
            if (prev_value == -1 || value_to_variable_[prev_value] != -1) continue;
            v = vars[x];
//            if (VariableHasPossibleValue(x, prev_matching_[x] + min_all_values_)) {
            if (v.contains(idToVal.get(prev_matching_[x]))) {
                variable_to_value_[x] = prev_matching_[x];
                value_to_variable_[prev_matching_[x]] = x;
            }
        }

        // Compute max matching.

        for (int x = 0; x < n; x++) {
            if (variable_to_value_[x] == -1) {

                for (int i = 0; i < numValue; ++i) {
                    value_visited_[i] = false;
                }

                for (int i = 0; i < n; ++i) {
                    variable_visited_[i] = false;
                }

                MakeAugmentingPath(x);
            }
            if (variable_to_value_[x] == -1) break;  // No augmenting path exists.
        }

        System.out.println(Arrays.toString(variable_to_value_));
        System.out.println(Arrays.toString(value_to_variable_));

        //      out.println("-----matching-----");
        for (int i = 0; i < n; i++) {
//            matching[i] = digraph.getPredOf(i).isEmpty() ? -1 : digraph.getPredOf(i).iterator().next();
            // 初始化matchedEdge、varMatchedEdge、valMatchedEdge
            matching[i] = variable_to_value_[i] + n;
            int valIdx = matching[i] - n; // 因为构造函数中建立map时是从n开始的，所以这里需要减去n
//          out.println(i + " matching " + valIdx);
            int edgeIdx = i * numValue + valIdx;
            matchedEdge.set(edgeIdx);
            varMatchedEdge[i] = edgeIdx;
            valMatchedEdge[valIdx] = edgeIdx;
        }


        TimeCount.matchingTime += System.nanoTime() - TimeCount.startTime;

        TimeCount.startTime = System.nanoTime();

        System.out.println("-----leftEdge-----");
        System.out.println(leftEdge.toString());
        System.out.println("-----matchedEdge-----");
        System.out.println(matchedEdge.toString());
        System.out.println("---varMatchedEdge---");
        for (int a : varMatchedEdge) {
            System.out.println(a);
        }
        System.out.println("---valMatchedEdge---");
        for (int a : valMatchedEdge) {
            System.out.println(a);
        }
        System.out.println("---varEdge---");
        for (NaiveSparseBitSet a : varEdge) {
            System.out.println(a.toString());
        }
        System.out.println("---valEdge---");
        for (NaiveSparseBitSet a : valEdge) {
            System.out.println(a.toString());
        }

        return filter();

    }

    //***********************************************************************************
    // Initialization
    //***********************************************************************************


    //***********************************************************************************
    // PRUNING
    //***********************************************************************************

    //  新函数从自由点出发，寻找交替路，区分论文中的四个集合
    private void distinguish() {
        searchEdge.clear();
        // 寻找从自由值出发的所有交替路
        // 首先将与自由值相连的边并入允许边
        for (int i = free.nextSetBit(n); i >= n && i < n2; i = free.nextSetBit(i + 1)) {
            int valIdx = i - n; // 因为构造函数中建立map时是从n开始的，所以这里需要减去n
            notA.remove(valIdx);
            searchEdge.or(valEdge[valIdx]);
        }
        searchEdge.and(leftEdge);
        // 然后看是否能继续扩展
        boolean extended;
        do {
            extended = false;
            notGamma.iterateValid();
            while (notGamma.hasNextValid()) {
                int varIdx = notGamma.next();
                if (searchEdge.isIntersect(varEdge[varIdx])) {
                    extended = true;
                    searchEdge.set(varMatchedEdge[varIdx]);
                    notGamma.remove();
                    // 把与匹配值相连的边并入
                    int valIdx = matching[varIdx] - n;
//                    searchEdge.or(valEdge[valIdx]);
//                    searchEdge.and(leftEdge);
                    searchEdge.setThenAnd(valEdge[valIdx], leftEdge);
                    notA.remove(valIdx);
                }
            }
        } while (extended);

        // out.println("-----notGamma-----");
        // out.println(notGamma.toString());
        // out.println("-----notA-----");
        // out.println(notA.toString());
//        out.println("-----searchEdge-----");
//        out.println(searchEdge.toString());
    }

    // 过滤第一种类型的冗余边
    private boolean filterFirstPart() throws ContradictionException {
        boolean filter = false;
        int varIdx, valIdx;
        IntVar v;
        int k;
        notA.iterateValid();
        while (notA.hasNextValid()) {
            valIdx = notA.next();
            notGamma.iterateInvalid();
            while (notGamma.hasNextInvalid()) {
                varIdx = notGamma.next();
                v = vars[varIdx];
                k = idToVal.get(valIdx + n);
                filter |= v.removeValue(k, aCause);
                leftEdge.clear(varIdx * numValue + valIdx);
            }
        }
        return filter;
    }

    // 寻找第二种类型的冗余边
    private boolean filterSecondPart() throws ContradictionException {
        boolean filter = false;
        int varIdx, valIdx;
        IntVar v;
        int k;
        int edgeIdx;

        // 从leftEdge中去掉searchEdge，即是需要检查SCC的边
        leftEdge.clear(searchEdge);
//        out.println("-----leftEdge-----");
//        out.println(leftEdge.toString());

        // out.println("-----SCC-----");
        // 记录当前limit
        notGamma.record();
        sccEdge.clear();

        // -------------------放在一起检查-------------------
        edgeIdx = leftEdge.nextSetBit(0);
        while (edgeIdx != -1) {
            if (vars[edgeIdx / numValue].getDomainSize() > 1 && !sccEdge.get(edgeIdx)) {
                if (checkSCC(edgeIdx)) {
                    // 进一步的还可以回溯路径，从checkEdge中删除
//                //out.println(edgeIdx + " is in SCC");
//                    v = vars[varIdx];
////                    k = idToVal.get(edgeIdx % numValue + n);
                    if (matchedEdge.get(edgeIdx)) { // 如果edge是匹配边
//                        filter |= v.instantiateTo(k, aCause);
////                        out.println(v.getName() + " instantiate to " + k);
//                        // 从leftEdge中去掉被删的边
//                        leftEdge.clear(varEdge[varIdx]);
//                        varEdge[varIdx]
//                        System.out.println("----------" + edgeIdx + "----------");
//                        System.out.println(searchEdge);
//                        sccEdge.set(edgeIdx);
//                        System.out.println(sccEdge);
//                        notGamma.iterateLimit();
//                        while (notGamma.hasNextLimit()) {
//                            varIdx = notGamma.next();
////                            if (sccEdge.isIntersect(varEdge[varIdx])) {
////                                sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
////                                System.out.println(sccEdge);
////                                notGamma.addLimit();
////                                // 把与匹配值相连的边并入
////                                valIdx = matching[varIdx] - n;
////                                sccEdge.setThenAnd(valEdge[valIdx], searchEdge);
////                                System.out.println(sccEdge);
////
////                            }
//                        }
//
//                        varIdx = edgeIdx / numValue;
//                        sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
//                        if (vars[2].getValue() == 5) {
//                            System.out.println("----------" + edgeIdx + "----------");
//                            System.out.println(sccEdge);
//                        }
                    } else { // 如果edge是非匹配边
//                        valIdx = edgeIdx % numValue;
//                        varIdx = valMatchedEdge[valIdx] / numValue;
//                        sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
//                        sccEdge.set(valMatchedEdge[valIdx]);
//                        notGamma.iterateLimit();
////                        notGamma.addLimit();
//                        while (notGamma.hasNextLimit()) {
//                            varIdx = notGamma.next();
////                            if (sccEdge.isIntersect(varEdge[varIdx])) {
//                                sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
//                                System.out.println(sccEdge);
//                                notGamma.addLimit();
//                                // 把与匹配值相连的边并入
//                                valIdx = matching[varIdx] - n;
//                                sccEdge.setThenAnd(valEdge[valIdx], searchEdge);
//                                System.out.println(sccEdge);
//
////                            }
//                        }
//                        if (vars[2].getValue() == 5) {
//                            System.out.println("----------" + edgeIdx + "----------");
//                            System.out.println(sccEdge);
//                        }
//                        System.out.println("----------"+edgeIdx+"----------");
//                        System.out.println(searchEdge);
//                        sccEdge.set(edgeIdx);
//                        System.out.println(sccEdge);
//
//
//                        notGamma.iterateLimit();
//                        while (notGamma.hasNextLimit()) {
//                            varIdx = notGamma.next();
//                            if (sccEdge.isIntersect(varEdge[varIdx])) {
//                                sccEdge.setThenAnd(varEdge[varIdx], searchEdge);
//                                System.out.println(sccEdge);
//                                notGamma.addLimit();
//                                // 把与匹配值相连的边并入
//                                valIdx = matching[varIdx] - n;
//                                sccEdge.setThenAnd(valEdge[valIdx], leftEdge);
//                                System.out.println(sccEdge);
//
//                            }
//                        }
                    }

                    // 回溯


//                    // 回溯路径，添加到leftEdge中
//                    int valNewIdx = edgeIdx % numValue;
//                    int tmpNewIdx = valNewIdx;
//                    do {
//                        int backEdgeIdx = valMatchedEdge[tmpNewIdx];
////                        out.println(backEdgeIdx + " is in SCC");
//                        sccEdge.set(backEdgeIdx);
//                        varIdx = backEdgeIdx / numValue;
//                        backEdgeIdx = father[varIdx];
////                        out.println(backEdgeIdx + " is in SCC");
//                        sccEdge.set(backEdgeIdx);
//                        tmpNewIdx = backEdgeIdx % numValue;
//                    } while (tmpNewIdx != valNewIdx);


                } else {
                    // 根据边索引得到对应的变量和取值
                    varIdx = edgeIdx / numValue;
                    v = vars[varIdx];
                    k = idToVal.get(edgeIdx % numValue + n);
                    if (matchedEdge.get(edgeIdx)) { // 如果edge是匹配边
                        filter |= v.instantiateTo(k, aCause);
//                        out.println(v.getName() + " instantiate to " + k);
                        // 从leftEdge中去掉被删的边
                        leftEdge.clear(varEdge[varIdx]);
                    } else { // 如果edge是非匹配边
                        filter |= v.removeValue(k, aCause);
//                        out.println(v.getName() + " remove " + k);
                        // 从leftEdge中去掉被删的边
                        leftEdge.clear(edgeIdx);
                    }
                }
            }
            edgeIdx = leftEdge.nextSetBit(edgeIdx + 1);
        }
        return filter;
    }

    // 判断边是否在SCC中
    private boolean checkSCC(int edgeIdx) {
        // 先根据是否是匹配边初始化
        int valIdx = edgeIdx % numValue;
        int matchedEdgeIdx;
        searchEdge.clear();
        if (matchedEdge.get(edgeIdx)) { // 如果edge是匹配边
            matchedEdgeIdx = edgeIdx;
            searchEdge.or(valEdge[valIdx]);
            searchEdge.clear(edgeIdx);
        } else { // 如果edge是非匹配边
            matchedEdgeIdx = valMatchedEdge[valIdx];
            searchEdge.set(edgeIdx);
        }

        // 开始搜索
        boolean extended;
        notGamma.restore();
        do {
            extended = false;
            // 头部扩展，匹配变量
            notGamma.iterateValid();
            while (notGamma.hasNextValid()) {
                int varIdx = notGamma.next();
                if (searchEdge.isIntersect(varEdge[varIdx])) {
                    extended = true;
                    searchEdge.set(varMatchedEdge[varIdx]);
                    notGamma.remove();
                    if (searchEdge.get(matchedEdgeIdx)) {
                        return true;
                    }
                    // 把与匹配值相连的边并入
                    valIdx = matching[varIdx] - n;
//                    searchEdge.or(valEdge[valIdx]);
//                    searchEdge.and(leftEdge);
                    searchEdge.setThenAnd(valEdge[valIdx], leftEdge);
                }
            }
        } while (extended);

        return false;
    }

    private boolean filter() throws ContradictionException {
        boolean filter = false;
        distinguish();
        filter |= filterFirstPart();
        filter |= filterSecondPart();
//        out.println("after vars: ");
//        for (IntVar x : vars) {
//            System.out.println(x.toString());
//        }
        TimeCount.filterTime += System.nanoTime() - TimeCount.startTime;
        return filter;
    }
}
