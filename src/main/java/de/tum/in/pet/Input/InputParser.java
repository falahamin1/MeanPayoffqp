package de.tum.in.pet.Input;

import de.tum.in.pet.implementation.meanPayoff.DeltaTCalculationMethod;
import de.tum.in.pet.implementation.meanPayoff.SimulateMec;
import de.tum.in.pet.implementation.reachability.UpdateMethod;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.util.CliHelper;
import de.tum.in.probmodels.explorer.InformationLevel;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.util.function.Function;

public class InputParser {

    public static InputValues parseInput(String[] args) {
        Options options = InputOptions.getAllInputOptions();
        CommandLine commandLine = CliHelper.parse(options, args);

        double precision = parseDoubleOption(commandLine, InputOptions.precisionOption, DefaultInputValues.PRECISION);
        int revisitThreshold = parseIntOption(commandLine, InputOptions.revisitThresholdOption, DefaultInputValues.THRESHOLD);
        double maxReward = parseDoubleOption(commandLine, InputOptions.maxRewardOption, DefaultInputValues.REWARD_UPPERBOUND);
        double pMin = parseDoubleOption(commandLine, InputOptions.pMinOption, DefaultInputValues.P_MIN_LOWERBOUND);
        double errorTolerance = parseDoubleOption(commandLine, InputOptions.errorToleranceOption, DefaultInputValues.ERROR_TOLERANCE);
        int iterSamples = parseIntOption(commandLine, InputOptions.iterationSamplesOption, DefaultInputValues.ITERATION_SAMPLE);
        long timeout = parseLongOption(commandLine, InputOptions.timeoutOption, DefaultInputValues.TIMEOUT);
        boolean getErrorProbability = isOptionPresent(commandLine, InputOptions.getErrorProbabilityOption);
        String rewardStructure = parseOption(commandLine, InputOptions.rewardModuleOption, null, Function.identity());

        SuccessorHeuristic heuristic = CliHelper.parseHeuristic(
                commandLine.getOptionValue(InputOptions.heuristicOption.getLongOpt()), DefaultInputValues.HEURISTIC);

        InformationLevel informationLevel = CliHelper.parseInformationLevel(
                commandLine.getOptionValue(InputOptions.informationOption.getLongOpt()), DefaultInputValues.INFORMATION_LEVEL);

        UpdateMethod updateMethod = CliHelper.parseUpdateMethod(
                commandLine.getOptionValue(InputOptions.updateMethodOption.getLongOpt()), DefaultInputValues.UPDATE_METHOD);

        boolean solveUsingQP = isOptionPresent(commandLine, InputOptions.solveWithQP);

        boolean solveUsingSG = isOptionPresent(commandLine, InputOptions.solveWithSG);

        SimulateMec simulateMec = CliHelper.parseSimulateMec(
                commandLine.getOptionValue(InputOptions.simulateMec.getLongOpt()), DefaultInputValues.SIMULATE_MEC);

        String outputPath = parseOption(commandLine, InputOptions.outputFile, DefaultInputValues.OUTPUT_PATH, Function.identity());

        int maxSuccessorsInModel = parseIntOption(commandLine, InputOptions.maxSuccessorOption,
                (int) (1/pMin));

        DeltaTCalculationMethod deltaTMethod = CliHelper.parseDeltaTCalculationMethod(
                commandLine.getOptionValue(InputOptions.deltaTOption.getLongOpt()), DefaultInputValues.DELTA_T_CALCULATION_METHOD);

        return new InputValues(precision,
                revisitThreshold,
                maxReward,
                pMin,
                errorTolerance,
                iterSamples,
                timeout,
                getErrorProbability,
                heuristic,
                informationLevel,
                updateMethod,
                rewardStructure,
                solveUsingQP,
                solveUsingSG,
                simulateMec,
                outputPath,
                maxSuccessorsInModel,
                deltaTMethod);
    }

    private static long parseLongOption(CommandLine commandLine, Option option, long defaultValue) {
        return parseOption(commandLine, option, defaultValue, Long::parseLong);
    }

    private static double parseDoubleOption(CommandLine commandLine, Option option, double defaultValue) {
        return parseOption(commandLine, option, defaultValue, Double::parseDouble);
    }

    private static int parseIntOption(CommandLine commandLine, Option option, int defaultValue) {
        return parseOption(commandLine, option, defaultValue, Integer::parseInt);
    }

    private static <E> E parseOption(CommandLine commandLine, Option option, E defaultValue,
                                     Function<String, E> parseFunction) {
        return isOptionPresent(commandLine, option)
                ? parseFunction.apply(commandLine.getOptionValue(option.getLongOpt()))
                : defaultValue;
    }

    private static boolean isOptionPresent(CommandLine commandLine, Option option) {
        return commandLine.hasOption(option.getLongOpt());
    }
}
