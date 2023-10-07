package de.tum.in.pet.implementation.core;

import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.lessOrEqual;

import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.util.SampleUtil;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.ToDoubleFunction;

abstract class UnboundedCoreValues implements UnboundedValues {
  public final double precision;
  public final SuccessorHeuristic heuristic;

  public UnboundedCoreValues(double precision, SuccessorHeuristic heuristic) {
    this.precision = precision;
    this.heuristic = heuristic;
  }

  @Override
  public Bounds bounds(int state) {
    return Bounds.reach(0.0, upperBound(state));
  }

  abstract double upperBound(int state);

  @Override
  public boolean isSolved(int state) {
    return upperBound(state) < precision;
  }

  @Override
  public boolean isUnknown(int state) {
    return isOne(upperBound(state));
  }

  @Override
  public int sampleNextState(int state, List<Distribution> choices) {
    ToDoubleFunction<Integer> actionScore = i -> choices.get(i).sumWeighted(this::upperBound);
    return SampleUtil.sampleNextState(choices, heuristic, actionScore, this::upperBound);
  }

  @Override
  public void update(int state, List<Distribution> choices) {
    if (choices.isEmpty()) {
      update(state, 0.0d);
    }

    double maximalValue = 0.0d;
    for (Distribution distribution : choices) {
      double expectedValue = distribution.sumWeighted(this::upperBound);
      if (expectedValue > maximalValue) {
        maximalValue = expectedValue;
      }
      if (isOne(expectedValue)) {
        break;
      }
    }
    update(state, maximalValue);
  }

  @Override
  public boolean isSmallestFixPoint() {
    return false;
  }

  abstract void update(int state, double value);

  public static final class Dense extends UnboundedCoreValues {
    private double[] bounds = new double[1024];

    public Dense(double precision, SuccessorHeuristic heuristic) {
      super(precision, heuristic);
      Arrays.fill(bounds, 1.0d);
    }

    @Override
    public boolean isUnknown(int state) {
      return state >= bounds.length || isOne(bounds[state]);
    }

    @Override
    public int sampleNextAction(int state, List<Distribution> choices) {
      return 0;
    }

    @Override
    public List<Distribution> getBadActions(int state, List<Distribution> choices)
    {
      return null;
    }

    @Override
    public double upperBound(int state) {
      return state < bounds.length ? bounds[state] : 1.0d;
    }

    @Override
    public void collapse(int representative, List<Distribution> choices, IntSet collapsed) {
      update(representative, choices);
    }

    @Override
    public void resetBounds() {

    }

    @Override
    void update(int state, double value) {
      int length = bounds.length;
      if (state >= length) {
        if (isOne(value)) {
          return;
        }
        bounds = Arrays.copyOf(bounds, length * 2);
        Arrays.fill(bounds, length, length * 2, 1.0d);
      }
      assert lessOrEqual(value, bounds[state]);
      bounds[state] = value;
    }

    @Override
    public void explored(int state) {
      // empty
    }
  }

  public static class Sparse extends UnboundedCoreValues {
    private final Int2DoubleMap map = new Int2DoubleOpenHashMap();

    public Sparse(double precision, SuccessorHeuristic heuristic) {
      super(precision, heuristic);
      map.defaultReturnValue(1.0d);
    }

    @Override
    public double upperBound(int state) {
      return map.get(state);
    }

    @Override
    public void collapse(int representative, List<Distribution> choices, IntSet collapsed) {
      collapsed.forEach((IntConsumer) map::remove);
      update(representative, choices);
    }

    @Override
    public void resetBounds() {

    }

    @Override
    void update(int state, double value) {
      if (isOne(value)) {
        return;
      }
      double oldValue = map.put(state, value);
      assert lessOrEqual(value, oldValue);
    }

    @Override
    public int sampleNextAction(int state, List<Distribution> choices) {
      return 0;
    }

    @Override
    public List<Distribution> getBadActions(int state, List<Distribution> choices)
    {
      return null;
    }

    @Override
    public void explored(int state) {
      // empty
    }
  }

  @Override
  public String toString() {
    return String.format("UnboundedValues(%s)@%f", heuristic, precision);
  }
}
