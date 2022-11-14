package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.DistributionBuilder;
import de.tum.in.probmodels.model.Distributions;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class MeanPayoffQPTest {
    public static void main(String[] args) {
        MeanPayoffQP meanPayoffQP = new MeanPayoffQP(SampleTestCase2.getMDP(),
                SampleTestCase2.getComponents(), SampleTestCase2.getReward(), SampleTestCase2.getCWFunction(), SampleTestCase2.getValidMECStateActionPairDetector(), true);

        try {
            double result = meanPayoffQP.solveForMeanPayoff();
            System.out.println("MeanPayoff is " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static MecInformationProvider testCase1() {
        // 2 states with 1 action on each state.

        return new MecInformationProvider() {
            @Override
            public NatBitSet provideStates() {
                return NatBitSets.ensureModifiable(NatBitSets.boundedFullSet(2));
            }

            @Override
            public IntSet provideActions(int state) {
                int[] actions = new int[1];
                actions[0] = 1;
                return new IntArraySet(actions);
            }

            @Override
            public Distribution provideDistribution(int state, int action) {
                DistributionBuilder builder = Distributions.defaultBuilder();
                builder.add((state + 1) % 2, 1d);
                return builder.build();
            }

            @Override
            public double provideConfidenceWidth(int state, int action) {
                return 0.1;
            }

            public double provideTwoSidedConfidenceWidth(int state, int action) {
                return 0.1;
            }
        };
    }

    private static MecInformationProvider testCase2() {
        return new MecInformationProvider() {
            @Override
            public NatBitSet provideStates() {
                return NatBitSets.ensureModifiable(NatBitSets.boundedFullSet(10));
            }

            @Override
            public IntSet provideActions(int state) {
                int[] actions = new int[1];
                actions[0] = 0;
//                actions[1] = 1;
                return new IntArraySet(actions);
            }

            @Override
            public Distribution provideDistribution(int state, int action) {
                DistributionBuilder builder = Distributions.defaultBuilder();
                builder.add((state + 1) % 10, 0.5d);
                builder.add((state + 2) % 10, 0.5d);
                return builder.build();
            }

            @Override
            public double provideConfidenceWidth(int state, int action) {
                return 0.1;
            }

            public double provideTwoSidedConfidenceWidth(int state, int action) {
                return 0.1;
            }
        };
    }

    private static LPRewardProvider testCase1RP() {
        return new LPRewardProvider() {
            @Override
            public double stateReward(int state) {
                return 1;
            }

            @Override
            public double transitionReward(int state, int action, Object actionLabel) {
                return 1;
            }
        };
    }
}
