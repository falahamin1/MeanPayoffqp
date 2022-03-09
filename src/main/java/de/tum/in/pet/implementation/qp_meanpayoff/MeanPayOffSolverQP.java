package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSetFactory;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.set.NatBitSetsUtil;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.graph.MecComponentAnalyser;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import it.unimi.dsi.fastutil.ints.IntSet;
import prism.PrismException;
import simulator.ModulesFileModelGenerator;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MeanPayOffSolverQP {

    public static void solveUsingQP(ModulesFileModelGenerator generator, String rewardStructure) throws PrismException {
        MDPModelConstructor constructor = new MDPModelConstructor();
        MarkovDecisionProcess mdp = constructor.constructMDP(generator, rewardStructure);

        IntSet states = NatBitSets.boundedFilledSet(mdp.getNumStates());
        MecComponentAnalyser mecAnalyzer = new MecComponentAnalyser();
        List<NatBitSet> components = mecAnalyzer.findComponents(mdp, states);

        List<Mec> mecs = components.stream().map(component -> Mec.create(mdp, component))
                .collect(Collectors.toList());

//        MeanPayoffLPWriter lpWriter = new MeanPayoffLPWriter(mdp, constructor.getRewardGenerator(), mecs, constructor.getStatesList());
        MeanPayoffLPWriter lpWriter = new MeanPayoffLPWriter(mdp, mecs, new MeanPayoffLPWriter.LPRewardProvider() {
            @Override
            public double stateReward(int state) {
                return constructor.getRewardGenerator().stateReward(constructor.getStatesList().get(state));
            }

            @Override
            public double transitionReward(int state, int action, Object actionLabel) {
                return constructor.getRewardGenerator().transitionReward(constructor.getStatesList().get(state),actionLabel);
            }
        });
        try {
            lpWriter.constructLP();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
