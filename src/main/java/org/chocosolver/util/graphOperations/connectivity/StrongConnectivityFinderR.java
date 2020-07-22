package org.chocosolver.util.graphOperations.connectivity;

import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISet;

import java.util.BitSet;
import java.util.Iterator;

public class StrongConnectivityFinderR {
    // input
    private DirectedGraph graph;
    private BitSet unvisited;
    private int n;

    //栈
    private int[] stack;
    private BitSet inStack;
    int stackIdx = 0;

    // 标记SCC
    private int nbSCC;
    private int[] nodeSCC;

    //
    private int maxDFS = 1;
    private int[] DFSNum;
    private int[] lowLink;
    private boolean hasSCCSplit = false;
//    private int index = 0;
//    private BitSet visited;


    public StrongConnectivityFinderR(DirectedGraph graph) {
        this.graph = graph;
        this.n = graph.getNbMaxNodes();

        stack = new int[n];
        inStack = new BitSet(n);

        nodeSCC = new int[n];
        nbSCC = 0;

        DFSNum = new int[n];
        lowLink = new int[n];

//        visited = new BitSet(n);
//        p = new int[n];
//        inf = new int[n];
//        nodeOfDfsNum = new int[n];
//        dfsNumOfNode = new int[n];
//        restriction = new BitSet(n);
//        sccFirstNode = new int[n];
//        nextNode = new int[n];
//        nodeSCC = new int[n];
//        nbSCC = 0;
//        //noinspection unchecked
//        iterator = new Iterator[n];
    }

    public void findAllSCC() {
        ISet nodes = graph.getNodes();
        for (int i = 0; i < n; i++) {
            unvisited.set(i, nodes.contains(i));
        }
        findAllSCCOf(unvisited);
    }

    public void findAllSCCOf(BitSet restriction) {
        // initialization
        clearStack();
        maxDFS = 1;
        nbSCC = 0;

        for (int i = 0; i < n; i++) {
            lowLink[i] = n + 2;
            nodeSCC[i] = -1;
            DFSNum[i] = -1;
        }

        findSingletons(restriction);
        int v = restriction.nextSetBit(0);
        while (v >= 0) {
            strongConnect(v);
            v = restriction.nextSetBit(v);
        }
    }

    void strongConnect(int curnode) {
        pushStack(curnode);
        DFSNum[curnode] = maxDFS;
        lowLink[curnode] = maxDFS;
        maxDFS++;
        unvisited.clear(curnode);

        Iterator<Integer> iterator = graph.getSuccOf(curnode).iterator();
        while (iterator.hasNext()) {
            int newnode = iterator.next();
            if (!unvisited.get(newnode)) {
                if (inStack.get(newnode)) {
                    lowLink[curnode] = Math.min(lowLink[curnode], DFSNum[newnode]);
                }
            } else {
                strongConnect(newnode);
                lowLink[curnode] = Math.min(lowLink[curnode], lowLink[newnode]);
            }
        }

        if (lowLink[curnode] == DFSNum[curnode]) {
            if (lowLink[curnode] > 1 || inStack.cardinality() > 0) {
                hasSCCSplit = true;
            }
            if (hasSCCSplit) {
                int stacknode = -1;

                while (stacknode != curnode) {
                    stacknode = popStack();
                    nodeSCC[stacknode] = nbSCC;
                }
                nbSCC++;
            }
        }


    }

    private void findSingletons(BitSet restriction) {
        ISet nodes = graph.getNodes();
        for (int i = restriction.nextSetBit(0); i >= 0; i = restriction.nextSetBit(i + 1)) {
            if (nodes.contains(i) && graph.getPredOf(i).size() * graph.getSuccOf(i).size() == 0) {
                nodeSCC[i] = nbSCC;
                restriction.clear(i);
            }
        }
    }

    void pushStack(int v) {
        stack[stackIdx++] = v;
        inStack.set(v);
    }

    void clearStack() {
        inStack.clear();
        stackIdx = 0;
    }

    int popStack() {
        int x = stack[--stackIdx];
        inStack.clear(x);
        return x;
    }

    public int[] getNodesSCC() {
        return nodeSCC;
    }

//    boolean inStack()

}
