package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.probmodels.generator.PrismRewardGenerator;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.DistributionBuilder;
import de.tum.in.probmodels.model.Distributions;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import parser.State;
import prism.ModelGenerator;
import prism.PrismException;
import simulator.ModulesFileModelGenerator;
import java.util.List;

public class MDPModelConstructor {
    private RewardGenerator<State> rewardGenerator;
    private List<State> statesList;

    public MarkovDecisionProcess constructMDP(ModulesFileModelGenerator generator, String rewardStructure) throws PrismException {
        statesList = generator.createVarList().getAllStates();
        int numStates = statesList.size();

        MarkovDecisionProcess model = new MarkovDecisionProcess();
        model.addStates(numStates);

        int numChoices, numTransitions;
        DistributionBuilder builder;
        String choiceLabel;
        for (int state = 0; state < numStates; state++) {
            generator.exploreState(statesList.get(state));

            numChoices = generator.getNumChoices();

            assert numChoices > 0;
            for (int choice = 0; choice < numChoices; choice++) {
                numTransitions = generator.getNumTransitions(choice);
                builder = Distributions.defaultBuilder();
                choiceLabel = generator.getChoiceAction(choice);

                assert numTransitions > 0;

                for (int transition = 0; transition < numTransitions; transition++) {
                    State targetState = generator.computeTransitionTarget(choice, transition);
                    int target = statesList.indexOf(targetState);
                    assert target != -1;
                    double prob = generator.getTransitionProbability(choice, transition);

                    builder.add(target, prob);
                }

                model.addChoice(state, Action.of(builder.build(), choiceLabel));
            }
        }

        for (State initialState : generator.getInitialStates()) {
            int initialStateIndex = statesList.indexOf(initialState);
            assert initialStateIndex != -1;

            model.addInitialState(initialStateIndex);
        }

        storeRewardGenerator(rewardStructure, generator);
        return model;
    }

    private void storeRewardGenerator(String rewardStructure, ModelGenerator modelGenerator) {
        int rewardIndex;

        if (rewardStructure == null) {
            rewardIndex = 0;
        } else {
            rewardIndex = modelGenerator.getRewardStructIndex(rewardStructure);
        }

        rewardGenerator = new PrismRewardGenerator(rewardIndex, modelGenerator);
    }

    public RewardGenerator<State> getRewardGenerator() {
        return rewardGenerator;
    }

    public List<State> getStatesList() {
        return statesList;
    }
}
