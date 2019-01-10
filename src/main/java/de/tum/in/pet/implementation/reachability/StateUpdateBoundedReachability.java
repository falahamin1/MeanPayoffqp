package de.tum.in.pet.implementation.reachability;

import de.tum.in.pet.model.Distribution;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.bounded.StateUpdateBounded;
import de.tum.in.pet.values.bounded.StateValuesBoundedFunction;
import java.util.List;
import java.util.function.IntPredicate;

public class StateUpdateBoundedReachability implements StateUpdateBounded  {
  private final IntPredicate target;
  private final ValueUpdate update;

  public StateUpdateBoundedReachability(IntPredicate target, ValueUpdate update) {
    this.target = target;
    this.update = update;
  }

  @Override
  public Bounds update(int state, int remainingSteps, List<Distribution> choices,
      StateValuesBoundedFunction values) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    if (values.lowerBound(state, remainingSteps) == 1.0d) {
      assert values.upperBound(state, remainingSteps) == 1.0d;
      return Bounds.reachOne();
    }
    if (values.upperBound(state, remainingSteps) == 0.0d) {
      assert values.lowerBound(state, remainingSteps) == 0.0d;
      return Bounds.reachZero();
    }
    assert !target.test(state);

    if (choices.isEmpty()) {
      return Bounds.reachZero();
    }

    if (choices.size() == 1) {
      return values.bounds(state, remainingSteps, choices.get(0));
    }

    double newLowerBound;
    double newUpperBound;
    if (update == ValueUpdate.MAX_VALUE) {
      newLowerBound = 0.0d;
      newUpperBound = 0.0d;
      for (Distribution distribution : choices) {
        Bounds bounds = values.bounds(state, remainingSteps, distribution);
        double upperBound = bounds.upperBound();
        if (upperBound > newUpperBound) {
          newUpperBound = upperBound;
        }
        double lowerBound = bounds.lowerBound();
        if (lowerBound > newLowerBound) {
          newLowerBound = lowerBound;
        }
      }
    } else {
      assert update == ValueUpdate.MIN_VALUE;

      newUpperBound = 1.0d;
      newLowerBound = 1.0d;
      for (Distribution distribution : choices) {
        Bounds bounds = values.bounds(state, remainingSteps, distribution);
        double upperBound = bounds.upperBound();
        if (upperBound < newUpperBound) {
          newUpperBound = upperBound;
        }
        double lowerBound = bounds.lowerBound();
        if (lowerBound < newLowerBound) {
          newLowerBound = lowerBound;
        }
      }
    }
    assert newLowerBound <= newUpperBound;
    return Bounds.of(newLowerBound, newUpperBound);
  }
}
