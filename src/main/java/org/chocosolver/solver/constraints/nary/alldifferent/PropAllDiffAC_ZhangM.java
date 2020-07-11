package org.chocosolver.solver.constraints.nary.alldifferent;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
//import org.chocosolver.solver.constraints.nary.alldifferent.algo.AlgoAllDiffACFast2;
import org.chocosolver.solver.constraints.nary.alldifferent.algo.AlgoAllDiffAC_ZhangM;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.ESat;

public class PropAllDiffAC_ZhangM extends Propagator<IntVar> {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    protected AlgoAllDiffAC_ZhangM filter;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    /**
     * AllDifferent constraint for integer variables
     * enables to control the cardinality of the matching
     *
     * @param variables array of integer variables
     */
    public PropAllDiffAC_ZhangM(IntVar[] variables) {
        super(variables, PropagatorPriority.QUADRATIC, false);
        this.filter = new AlgoAllDiffAC_ZhangM(variables, this);
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
