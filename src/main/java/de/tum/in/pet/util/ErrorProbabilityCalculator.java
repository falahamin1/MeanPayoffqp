package de.tum.in.pet.util;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.implementation.meanPayoff.BoundedMecQuotient;
import de.tum.in.probmodels.explorer.BlackExplorer;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import prism.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;

public class ErrorProbabilityCalculator {
    // Once the error probability is calculated for a state, we update the state as computed.
    // This is because the same state might recursively get called.
    // So to avoid infinite looping, we only once do the calculation for each state.
    Int2BooleanMap computedStates = new Int2BooleanOpenHashMap();

    // Given a state, this tells whether this state has already been added to the queue previously
    Int2BooleanMap addedToQueue = new Int2BooleanOpenHashMap();

    List<Pair<Pair<Integer, Integer>, Double>> stateActionProbabilities = new ArrayList<>();

    private final Int2ObjectMap<ObjectArrayList<Action>> originalStateActions;
    private final Int2ObjectFunction<List<Action>> getStateActions;
    private final Int2ObjectMap<ObjectArrayList<Int2LongMap>> stateTransitionCounts;
    private final Int2IntMap stateToMecMap;
    private final List<NatBitSet> mecs;

    public ErrorProbabilityCalculator(Int2ObjectFunction<List<Action>> getStateActions,
                                      Int2ObjectMap<ObjectArrayList<Action>> originalStateActions,
                                      Int2ObjectMap<ObjectArrayList<Int2LongMap>> stateTransitionCounts,
                                      Int2IntMap stateToMecMap,
                                      List<NatBitSet> mecs) {
        this.getStateActions = getStateActions;
        this.originalStateActions = originalStateActions;
        this.stateTransitionCounts = stateTransitionCounts;
        this.stateToMecMap = stateToMecMap;
        this.mecs = mecs;
    }

    public double getErrorProbability(int initialState) {
        computeErrorProbability(initialState, 1);
//        computeErrorProbabilityBFS(initialState);
        return computeResult();
    }

    private void computeErrorProbabilityBFS(int initialState) {
        LinkedTransferQueue<Pair<Integer, Double>> queue = new LinkedTransferQueue<>();
        queue.put(new Pair<>(initialState, 1d));
        addedToQueue.put(initialState, true);

        while (!queue.isEmpty()) {

            Pair<Integer, Double> elem = queue.poll();
            int state = elem.first;
            double reachProb = elem.second;

            if (computedStates.containsKey(state)) {
                continue;
            }

            List<Pair<Integer, Double>> successors = updateErrorProbabilities(state, reachProb);
            computedStates.put(state, true);

            for (Pair<Integer, Double> successor : successors) {
                int s = successor.getKey();
                double prob = successor.getValue();

                if (addedToQueue.containsKey(s) || BoundedMecQuotient.isSinkState(s)) {
                    continue;
                }

                addedToQueue.put(s, true);
                queue.put(new Pair<>(s, reachProb*prob));
            }
            // compute error probability
            // update computed states
            // add successors to queue, while adding update addedToQueue

        }
    }

    private List<Pair<Integer, Double>> updateErrorProbabilities(int state, double reachProbability) {
        List<Pair<Integer, Double>> stateSuccessors = new ArrayList<>();
        List<Action> actions = getStateActions.apply(state);

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);

            // If not in MEC, we compute the error probability
            if (!isInMEC(state, action)) {
                double prob = oneOfTheSuccessorIsNotVisited(state, i) * reachProbability;
                stateActionProbabilities.add(new Pair<>(new Pair<>(state, i), prob));
            }

            stateSuccessors.addAll(getSuccessors(state, i));
        }

        return stateSuccessors;
    }

    private void computeErrorProbability(int state, double reachProbability) {
        // Mark this state as computed. So It won't be again computed next time.
        // Even though we filter this out at last, it still needs to be here, because same state might appear twice in state
        // successors
        if (computedStates.containsKey(state)) {
            return;
        }

        computedStates.put(state, true);

        List<Pair<Integer, Double>> stateSuccessors = new ArrayList<>();

        //explorer.getActions will not contain the sink state
        List<Action> actions = getStateActions.apply(state);

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);

            // If not in MEC, we compute the error probability
            if (!isInMEC(state, action)) {
                double prob = oneOfTheSuccessorIsNotVisited(state, i) * reachProbability;
                stateActionProbabilities.add(new Pair<>(new Pair<>(state, i), prob));
            }

            stateSuccessors.addAll(getSuccessors(state, i));
        }

        // We recursively compute the error probabilities for the successors of the state
        // Before that we filter out already computed state and sink states
        stateSuccessors.stream()
                .filter(successorProbPair -> notAlreadyComputed(successorProbPair.first) && notASinkState(successorProbPair.first))
                .forEach(successorProbPair -> {
                    computeErrorProbability(successorProbPair.first, reachProbability * successorProbPair.second);
                });
    }

    private double computeResult() {
        double result = stateActionProbabilities.stream()
                .mapToDouble(elem -> 1 - elem.second)
                .reduce(1, (a, b) -> a*b);

        return 1 - result;
    }

    // Computes the probability that one of the successor is not visited for this state, and it's best action
    private double oneOfTheSuccessorIsNotVisited(int state, int bestActionIndex) {
        if (hasNoActions(state)) {
            return 0;
        }

        assert bestActionIndex != -1;

        List<Double> successorProbabilities = getOriginalSuccessorProbabilities(state, bestActionIndex);
        long numVisits = getStateActionVisitCount(state, bestActionIndex);

        if (numVisits == 0) {
            return 0;
        }

        return successorProbabilities.stream()
                .mapToDouble(prob -> successorNotVisited(prob, numVisits))
                .sum();
    }


    // Given a state and action, it returns the list of successor probabilities
    private List<Double> getOriginalSuccessorProbabilities(int state, int actionIndex) {
        Distribution actionDistribution = originalStateActions.get(state).get(actionIndex).distribution();
        List<Double> successorProbabilities = new ArrayList<>();
        for (Int2DoubleMap.Entry entry : actionDistribution) {
            successorProbabilities.add(entry.getDoubleValue());
        }

        return successorProbabilities;
    }

    // Returns the number of times, this state, action has been visited.
    private long getStateActionVisitCount(int state, int actionIndex) {
        // This map contains the number of visits for each successor of the action. We sum them up to find how many number
        // times this state action has been visited.
        Int2LongMap actionSuccessorsVisitCounts = stateTransitionCounts.get(state).get(actionIndex);


        return actionSuccessorsVisitCounts.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private double successorNotVisited(double successorProbability, long numVisits) {
        return Math.pow(1-successorProbability, numVisits);
    }

    private List<Pair<Integer, Double>> getSuccessors(int state, int actionIndex) {
        if (hasNoActions(state)) {
            return Collections.emptyList();
        }

        Distribution actionDistribution = getStateActions.apply(state).get(actionIndex).distribution();

        List<Pair<Integer, Double>> successors = new ArrayList<>();

        for (Int2DoubleMap.Entry entry : actionDistribution) {
            Pair<Integer, Double> pair = new Pair<>(entry.getIntKey(), entry.getDoubleValue());
            successors.add(pair);
        }

        return successors;
    }

    private boolean isInMEC(int state) {
        return stateToMecMap.containsKey(state);
    }

    // Checks whether the state action pair is in a MEC
    private boolean isInMEC(int state, Action action) {
        int mecIndex = getMECIndex(state);

        if (mecIndex == -1) {
            return false;
        }

        NatBitSet mecStates = mecs.get(mecIndex);
        NatBitSet actionSuccessors = action.distribution().support();
        return mecStates.containsAll(actionSuccessors);
    }

    private int getMECIndex(int state) {
        return isInMEC(state) ? stateToMecMap.get(state) : -1;
    }

    private boolean belongsToMEC(int state, int mecIndex) {
        return isInMEC(state) && (stateToMecMap.get(state) == mecIndex);
    }

    private boolean hasNoActions(int state) {
        return getStateActions.apply(state).isEmpty();
    }

    private boolean notInSameMEC(int state, int succ) {
        boolean belongsToSameMEC =  isInMEC(succ) && (getMECIndex(state) == getMECIndex(succ));
        return !belongsToSameMEC;
    }

    private boolean notAlreadyComputed(int state) {
        boolean alreadyComputed = computedStates.containsKey(state);
        return !alreadyComputed;
    }

    private boolean notASinkState(int state) {
        boolean isSink = BoundedMecQuotient.isSinkState(state);
        return !isSink;
    }
}
