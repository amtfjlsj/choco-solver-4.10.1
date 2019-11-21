package org.chocosolver.solver.constraints.nary.alldifferent.algo;

import amtf.TimeCount;
import gnu.trove.map.hash.TIntIntHashMap;
import org.chocosolver.solver.ICause;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.SparseSet;
import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISetIterator;
import org.chocosolver.util.objects.setDataStructures.SetType;

import java.util.BitSet;

import static java.lang.System.out;

/**
 * Algorithm of Alldifferent with AC
 * <p>
 * Uses Zhang algorithm in the paper of IJCAI-18
 * "A Fast Algorithm for Generalized Arc Consistency of the Alldifferent Constraint"
 * <p>
 * We try to use the bit to speed up.
 *
 * @author Jean-Guillaume Fages, Zhe Li, Jia'nan Chen
 */
public class AlgoAllDiffAC_Fastbit1 {

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
    private int numValue, numBit;
    // 需要新增一个取值编号到取值的映射，也是一对一
    private TIntIntHashMap idToVal;
    // 总图
    // 图中现存的所有边
    private BitSet existentEdge;
    // 匹配边
    private BitSet matchedEdge;

    // 右部图
    // 允许边，从自由点出发的交替路，Γ(A)和A之间的边
    private BitSet allowedEdge;

    // 左部图
    // leftEdge是Xc-Γ(A)和Dc-A之间的边
    private BitSet leftEdge;
    // 需要检查强连通的边，leftEdge去掉matchedEdge
    private BitSet checkEdge;
    // 搜索强连通的边
    private BitSet searchEdge;

    // 临时边
    private BitSet tmp;

    // 变量、值的匹配边和非匹配边
    private int[] varMatchedEdge;
    private int[] valMatchedEdge;
    private BitSet[] varUnmatchedEdge;
    private BitSet[] valUnmatchedEdge;

    // Xc-Γ(A)
    private SparseSet notGamma;
    // Dc-A
    private SparseSet notA;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    public AlgoAllDiffAC_Fastbit1(IntVar[] variables, ICause cause) {
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
        numBit = n * numValue;
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
        existentEdge = new BitSet(numBit);
        matchedEdge = new BitSet(numBit);

        allowedEdge = new BitSet(numBit);

        leftEdge = new BitSet(numBit);
        checkEdge = new BitSet(numBit);
        searchEdge = new BitSet(numBit);

        tmp = new BitSet(numBit);

        varMatchedEdge = new int[n];
        valMatchedEdge = new int[numValue];
        varUnmatchedEdge = new BitSet[n];
        valUnmatchedEdge = new BitSet[numValue];

        for (int i = 0; i < n; ++i) {
            varUnmatchedEdge[i] = new BitSet(numBit);
        }
        for (int i = 0; i < numValue; ++i) {
            valUnmatchedEdge[i] = new BitSet(numBit);
        }

        notGamma = new SparseSet(n);
        notA = new SparseSet(numValue);
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
        findMaximumMatching();
        TimeCount.matchingTime += System.nanoTime() - TimeCount.startTime;

        TimeCount.startTime = System.nanoTime();
        return filter();
    }

    //***********************************************************************************
    // Initialization
    //***********************************************************************************

    private void findMaximumMatching() throws ContradictionException {
        // 每次都重新建图
        for (int i = 0; i < n2; i++) {
            digraph.getSuccOf(i).clear();
            digraph.getPredOf(i).clear();
        }
        free.set(0, n2);

        existentEdge.clear();
        matchedEdge.clear();
        // 每次调用需要初始化varUnmatchedEdge valUnmatchedEdge
        for (int i = 0; i < n; ++i) {
            varUnmatchedEdge[i].clear();
        }
        for (int i = 0; i < numValue; ++i) {
            valUnmatchedEdge[i].clear();
        }
        // 初始化两个not集合
        notGamma.fill();
        notA.fill();

        int k, ub;
        IntVar v;
        for (int i = 0; i < n; i++) {
            v = vars[i];
            ub = v.getUB();
            int mate = matching[i];
            for (k = v.getLB(); k <= ub; k = v.nextValue(k)) {
                int j = map.get(k);
                // 利用之前已经找到的匹配
                if (mate == j) {
                    assert free.get(i) && free.get(j);
                    digraph.addArc(j, i);
                    free.clear(i);
                    free.clear(j);
                } else {
                    digraph.addArc(i, j);
                }
                // 初始化existentEdge、varUnmatchedEdge、valUnmatchedEdge
                // Idx是二部图值和边的索引
                int valIdx = j - n; // 因为构造函数中建立map时是从n开始的，所以这里需要减去n
                int edgeIdx = i * numValue + valIdx;
                existentEdge.set(edgeIdx);
                varUnmatchedEdge[i].set(edgeIdx);
                valUnmatchedEdge[valIdx].set(edgeIdx);
            }
        }
        // 尝试为每个变量都寻找一个匹配，即最大匹配的个数要与变量个数相等，否则回溯
        // 利用匈牙利算法寻找最大匹配
        for (int i = free.nextSetBit(0); i >= 0 && i < n; i = free.nextSetBit(i + 1)) {
            tryToMatch(i);
        }
        // 匹配边是由值指向变量，非匹配边是由变量指向值
//        // out.println("-----matching-----");
        for (int i = 0; i < n; i++) {
            matching[i] = digraph.getPredOf(i).isEmpty() ? -1 : digraph.getPredOf(i).iterator().next();
            // 初始化matchedEdge、varMatchedEdge、valMatchedEdge，调整varUnmatchedEdge、valUnmatchedEdge
            int valIdx = matching[i] - n; // 因为构造函数中建立map时是从n开始的，所以这里需要减去n
//            // out.println(i + " matching " + valIdx);
            int edgeIdx = i * numValue + valIdx;
            matchedEdge.set(edgeIdx);
            varMatchedEdge[i] = edgeIdx;
            valMatchedEdge[valIdx] = edgeIdx;
            varUnmatchedEdge[i].clear(edgeIdx);
            valUnmatchedEdge[valIdx].clear(edgeIdx);
        }
//        // out.println("-----existentEdge-----");
//        // out.println(existentEdge.toString());
//        // out.println("-----matchedEdge-----");
//        // out.println(matchedEdge.toString());
//        // out.println("---varMatchedEdge---");
//        for (int a : varMatchedEdge) {
//            // out.println(a);
//        }
//        // out.println("---valMatchedEdge---");
//        for (int a : valMatchedEdge) {
//            // out.println(a);
//        }
//        // out.println("---varUnmatchedEdge---");
//        for (BitSet a : varUnmatchedEdge) {
//            // out.println(a.toString());
//        }
//        // out.println("---valUnmatchedEdge---");
//        for (BitSet a : valUnmatchedEdge) {
//            // out.println(a.toString());
//        }
    }

    private void tryToMatch(int i) throws ContradictionException {
        int mate = augmentPath_BFS(i);
        if (mate != -1) {// 值mate是一个自由点
            free.clear(mate);
            free.clear(i);
            int tmp = mate;
            // 沿着father回溯即是增广路径
            while (tmp != i) {
                // 翻转边的方向
                digraph.removeArc(father[tmp], tmp);
                digraph.addArc(tmp, father[tmp]);
                // 回溯
                tmp = father[tmp];
            }
        } else {//应该是匹配失败，即最大匹配个数与变量个数不相等，需要回溯
            vars[0].instantiateTo(vars[0].getLB() - 1, aCause);
        }
    }

    // 宽度优先搜索寻找增广路径
    private int augmentPath_BFS(int root) {
        // root是一个自由点（变量）。
        // 如果与root相连的值中有自由点，就返回第一个自由点；
        // 如果没有，尝试为匹配变量找一个新的自由点，过程中通过father标记增广路径。
        in.clear();
        int indexFirst = 0, indexLast = 0;
        fifo[indexLast++] = root;
        int x;
        ISetIterator succs;
        while (indexFirst != indexLast) {
            x = fifo[indexFirst++];
            // 如果x是一个变量，那么它的后继就是非匹配的值；
            // 如果x是一个值，那么它的后继只有一个，是与它匹配的变量。
            succs = digraph.getSuccOf(x).iterator();
            while (succs.hasNext()) {
                int y = succs.nextInt();
                if (!in.get(y)) {
                    father[y] = x;
                    fifo[indexLast++] = y;
                    in.set(y);
                    if (free.get(y)) { //自由点（值）
                        return y;
                    }
                }
            }
        }
        return -1;
    }

    //***********************************************************************************
    // PRUNING
    //***********************************************************************************

    //  新函数从自由点出发，寻找交替路，区分论文中的四个集合
    private void distinguish() {
        allowedEdge.clear();
        // 寻找从自由值出发的所有交替路
        // 首先将与自由值相连的边并入允许边
        for (int i = free.nextSetBit(n); i >= n && i < n2; i = free.nextSetBit(i + 1)) {
            int valIdx = i - n; // 因为构造函数中建立map时是从n开始的，所以这里需要减去n
            notA.remove(valIdx);
            allowedEdge.or(valUnmatchedEdge[valIdx]);
        }
        // 然后看是否能继续扩展
        boolean extended;
        do {
            extended = false;
            notGamma.iterateValid();
            while (notGamma.hasNextValid()) {
                int varIdx = notGamma.next();
                tmp.clear();
                tmp.or(allowedEdge);
                tmp.and(varUnmatchedEdge[varIdx]);
                if (!tmp.isEmpty()) {
                    extended = true;
                    allowedEdge.set(varMatchedEdge[varIdx]);
                    notGamma.remove();
                    // 把与匹配值相连的边并入
                    int valIdx = matching[varIdx] - n;
                    allowedEdge.or(valUnmatchedEdge[valIdx]);
                    notA.remove(valIdx);
                }
            }
        } while (extended);

        // out.println("-----notGamma-----");
        // out.println(notGamma.toString());
        // out.println("-----notA-----");
        // out.println(notA.toString());
        // out.println("-----allowedEdge-----");
        // out.println(allowedEdge.toString());
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
                existentEdge.clear(varIdx * numValue + valIdx);
            }
        }
        return filter;
    }

    // 寻找第二种类型的冗余边
    private boolean filterSecondPart() throws ContradictionException {
        boolean filter = false;
        int varIdx;
        IntVar v;
        int k;
        int edgeIdx;

        // 从existentEdge中去掉allowedEdge和跨界边后，即是leftEdge
        // 当前redundantEdge存的就是跨界边
        leftEdge.clear();
        leftEdge.or(allowedEdge);
        leftEdge.flip(0, numBit);
        leftEdge.and(existentEdge);
//        out.println("-----leftEdge-----");
//        out.println(leftEdge.toString());

        // out.println("-----SCC-----");
        // 记录当前limit
        notGamma.record();

        // -------------------先检查匹配边-------------------
        // 从leftEdge和matchedEdge共有的边，即是需要检查的匹配边
//        checkEdge.clear();
//        checkEdge.or(matchedEdge);
//        checkEdge.and(leftEdge);
////        out.println("-----matchedEdge-----");
////        out.println(matchedEdge.toString());
////        out.println("-----checkMatchedEdge-----");
////        out.println(checkEdge.toString());
//        edgeIdx = checkEdge.nextSetBit(0);
//        // 论域大小大于1的变量才需要检查
//        while (edgeIdx != -1 && vars[edgeIdx / numValue].getDomainSize() > 1) {
//            if (checkSCC(edgeIdx, true)) {
//                // 进一步的还可以回溯路径，从checkEdge中删除
////                //out.println(edgeIdx + " is in SCC");
//            } else {
//                // 根据边索引得到对应的变量和取值
//                varIdx = edgeIdx / numValue;
//                v = vars[varIdx];
//                k = idToVal.get(matching[varIdx]);
//                filter |= v.instantiateTo(k, aCause);
////                out.println(v.getName() + " instantiate to " + k);
//                // 从leftEdge中去掉被删的边
//                tmp.clear();
//                tmp.or(varUnmatchedEdge[varIdx]);
//                tmp.flip(0, numBit);
//                leftEdge.and(tmp);
//            }
//            edgeIdx = checkEdge.nextSetBit(edgeIdx + 1);
//        }

        // -------------------后检查非匹配边-------------------
        // 从leftEdge中去掉matchedEdge，即是需要检查的非匹配边
        checkEdge.clear();
        checkEdge.or(matchedEdge);
        checkEdge.flip(0, numBit);
        checkEdge.and(leftEdge);
//        out.println("-----checkUnmatchedEdge-----");
//        out.println(checkEdge.toString());
        edgeIdx = checkEdge.nextSetBit(0);
        while (edgeIdx != -1) {
            if (checkSCC(edgeIdx, false)) {
                // 进一步的还可以回溯路径，从checkEdge中删除
//                //out.println(edgeIdx + " is in SCC");
            } else {
                // 根据边索引得到对应的变量和取值
                varIdx = edgeIdx / numValue;
                v = vars[varIdx];
                k = idToVal.get(edgeIdx % numValue + n);
                filter |= v.removeValue(k, aCause);
//                out.println(v.getName() + " remove " + k);
            }
            edgeIdx = checkEdge.nextSetBit(edgeIdx + 1);
        }
        return filter;
    }

    // 判断边是否在SCC中
    private boolean checkSCC(int edgeIdx, boolean matched) {
        // 先根据是否是匹配边初始化
        int valIdx = edgeIdx % numValue;
        int matchedEdgeIdx;
        searchEdge.clear();
        if (matched) { // 如果edge是匹配边
            matchedEdgeIdx = edgeIdx;
            searchEdge.or(valUnmatchedEdge[valIdx]);
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
                tmp.clear();
                tmp.or(searchEdge);
                tmp.and(varUnmatchedEdge[varIdx]);
                if (!tmp.isEmpty()) {
                    extended = true;
                    searchEdge.set(varMatchedEdge[varIdx]);
                    notGamma.remove();
                    if (searchEdge.get(matchedEdgeIdx)) {
                        return true;
                    }
                    // 把与匹配值相连的边并入
                    valIdx = matching[varIdx] - n;
                    searchEdge.or(valUnmatchedEdge[valIdx]);
                    searchEdge.and(leftEdge);
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
