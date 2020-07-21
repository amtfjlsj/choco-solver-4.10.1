/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2019, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.constraints.nary.alldifferent;

import gnu.trove.map.hash.TIntIntHashMap;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.constraints.nary.alldifferent.algo.*;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.ESat;

/**
 * Propagator for AllDifferent AC constraint for integer variables
 * <p/>
 * Uses Zhang algorithm in the paper of IJCAI-18
 * "A Fast Algorithm for Generalized Arc Consistency of the Alldifferent Constraint"
 * <p>
 * We try to use the bit to speed up.
 * <p>
 * Runs in O(m.n) worst case time for the initial propagation
 * but has a good average behavior in practice
 * <p/>
 * Runs incrementally for maintaining a matching
 * <p/>
 *
 * @author Jia'nan Chen
 */

public class PropAllDiffAC_Naive extends Propagator<IntVar> {

    //***********************************************************************************0
    // VARIABLES
    //***********************************************************************************

    protected AlgoAllDiffAC_Naive filter;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    /**
     * AllDifferent constraint for integer variables
     * enables to control the cardinality of the matching
     *
     * @param variables array of integer variables
     */
    public PropAllDiffAC_Naive(IntVar[] variables) {
        super(variables, PropagatorPriority.QUADRATIC, false);
//        //统计值个数
//        TIntIntHashMap val2Idx = new TIntIntHashMap();
//        // 统计所有变量论域中不同值的个数
//        for (IntVar v : variables) {
//            for (int j = v.getLB(), ub = v.getUB(); j <= ub; j = v.nextValue(j)) {
//                if (!val2Idx.containsKey(j)) {
//                    val2Idx.put(j, val2Idx.size());
//                }
//            }
//        }

//        int numValues = val2Idx.size();
        if (vars.length <= 32) {
            this.filter = new AlgoAllDiffAC_Naive32(variables, this);
        } else if (vars.length <= 64) {
            this.filter = new AlgoAllDiffAC_Naive64(variables, this);
        } else {
            this.filter = new AlgoAllDiffAC_NaiveBitSet(variables, this);
        }
    }

    //***********************************************************************************
    // PROPAGATION
    //***********************************************************************************

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        filter.propagate();
    }

    @Override
    public ESat isEntailed() {
        return ESat.TRUE; // redundant propagator (used with PropAllDiffInst)
    }

}
