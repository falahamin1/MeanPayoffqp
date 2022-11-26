package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.implementation.qp_meanpayoff.LowerBound;
import de.tum.in.pet.implementation.qp_meanpayoff.UpperBound;
import de.tum.in.pet.implementation.reachability.GreyUnboundedReachValues;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.explorer.GreyExplorer;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.doubles.Double2LongFunction;
import it.unimi.dsi.fastutil.ints.*;

/**
 * While handling components, we only keep the actions that are fully explored.
 * Also we use greybox equations to update, only if all the successors are visited.
 */
public class GreyOnDemandValueIterator<S, M extends Model> extends BlackOnDemandValueIterator<S, M> {

    public GreyOnDemandValueIterator(Explorer<S, M> explorer, UnboundedValues values, RewardGenerator<S> rewardGenerator,
                                     int revisitThreshold, double rMax, double pMin, double errorTolerance,
                                     Double2LongFunction nSampleFunction, double precision, long timeout, SimulateMec simulateMec,
                                     DeltaTCalculationMethod deltaTCalculationMethod, int maxSuccessorsInModel) {
        super(explorer, values, rewardGenerator, revisitThreshold, rMax, pMin, errorTolerance, nSampleFunction, precision,
                timeout, false, simulateMec, deltaTCalculationMethod, maxSuccessorsInModel, LowerBound.VI, UpperBound.VI);

        initGreyUnboundedReachValues();
    }

    /**
     * Unlike blackbox, we only look for components, if we have an action for which all successors has been visited
     */
    @Override
    protected boolean shouldHandleComponents() {
        GreyExplorer<S, M> explorer = (GreyExplorer<S, M>) this.explorer;
        return explorer.isNewFullyExploredActionAvailable();
    }

    @Override
    protected void resetSeenTransitionsSignificantlyFlag() {
        super.resetSeenTransitionsSignificantlyFlag();

        GreyExplorer<S, M> explorer = (GreyExplorer<S, M>) this.explorer;
        explorer.resetFullyExploredActionFlag();
    }

    private void initGreyUnboundedReachValues() {
        GreyUnboundedReachValues greyUnboundedReachValues = (GreyUnboundedReachValues) this.values;

        GreyExplorer<S, M> greyExplorer = (GreyExplorer<S, M>) this.explorer;

        greyUnboundedReachValues.setChoiceFunction(this::choices);
        Int2ObjectFunction<Int2IntFunction> getNumSuccessors = x -> (y -> greyExplorer.getOriginalNumSuccessors(x, y));
        greyUnboundedReachValues.setGetNumSuccessors(getNumSuccessors);
        greyUnboundedReachValues.setIsInMEC(stateToMecMap::containsKey);
    }
}
