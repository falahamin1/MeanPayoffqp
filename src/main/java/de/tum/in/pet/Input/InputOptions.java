package de.tum.in.pet.Input;

import de.tum.in.pet.util.CliHelper;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

// On adding new option, make sure to add them to getAllInputOptions method
public class InputOptions {
    public static Option precisionOption = new Option(null, "precision", true, "Required precision of the returned value. (Default: 10^{-6})");
    public static Option heuristicOption = CliHelper.getDefaultHeuristicOption();
    public static Option modelOption = new Option("m", "model", true, "Path to model file");
    public static Option constantsOption = new Option("c", "const", true,
            "Constants of model/property file, comma separated list");
    public static Option revisitThresholdOption = new Option(null, "revisitThreshold", true, "Number of times a state should be visited before a sampling run is stopped. (Default: 5)");
    public static Option rewardModuleOption = new Option(null, "rewardModule", true, "Name of the reward module in the model file.");
    public static Option maxRewardOption = new Option(null, "maxReward", true, "Estimated max reward value for a single transition in the model. (Default: 10)");
    public static Option informationOption = CliHelper.getDefaultInformationLevelOption();
    public static Option pMinOption = new Option(null, "pMin", true, "Estimated minimum probability of a transition in the model. (Only used for BlackBox models)");
    public static Option errorToleranceOption = new Option(null, "errorTolerance", true, "Error tolerance for blackbox exploration.");
    public static Option iterationSamplesOption =  new Option(null, "iterSamples", true, "Number of sample paths to be generated per episodic run.");
    public static Option updateMethodOption = new Option(null, "updateMethod", true, "Update method to be used (greybox/blackbox)");
    public static Option timeoutOption = new Option(null, "timeout", true, "Time before experiment forcefully terminates");
    public static Option getErrorProbabilityOption = new Option(null, "getErrorProbability", false, "Computes the error probability for blackbox with greybox equations");

    public static Options getAllInputOptions() {
        modelOption.setRequired(true);

        return new Options()
                .addOption(modelOption)
                .addOption(constantsOption)
                .addOption(precisionOption)
                .addOption(heuristicOption)
                .addOption(revisitThresholdOption)
                .addOption(rewardModuleOption)
                .addOption(maxRewardOption)
                .addOption(informationOption)
                .addOption(pMinOption)
                .addOption(errorToleranceOption)
                .addOption(iterationSamplesOption)
                .addOption(updateMethodOption)
                .addOption(timeoutOption)
                .addOption(getErrorProbabilityOption);
    }
}