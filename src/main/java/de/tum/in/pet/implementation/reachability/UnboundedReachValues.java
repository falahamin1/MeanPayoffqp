package de.tum.in.pet.implementation.reachability;

import static com.google.common.base.Preconditions.checkArgument;
import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;

import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.util.SampleUtil;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.*;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;

public class UnboundedReachValues implements UnboundedValues {
  protected final Int2ObjectMap<Bounds> bounds = new Int2ObjectOpenHashMap<>();
  protected final ValueUpdate update;
  protected final IntPredicate target; // Predicate to indicate if a given state is a target state
  protected final double precision;
  private final SuccessorHeuristic heuristic;

  public UnboundedReachValues(ValueUpdate update, IntPredicate target, double precision,
      SuccessorHeuristic heuristic) {
    this.update = update;
    this.target = target;
    this.precision = precision;
    this.heuristic = heuristic;
  }

  @Override
  public boolean isSmallestFixPoint() {
    return update == ValueUpdate.MIN_VALUE;
  }

  @Override
  // Returns the bounds for a state
  public Bounds bounds(int state) {
    // Checks if the given state is the target state. Return a reached bound (u=1, l=1), else if present, return the stored value or return an unknown value (u=1, l=0)
    return target.test(state)
        ? Bounds.reachOne()
        : bounds.getOrDefault(state, Bounds.reachUnknown());
  }

  public double lowerBound(int state) {
    if (target.test(state)) {
      return 1.0d;
    }
    Bounds bounds = this.bounds.get(state);
    return bounds == null ? 0.0d : bounds.lowerBound();
  }

  public double upperBound(int state) {
    if (target.test(state)) {
      return 1.0d;
    }
    Bounds bounds = this.bounds.get(state);
    return bounds == null ? 1.0d : bounds.upperBound();
  }


  @Override
  // Checks if u-l for a state is less than precision
  public boolean isSolved(int state) {
    return bounds(state).difference() < precision;
  }

  @Override
  // Checks if u-l for a state is one.
  public boolean isUnknown(int state) {
    return isOne(bounds(state).difference());
  }

  @Override
  // Samples a successor from a state given a list of choices.
  public int sampleNextState(int state, List<Distribution> choices) {
    // Gives weights to action according the their respective support's upper bounds.
    ToDoubleFunction<Integer> actionScore = isSmallestFixPoint()
        ? i -> 1.0d - choices.get(i).sumWeighted(this::lowerBound)
        : i -> choices.get(i).sumWeighted(this::upperBound);
    IntToDoubleFunction successorDifferences = s -> bounds(s).difference();

    return SampleUtil.sampleNextState(choices, heuristic, actionScore, successorDifferences);
  }

  @Override
  public int sampleNextAction(int state, List<Distribution> choices){
    ToDoubleFunction<Integer> actionScore = isSmallestFixPoint()
            ? i -> 1.0d - choices.get(i).sumWeighted(this::lowerBound)
            : i -> choices.get(i).sumWeighted(this::upperBound);

    return SampleUtil.getOptimalChoice(choices, actionScore);
  }

  @Override
  public List<Distribution> getBadActions (int state, List<Distribution> choices)
  {
    return null;
  }

  @Override
  // collapse a set of state into a new representative. Updates the bounds of the representative and removes bounds for all other states
  public void collapse(int representative, List<Distribution> choices, IntSet collapsed) {
    bounds.keySet().removeAll(collapsed);

    if (isSmallestFixPoint()) {
      // Only collapse bottom components
      checkArgument(choices.isEmpty());
    }

    // checks if any of the collapsed states is a target, if yes, set bounds to one (u=1, l=1).
    if (IntIterators.any(collapsed.iterator(), target)) {
      bounds.put(representative, Bounds.reachOne());
    } else {
      // updates bounds according to choices
      update(representative, choices);
    }
  }

  // Calculates new bounds according to an action. Lines 20, 21 in OnDemandVI in CAV'17 paper
  protected Bounds successorBounds(int state, Distribution distribution) {
    double lower = 0.0d;
    double upper = 0.0d;
    double sum = 0.0d;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      // It may be that the distribution has self loops, we want to avoid those
      if (successor == state) {
        continue;
      }
      Bounds successorBounds = bounds(successor);
      double probability = entry.getDoubleValue();
      sum += probability;
      lower += successorBounds.lowerBound() * probability;
      upper += successorBounds.upperBound() * probability;
    }
    if (sum == 0.0d) {
      return bounds(state);
    }
    return Bounds.reach(lower / sum, upper / sum);
  }



  @Override
  // updates bound of a state according to the given list of distributions
  public void update(int state, List<Distribution> choices) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    Bounds stateBounds = bounds(state);
    if (isOne(stateBounds.lowerBound()) || isZero(stateBounds.upperBound())) {
      return;
    }
    assert !target.test(state);

    Bounds oldBounds;
    Bounds newBounds;
    // If there are no choices from the state, it must have a zero value (u=0, l=0)
    if (choices.isEmpty()) {
      newBounds = Bounds.reachZero();
      oldBounds = bounds.put(state, newBounds);
    }
    else if (choices.size() == 1) {
      newBounds = successorBounds(state, choices.get(0));
      oldBounds = bounds.put(state, newBounds);
    }
    else {
      double newLowerBound;
      double newUpperBound;

      // finds bounds for each distribution using successorBounds. The new upper and lower bound are either the maximum or minimum of all computed respective bounds

      if (update == ValueUpdate.MAX_VALUE) {
        newLowerBound = 0.0d;
        newUpperBound = 0.0d;
        for (Distribution distribution : choices) {
          Bounds bounds = successorBounds(state, distribution);
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
          Bounds bounds = successorBounds(state, distribution);
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
      newBounds = Bounds.of(newLowerBound, newUpperBound);
      oldBounds = bounds.put(state, newBounds);
    }
    assert oldBounds == null || oldBounds.contains(newBounds);
  }

  @Override
  public void resetBounds(){
    bounds.clear();
  }

  @Override
  public void explored(int state) {
    // empty
  }
}
