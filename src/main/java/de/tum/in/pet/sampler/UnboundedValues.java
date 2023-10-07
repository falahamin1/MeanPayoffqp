package de.tum.in.pet.sampler;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;

public interface UnboundedValues {
  Bounds bounds(int state);

  boolean isSolved(int state);

  boolean isUnknown(int state);

  int sampleNextState(int state, List<Distribution> choices);

  int sampleNextAction(int state, List<Distribution> choices);

  public  List<Distribution> getBadActions(int state, List<Distribution> choices);

  void update(int state, List<Distribution> choices);

  void explored(int state);

  boolean isSmallestFixPoint();

  void collapse(int representative, List<Distribution> choices, IntSet collapsed);

  void resetBounds();
}
