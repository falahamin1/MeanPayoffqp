package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.set.RoaringNatBitSetFactory;
import de.tum.in.pet.Input.InputOptions;
import de.tum.in.pet.Input.InputParser;
import de.tum.in.pet.Input.InputValues;
import de.tum.in.pet.Main;
import de.tum.in.pet.implementation.qp_meanpayoff.MeanPayOffSolverQP;
import de.tum.in.pet.implementation.reachability.*;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.util.CliHelper;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.CTMDPBlackExplorer;
import de.tum.in.probmodels.explorer.Explorers;
import de.tum.in.probmodels.explorer.InformationLevel;
import de.tum.in.probmodels.generator.*;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.util.PrismHelper;
import it.unimi.dsi.fastutil.doubles.Double2LongFunction;
import org.apache.commons.cli.CommandLine;
import parser.State;
import parser.ast.ModulesFile;
import prism.*;
import simulator.ModulesFileModelGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
This code's purpose is to facilitate the testing of the OnDemandValueIterator.
This code should return correct results if the number of states is lesser than or equal to INT_MAX-3
the values INT_MAX, INT_MAX-1, INT_MAX-2 are assigned to special states.
* */
public final class MeanPayoffChecker {
    private static final Logger logger = Logger.getLogger(MeanPayoffChecker.class.getName());
    private static final List<Pair<Long, Bounds>> timeVBound = new ArrayList<>();
    private static final List<Double> qp_result = new ArrayList<>();
    private static final List<String> additionalWriteInfo = new ArrayList<>();

    public static double solve(ModelGenerator generator, int rewardIndex, InputValues inputValues)
            throws PrismException {
        ModelType modelType = generator.getModelType();
        switch (modelType) {
            case MDP:
                return solveMdp(generator, rewardIndex, inputValues);
            case CTMC:
            case DTMC:
            case LTS:
            case CTMDP:
                return solveCtmdp(generator, rewardIndex, inputValues);
            case PTA:
            case STPG:
            case SMG:
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static <S, M extends Model> double solveCtmdp(M partialModel, Generator<S> generator,
                                                          RewardGenerator<S> rewardGenerator, InputValues inputValues)
            throws PrismException {

        var explorer = new CTMDPBlackExplorer<S, M>(partialModel, generator, false,
                System.currentTimeMillis() + inputValues.timeout);

        IntPredicate target = (x) -> x == Integer.MAX_VALUE;
        OnDemandValueIterator<S, M> valueIterator;

        if (inputValues.informationLevel == InformationLevel.WHITEBOX) {
            throw new UnsupportedOperationException("Whitebox not implemented for CTMDP");
        } else if (inputValues.informationLevel == InformationLevel.BLACKBOX) {
            Double2LongFunction nSampleFunction = s -> inputValues.iterSamples;

            UnboundedValues values = new BlackUnboundedReachValues(ValueUpdate.MAX_VALUE, inputValues.updateMethod, target, inputValues.precision / inputValues.maxReward, inputValues.successorHeuristic);

            valueIterator = new CTMDPBlackOnDemandValueIterator<>(explorer, values, rewardGenerator,
                    inputValues.revisitThreshold, inputValues.maxReward, inputValues.pMin, inputValues.errorTolerance,
                    nSampleFunction, inputValues.precision / inputValues.maxReward,
                    System.currentTimeMillis() + inputValues.timeout, inputValues.getErrorProbability, inputValues.simulateMec,
                    inputValues.deltaTCalculationMethod, inputValues.maxSuccessorsInModel);
        } else {
            throw new UnsupportedOperationException("Greybox not implemented for CTMDP");
        }

        valueIterator.run();

        int initState = explorer.initialStates().iterator().nextInt();
        Bounds bounds = valueIterator.bounds(initState);

        logger.log(Level.INFO, "Explored states {0}", new Object[]{explorer.exploredStateCount()});

        timeVBound.addAll(valueIterator.timeVBound);
        qp_result.addAll(valueIterator.qp_result);
        additionalWriteInfo.addAll(valueIterator.additionalWriteInfo);

        return inputValues.maxReward * bounds.average();

    }

    private static <S, M extends Model> double solve(M partialModel, Generator<S> generator, RewardGenerator<S> rewardGenerator,
                                                     InputValues ip)
            throws PrismException {

        var explorer = Explorers.getExplorer(partialModel, generator, ip.informationLevel, false,
                System.currentTimeMillis() + ip.timeout);

        IntPredicate target = (x) -> x == Integer.MAX_VALUE;
        OnDemandValueIterator<S, M> valueIterator;

        if (ip.informationLevel == InformationLevel.WHITEBOX) {
            UnboundedValues values = new UnboundedReachValues(ValueUpdate.MAX_VALUE, target,
                    ip.precision / ip.maxReward, ip.successorHeuristic);

            valueIterator = new OnDemandValueIterator<>(explorer, values, rewardGenerator, ip.revisitThreshold,
                    ip.maxReward, ip.precision / ip.maxReward,
                    System.currentTimeMillis() + ip.timeout);
        } else if (ip.informationLevel == InformationLevel.BLACKBOX) {
            Double2LongFunction nSampleFunction = s -> ip.iterSamples;

            UnboundedValues values = new BlackUnboundedReachValues(ValueUpdate.MAX_VALUE, ip.updateMethod, target,
                    ip.precision / ip.maxReward, ip.successorHeuristic);

            valueIterator = new BlackOnDemandValueIterator<>(explorer, values, rewardGenerator,
                    ip.revisitThreshold, ip.maxReward, ip.pMin, ip.errorTolerance, nSampleFunction,
                    ip.precision / ip.maxReward, System.currentTimeMillis() + ip.timeout, ip.getErrorProbability,
                    ip.simulateMec, ip.deltaTCalculationMethod, ip.maxSuccessorsInModel, ip.solveUsingQP, ip.solveUsingSG, ip.lowerBound, ip.upperBound);
        } else {
            Double2LongFunction nSampleFunction = s -> ip.iterSamples;

            UnboundedValues values = new GreyUnboundedReachValues(ValueUpdate.MAX_VALUE, ip.updateMethod, target,
                    ip.precision / ip.maxReward, ip.successorHeuristic);

            valueIterator = new GreyOnDemandValueIterator<>(explorer, values, rewardGenerator,
                    ip.revisitThreshold, ip.maxReward, ip.pMin, ip.errorTolerance, nSampleFunction,
                    ip.precision / ip.maxReward, System.currentTimeMillis() + ip.timeout,
                    ip.simulateMec, ip.deltaTCalculationMethod, ip.maxSuccessorsInModel);
        }

        valueIterator.run();

        int initState = explorer.initialStates().iterator().nextInt();
        Bounds bounds = valueIterator.bounds(initState);

        logger.log(Level.INFO, "Explored states {0}", new Object[]{explorer.exploredStateCount()});

        timeVBound.addAll(valueIterator.timeVBound);
        qp_result.addAll(valueIterator.qp_result);
        additionalWriteInfo.addAll(valueIterator.additionalWriteInfo);

        return ip.maxReward * bounds.average();

    }


    private static double solveCtmdp(ModelGenerator prismGenerator, int rewardIndex, InputValues inputValues)
            throws PrismException {

        MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
        Generator<State> generator = new CtmdpGenerator(prismGenerator);

        RewardGenerator<State> rewardGenerator = new PrismRewardGenerator(rewardIndex, prismGenerator);

        return solveCtmdp(partialModel, generator, rewardGenerator, inputValues);

    }

    private static double solveMdp(ModelGenerator prismGenerator, int rewardIndex, InputValues inputValues)
            throws PrismException {

        MarkovDecisionProcess partialModel = new MarkovDecisionProcess();
        Generator<State> generator = new MdpGenerator(prismGenerator);

        RewardGenerator<State> rewardGenerator = new PrismRewardGenerator(rewardIndex, prismGenerator);

        return solve(partialModel, generator, rewardGenerator, inputValues);

    }

    public static void main(String[] args) throws PrismException, IOException {
        InputValues ip = InputParser.parseInput(args);
        NatBitSets.setFactory(new RoaringNatBitSetFactory());
        CommandLine commandLine = CliHelper.parse(InputOptions.getAllInputOptions(), args);

        double startTime1 = System.currentTimeMillis();
        PrismHelper.PrismParseResult parse =
                Main.parse(commandLine, InputOptions.modelOption, null, InputOptions.constantsOption);
        ModulesFile modulesFile = parse.modulesFile();

        Prism prism = new Prism(new PrismDevNullLog());
        ModulesFileModelGenerator generator = new ModulesFileModelGenerator(modulesFile, prism);


        int rewardIndex = ip.rewardStructure == null ? 0 : generator.getRewardStructIndex(ip.rewardStructure);
        if (rewardIndex == -1) {
            throw new NoSuchElementException("Reward module " + commandLine.getOptionValue(InputOptions.rewardModuleOption.getLongOpt()) + " not found");
        }

        long startTime2 = System.currentTimeMillis();
        timeVBound.add(new Pair<>(startTime2, Bounds.of(0, ip.maxReward)));
        double meanPayoff = solve(generator, rewardIndex, ip);
        long endTime = System.currentTimeMillis();

        ResultWriter.write(commandLine, timeVBound, qp_result, additionalWriteInfo, ip.outputPath);

        logger.log(Level.INFO, "Time to parse, construct model, and compute {0}", new Object[]{endTime - startTime1});
        logger.log(Level.INFO, "Time to compute {0}", new Object[]{endTime - startTime2});
        logger.log(Level.INFO, "Result is {0}", new Object[]{meanPayoff});
    }

}
