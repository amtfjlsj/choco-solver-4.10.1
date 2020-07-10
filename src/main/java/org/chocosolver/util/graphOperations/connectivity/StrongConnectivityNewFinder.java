/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2020, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.util.graphOperations.connectivity;

import org.chocosolver.solver.search.strategy.selectors.variables.Cyclic;
import org.chocosolver.util.objects.IntTuple2;
import org.chocosolver.util.objects.graphs.DirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;

public class StrongConnectivityNewFinder {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    // input
    private DirectedGraph graph;
    private BitSet restriction;
    private int n;
    // output
    private int[] sccFirstNode, nextNode, nodeSCC;
    private int nbSCC;

    // util
    private int[] stack, p, inf, nodeOfDfsNum, dfsNumOfNode;
    private Iterator<Integer>[] iterator;
    private BitSet inStack;


    // for early detection
    // 由构造函数传入

    // deletedEdge
    // DE存的是边，而cycles存的是nbSCC
    private ArrayList<IntTuple2> DE, cycles;
    private boolean unconnected = false;


    //***********************************************************************************
    // CONSTRUCTOR
    //***********************************************************************************

    public StrongConnectivityNewFinder(DirectedGraph graph, ArrayList<IntTuple2> deletedEdges) {
        this.graph = graph;
        this.n = graph.getNbMaxNodes();
        //
        stack = new int[n];
        p = new int[n];
        inf = new int[n];
        nodeOfDfsNum = new int[n];
        dfsNumOfNode = new int[n];
        inStack = new BitSet(n);
        restriction = new BitSet(n);
        sccFirstNode = new int[n];
        nextNode = new int[n];
        nodeSCC = new int[n];
        nbSCC = 0;
        //noinspection unchecked
        iterator = new Iterator[n];

        //for early detection
        DE = deletedEdges;
        cycles = new ArrayList<>();
    }

    //***********************************************************************************
    // ALGORITHM
    //***********************************************************************************

    public void findAllSCC() {
        ISet nodes = graph.getNodes();
        for (int i = 0; i < n; i++) {
            restriction.set(i, nodes.contains(i));
        }
        findAllSCCOf(restriction);
    }

    // exception is a set of nodes that do not need to be found SCC
    public void findAllSCC(BitSet exception) {
        ISet nodes = graph.getNodes();
        for (int i = exception.nextClearBit(0); i >= 0 && i < n; i = exception.nextClearBit(i + 1)) {
            restriction.set(i, nodes.contains(i));
        }
        findAllSCCOf(restriction);
    }

    //!!这里改成boolean,表示提前退出propagation
    public void findAllSCCWithEarlyDetection() {
        ISet nodes = graph.getNodes();
        for (int i = 0; i < n; i++) {
            restriction.set(i, nodes.contains(i));
        }
        findAllSCCOfWithEarlyDetection(restriction);
    }

    public void findAllSCCOf(BitSet restriction) {
        inStack.clear();
        for (int i = 0; i < n; i++) {
            dfsNumOfNode[i] = 0;
            inf[i] = n + 2;
            nextNode[i] = -1;
            sccFirstNode[i] = -1;
            nodeSCC[i] = -1;
        }
        nbSCC = 0;
        findSingletons(restriction);
        int first = restriction.nextSetBit(0);
        while (first >= 0) {
            findSCC(first, restriction, stack, p, inf, nodeOfDfsNum, dfsNumOfNode, inStack);
            first = restriction.nextSetBit(first);
        }
    }

    public void findAllSCCOfWithEarlyDetection(BitSet restriction) {
        inStack.clear();
        for (int i = 0; i < n; i++) {
            dfsNumOfNode[i] = 0;
            inf[i] = n + 2;
            nextNode[i] = -1;
            sccFirstNode[i] = -1;
            nodeSCC[i] = -1;
        }
        nbSCC = 0;


        findSingletons(restriction);
        int first = restriction.nextSetBit(0);
        while (first >= 0) {
            findSCC(first, restriction, stack, p, inf, nodeOfDfsNum, dfsNumOfNode, inStack);
            first = restriction.nextSetBit(first);
        }
    }

    private void findSingletons(BitSet restriction) {
        ISet nodes = graph.getNodes();
        for (int i = restriction.nextSetBit(0); i >= 0; i = restriction.nextSetBit(i + 1)) {
            if (nodes.contains(i) && graph.getPredOf(i).size() * graph.getSuccOf(i).size() == 0) {
                nodeSCC[i] = nbSCC;
                sccFirstNode[nbSCC++] = i;
                restriction.clear(i);
            }
        }
    }

    private void findSCC(int start, BitSet restriction, int[] stack, int[] p, int[] inf, int[] nodeOfDfsNum, int[] dfsNumOfNode, BitSet inStack) {
        int nb = restriction.cardinality();
        // trivial case
        if (nb == 1) {
            nodeSCC[start] = nbSCC;
            sccFirstNode[nbSCC++] = start;
            restriction.clear(start);
            return;
        }
        //initialization
        int stackIdx = 0;
        int k = 0;
        int i = k;
        dfsNumOfNode[start] = k;
        nodeOfDfsNum[k] = start;
        stack[stackIdx++] = i;
        inStack.set(i);
        p[k] = k;
        iterator[k] = graph.getSuccOf(start).iterator();
        int j;
        // algo
        while (true) {
            if (iterator[i].hasNext()) {
                j = iterator[i].next();
                if (restriction.get(j)) {
                    if (dfsNumOfNode[j] == 0 && j != start) {
                        k++;
                        nodeOfDfsNum[k] = j;
                        dfsNumOfNode[j] = k;
                        p[k] = i;
                        i = k;
                        iterator[i] = graph.getSuccOf(j).iterator();
                        stack[stackIdx++] = i;
                        inStack.set(i);
                        inf[i] = i;
                    } else if (inStack.get(dfsNumOfNode[j])) {
                        inf[i] = Math.min(inf[i], dfsNumOfNode[j]);
                    }
                }
            } else {
                if (i == 0) {
                    break;
                }
                if (inf[i] >= i) {
                    int y, z;
                    do {
                        z = stack[--stackIdx];
                        inStack.clear(z);
                        y = nodeOfDfsNum[z];
                        restriction.clear(y);
                        sccAdd(y);
                    } while (z != i);
                    nbSCC++;
                }
                inf[p[i]] = Math.min(inf[p[i]], inf[i]);
                i = p[i];
            }
        }
        if (inStack.cardinality() > 0) {
            int y;
            do {
                y = nodeOfDfsNum[stack[--stackIdx]];
                restriction.clear(y);
                sccAdd(y);
            } while (y != start);
            nbSCC++;
        }
    }

    private boolean findSCCWithEarlyDetection(int start, BitSet restriction, int[] stack, int[] p, int[] inf, int[] nodeOfDfsNum, int[] dfsNumOfNode, BitSet inStack) {
        int nb = restriction.cardinality();
        // trivial case
        if (nb == 1) {
            nodeSCC[start] = nbSCC;
            sccFirstNode[nbSCC++] = start;
            restriction.clear(start);
            return true;
        }
        //initialization
        int stackIdx = 0;
        int k = 0;
        int i = k;
        dfsNumOfNode[start] = k;
        nodeOfDfsNum[k] = start;
        stack[stackIdx++] = i;
        inStack.set(i);
        p[k] = k;
        iterator[k] = graph.getSuccOf(start).iterator();
        int j;
        // algo
        while (true) {
            if (iterator[i].hasNext()) {
                j = iterator[i].next();
                if (restriction.get(j)) {
                    if (dfsNumOfNode[j] == 0 && j != start) {
                        k++;
                        nodeOfDfsNum[k] = j;
                        dfsNumOfNode[j] = k;
                        p[k] = i;
                        i = k;
                        iterator[i] = graph.getSuccOf(j).iterator();
                        stack[stackIdx++] = i;
                        inStack.set(i);
                        inf[i] = i;
                    } else if (inStack.get(dfsNumOfNode[j])) {
                        inf[i] = Math.min(inf[i], dfsNumOfNode[j]);

                        //for early detection
                        if (!unconnected) {
                            addCycles(inf[i], nbSCC);

                            while (inCycles(DE.get(DE.size()))){
                                DE.remove(DE.size());
                            }
                        }
                    }
                }
            } else {
                if (i == 0) {
                    break;
                }
                if (inf[i] >= i) {
                    int y, z;
                    do {
                        z = stack[--stackIdx];
                        inStack.clear(z);
                        y = nodeOfDfsNum[z];
                        restriction.clear(y);
                        sccAdd(y);
                    } while (z != i);
                    nbSCC++;
                }
                inf[p[i]] = Math.min(inf[p[i]], inf[i]);
                i = p[i];
            }

            if (!unconnected&&DE.isEmpty()){
                // 停止传播
                return false;
            }
        }
        if (inStack.cardinality() > 0) {
            int y;
            do {
                y = nodeOfDfsNum[stack[--stackIdx]];
                restriction.clear(y);
                sccAdd(y);
            } while (y != start);
            nbSCC++;
        }

        return true;
    }


    private void sccAdd(int y) {
        nodeSCC[y] = nbSCC;
        nextNode[y] = sccFirstNode[nbSCC];
        sccFirstNode[nbSCC] = y;
    }


    private void addCycles(int a, int b) {
        Iterator<IntTuple2> iter = cycles.iterator();
        while (iter.hasNext()) {
            IntTuple2 t = iter.next();
            if (t.overlap(a, b)) {
                t.a = Math.min(t.a, a);
                t.b = Math.min(t.b, b);
                return;
            }
        }
        cycles.add(new IntTuple2(a, b));
    }

    private boolean inCycles(IntTuple2 t) {
        for (IntTuple2 tt : cycles) {
            if (tt.cover(dfsNumOfNode[t.a], dfsNumOfNode[t.b])) {
                return true;
            }
        }
        return false;
    }
    //***********************************************************************************
    // ACCESSORS
    //***********************************************************************************

    public int getNbSCC() {
        return nbSCC;
    }

    public int[] getNodesSCC() {
        return nodeSCC;
    }

    public int getSCCFirstNode(int i) {
        return sccFirstNode[i];
    }

    public int getNextNode(int j) {
        return nextNode[j];
    }

}