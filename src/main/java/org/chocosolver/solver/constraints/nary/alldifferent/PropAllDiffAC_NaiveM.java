package org.chocosolver.solver.constraints.nary.alldifferent;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.constraints.nary.alldifferent.algo.AlgoAllDiffAC_Naive;
import org.chocosolver.solver.constraints.nary.alldifferent.algo.AlgoAllDiffAC_NaiveBitSet;
import org.chocosolver.solver.constraints.nary.alldifferent.algo.AlgoAllDiffAC_Naive32;
import org.chocosolver.solver.constraints.nary.alldifferent.algo.AlgoAllDiffAC_Naive64;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.ESat;

public class PropAllDiffAC_NaiveM extends Propagator<IntVar> {

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
    public PropAllDiffAC_NaiveM(IntVar[] variables) {
        super(variables, PropagatorPriority.QUADRATIC, false);
//        out.println("vars length: " + variables.length);
        if (variables.length <= 32) {
            this.filter = new AlgoAllDiffAC_Naive32(variables, this);
        } else if (variables.length <= 64) {
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