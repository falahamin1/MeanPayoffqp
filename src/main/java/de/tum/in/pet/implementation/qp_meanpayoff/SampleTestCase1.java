package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.Int2BooleanFunction;
import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

import java.util.ArrayList;
import java.util.List;

public class SampleTestCase1 {
    public static MarkovDecisionProcess getMDP() {
        MarkovDecisionProcess mdp = new MarkovDecisionProcess();
        mdp.addStates(3);
//        State s0 = new State(0);
//        State s1 = new State(1);
//        State s2 = new State(2);

//        List<State> states = new ArrayList<>(3);
//        states.add(s0);
//        states.add(s1);
//        states.add(s2);
//
//        mdp.setStatesList(states);

        for (int i = 0; i < 3; i++) {
            mdp.setActions(i, getActions(i));
        }

        List<Integer> initialStates = new ArrayList<>(1);
        initialStates.add(0);
        mdp.setInitialStates(initialStates);

        return mdp;
    }

    public static List<NatBitSet> getComponents() {
        NatBitSet set = NatBitSets.ensureModifiable(NatBitSets.boundedFullSet(3));
        List<NatBitSet> components = new ArrayList<>(1);
        components.add(set);
        return components;
    }

    public static LPRewardProvider getReward() {
        return new LPRewardProvider() {
            @Override
            public double stateReward(int state) {
                if (state == 1) {
                    return 1;
                }

                return 5;
            }

            @Override
            public double transitionReward(int state, int action, Object actionLabel) {
                return 0;
            }
        };
    }

    public static Int2ObjectFunction<Int2DoubleFunction> getCWFunction() {
        Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction = x -> (y -> 0);
        return confidenceWidthFunction;
    }

    public static Int2ObjectFunction<Int2BooleanFunction> getValidMECStateActionPairDetector() {
        Int2ObjectFunction<Int2BooleanFunction> vFunction = x -> (y -> true);
        return vFunction;
    }

    private static List<Action> getActions(int state) {
        DistributionBuilder builder = Distributions.defaultBuilder();
        builder.add((state + 1) % 3, 1d);
        Action a = Action.of(builder.build());

        builder = Distributions.defaultBuilder();
        builder.add((state + 2) % 3, 1d);
        Action b = Action.of(builder.build());

        List<Action> actions = new ArrayList<>(2);
        actions.add(a);
        actions.add(b);
        return actions;
    }
}
