package de.tum.in.pet.util;

import de.tum.in.pet.implementation.meanPayoff.DeltaTCalculationMethod;
import de.tum.in.pet.implementation.meanPayoff.SimulateMec;
import de.tum.in.pet.implementation.qp_meanpayoff.LowerBound;
import de.tum.in.pet.implementation.qp_meanpayoff.UpperBound;
import de.tum.in.pet.implementation.reachability.UpdateMethod;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import de.tum.in.probmodels.explorer.InformationLevel;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.checkerframework.checker.nullness.Opt;

@SuppressWarnings("PMD.SystemPrintln")
public final class CliHelper {
  private static final Logger logger = Logger.getLogger(CliHelper.class.getName());

  private CliHelper() {
    // Empty
  }

  public static Option getDefaultHeuristicOption() {
    return new Option(null, "heuristic", true, "Sampling heuristic to be used.");
  }

  public static Option getDefaultInformationLevelOption() {
    return new Option(null, "informationLevel", true, "Information Level Assumption of Model. Default is WhiteBox.");
  }

  public static SuccessorHeuristic parseHeuristic(String optionString, SuccessorHeuristic defaultValue) {
    if (optionString == null) {
      return defaultValue;
    }
    try {
      return SuccessorHeuristic.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse heuristic", e);
      String values = Arrays.stream(SuccessorHeuristic.values())
          .map(Object::toString)
          .collect(Collectors.joining(", "));
      System.out.println("Unknown heuristic " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static InformationLevel parseInformationLevel(String optionString, InformationLevel defaultValue) {
    if (optionString == null) {
      return defaultValue;
    }
    try {
      return InformationLevel.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse information level", e);
      String values = Arrays.stream(InformationLevel.values())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      System.out.println("Unknown information level " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static UpdateMethod parseUpdateMethod(String optionString, UpdateMethod defaultValue) {
    if (optionString == null) {
      return defaultValue;
    }
    try {
      return UpdateMethod.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse information level", e);
      String values = Arrays.stream(InformationLevel.values())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      System.out.println("Unknown information level " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static LowerBound parseLowerBound(String optionString, LowerBound defaultValue) {
    if (optionString == null) {
      return defaultValue;
    }
    try {
      return LowerBound.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse information level", e);
      String values = Arrays.stream(InformationLevel.values())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      System.out.println("Unknown information level " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static UpperBound parseUpperBound(String optionString, UpperBound defaultValue) {
    if (optionString == null) {
      return defaultValue;
    }
    try {
      return UpperBound.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse information level", e);
      String values = Arrays.stream(InformationLevel.values())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      System.out.println("Unknown information level " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static SimulateMec parseSimulateMec(String optionString, SimulateMec defaultValue) {
    if (optionString == null) {
      return defaultValue;
    }

    try {
      return SimulateMec.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse simulate mec", e);
      String values = Arrays.stream(SimulateMec.values())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      System.out.println("Unknown simulate mec value " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static DeltaTCalculationMethod parseDeltaTCalculationMethod(String optionString, DeltaTCalculationMethod defaultMethod) {
    if (optionString == null) {
      return defaultMethod;
    }

    try {
      return DeltaTCalculationMethod.valueOf(optionString);
    } catch (IllegalArgumentException e) {
      logger.log(Level.FINE, "Failed to parse deltaTMethod ", e);
      String values = Arrays.stream(DeltaTCalculationMethod.values())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      System.out.println("Unknown deltat method value " + optionString + ". Possible values are: " + values);
      System.exit(1);
      throw new AssertionError(e);
    }
  }

  public static CommandLine parse(Options options, String[] args) {
    HelpFormatter formatter = new HelpFormatter();
    CommandLineParser cliParser = new DefaultParser();
    try {
      return cliParser.parse(options, args);
    } catch (ParseException e) {
      System.out.println("Failed to parse command line arguments: " + e.getMessage());
      formatter.printHelp("argument list", options);
      System.exit(1);
      throw new AssertionError(e);
    }
  }
}
