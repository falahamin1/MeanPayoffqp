package de.tum.in.pet.values.bounded;

import de.tum.in.pet.model.Distribution;

@FunctionalInterface
public interface UpperBoundsBoundedFunction {
  double upperBound(int state, int remainingSteps);

  default double upperBound(int state, int remainingSteps, Distribution distribution) {
    return distribution.sumWeighted(s -> upperBound(s, remainingSteps - 1));
  }

  default boolean isZeroUpperBound(int state, int remainingSteps) {
    return upperBound(state, remainingSteps) == 0.0d;
  }
}