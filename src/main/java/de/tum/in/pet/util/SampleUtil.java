package de.tum.in.pet.util;

import static de.tum.in.probmodels.util.Util.isEqual;
import static de.tum.in.probmodels.util.Util.isZero;

import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.util.Sample;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;

import java.util.List;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;

import java.util.ArrayList;

public final class SampleUtil {
  private SampleUtil() {
  }

  public static int sampleNextState(List<Distribution> choices, SuccessorHeuristic heuristic,
      ToDoubleFunction<Integer> actionScore, IntToDoubleFunction successorScore) {
    if (choices.isEmpty()) {
      return -1;
    }

    if (heuristic == SuccessorHeuristic.GRAPH_WEIGHTED) {
      if (choices.size() == 1) {
        Distribution distribution = choices.get(0);
        return distribution.sampleWeighted((s, p) -> p * successorScore.applyAsDouble(s));
      }
      Int2DoubleMap map = new Int2DoubleOpenHashMap();
      choices.forEach(choice -> choice.forEach((s, p) ->
          map.merge(s, successorScore.applyAsDouble(s) * p, Double::max)));
      return Sample.sample(map);
    }
    if (heuristic == SuccessorHeuristic.GRAPH_DIFFERENCE) {
      if (choices.size() == 1) {
        Distribution distribution = choices.get(0);
        return distribution.sampleWeighted((s, p) -> successorScore.applyAsDouble(s));
      }
      Int2DoubleMap map = new Int2DoubleOpenHashMap();
      choices.forEach(choice -> choice.forEach((s, p) ->
          map.merge(s, successorScore.applyAsDouble(s), Double::max)));
      return Sample.sample(map);
    }

    int distributionIndex = getOptimalChoice(choices, actionScore);
    if (distributionIndex == -1) {
      return -1;
    }
    Distribution distribution = choices.get(distributionIndex);
    if (distribution.isEmpty()) {
      return -1;
    }
    if (distribution.size() == 1) {
      return distribution.support().firstInt();
    }

    // Selected the action, now sample the successor
    switch (heuristic) {
      case PROB:
        return distribution.sample();
      case WEIGHTED:
        return distribution.sampleWeighted((s, p) -> p * successorScore.applyAsDouble(s));
      case DIFFERENCE:
        return distribution.sampleWeighted((s, p) -> successorScore.applyAsDouble(s));
      default:
        throw new AssertionError();
    }
  }

  public static int getOptimalChoice(List<Distribution> choices,
      ToDoubleFunction<Integer> score) {
    int choiceCount = choices.size();
    if (choiceCount == 1) {
      return 0;
    }

    int maximalBestActions = 0;
    double bestValue = Double.NEGATIVE_INFINITY;
    double[] actionUpperBounds = new double[choiceCount];
    for (int choice = 0; choice < choiceCount; choice++) {
      double upperBound = score.applyAsDouble(choice);
      actionUpperBounds[choice] = upperBound;
      if (upperBound > bestValue) {
        maximalBestActions = isEqual(upperBound, bestValue) ? maximalBestActions + 1 : 1;
        bestValue = upperBound;
      } else if (isEqual(upperBound, bestValue)) {
        maximalBestActions += 1;
      }
    }

    if (isZero(bestValue)) {
      // All successors have an score == 0
      return -1;
    }

    // maximalBestActions was an upper bound on the amount, compute precisely now
    int bestActionCount = 0;
    int[] bestActions = new int[maximalBestActions];
    for (int choice = 0; choice < choiceCount; choice++) {
      if (isEqual(bestValue, actionUpperBounds[choice])) {
        bestActions[bestActionCount] = choice;
        bestActionCount += 1;
      }
    }

    // There has to be a witness for the bestValue
    assert bestActionCount > 0;
    int distributionIndex = Sample.sampleUniform(bestActions, bestActionCount);
    assert isEqual(bestValue, score.applyAsDouble(distributionIndex));

    return distributionIndex;
  }
  public static List<Distribution> getBadActions(List<Distribution> choices, ToDoubleFunction<Integer> score, ToDoubleFunction<Integer> scoreL)
  {
    List<Distribution> optimizedChoices = new ArrayList<>();

    for (int i = 0; i < choices.size(); i++) {
      Distribution choiceI = choices.get(i);
      boolean isOptimal = true;

      for (int j = 0; j < choices.size(); j++) {
        if (i != j) {
          double scoreUI = score.applyAsDouble(i);
          double scoreLJ = scoreL.applyAsDouble(j);

          if (scoreUI < scoreLJ) {
            isOptimal = false;
            break;
          }
        }
      }

      if (!isOptimal) {
        optimizedChoices.add(choiceI);
      }
    }


    return optimizedChoices;
  }

  public static int getOptimalChoiceOptimized(List<Distribution> choices,
                                     ToDoubleFunction<Integer> score, ToDoubleFunction<Integer> scoreL ) {
    choices = getOptimizedChoices(choices, score, scoreL );
    int choiceCount = choices.size();
    if (choiceCount == 1) {
      return 0;
    }

    int maximalBestActions = 0;
    double bestValue = Double.NEGATIVE_INFINITY;
    double[] actionUpperBounds = new double[choiceCount];
    for (int choice = 0; choice < choiceCount; choice++) {
      double upperBound = score.applyAsDouble(choice);
      actionUpperBounds[choice] = upperBound;
      if (upperBound > bestValue) {
        maximalBestActions = isEqual(upperBound, bestValue) ? maximalBestActions + 1 : 1;
        bestValue = upperBound;
      } else if (isEqual(upperBound, bestValue)) {
        maximalBestActions += 1;
      }
    }

    if (isZero(bestValue)) {
      // All successors have an score == 0
      return -1;
    }

    // maximalBestActions was an upper bound on the amount, compute precisely now
    int bestActionCount = 0;
    int[] bestActions = new int[maximalBestActions];
    for (int choice = 0; choice < choiceCount; choice++) {
      if (isEqual(bestValue, actionUpperBounds[choice])) {
        bestActions[bestActionCount] = choice;
        bestActionCount += 1;
      }
    }

    // There has to be a witness for the bestValue
    assert bestActionCount > 0;
    int distributionIndex = Sample.sampleUniform(bestActions, bestActionCount);
    assert isEqual(bestValue, score.applyAsDouble(distributionIndex));

    return distributionIndex;
  }

  public static List<Distribution> getOptimizedChoices(List<Distribution> choices,
                                                       ToDoubleFunction<Integer> score, ToDoubleFunction<Integer> scoreL) {
    List<Distribution> optimizedChoices = new ArrayList<>();

    for (int i = 0; i < choices.size(); i++) {
      Distribution choiceI = choices.get(i);
      boolean isOptimal = true;

      for (int j = 0; j < choices.size(); j++) {
        if (i != j) {
          double scoreUI = score.applyAsDouble(i);
          double scoreLJ = scoreL.applyAsDouble(j);

          if (scoreUI < scoreLJ) {
            isOptimal = false;
            break;
          }
        }
      }

      if (isOptimal) {
        optimizedChoices.add(choiceI);
      }
    }
    int difference = choices.size() - optimizedChoices.size();
    if(difference > 0)
    {
      System.out.println("The difference in size: " + difference);
    }


    return optimizedChoices;
  }
}
