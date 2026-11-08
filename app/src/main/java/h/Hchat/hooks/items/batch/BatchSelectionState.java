package h.Hchat.hooks.items.batch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stable multi-scope selection ordering shared by the batch picker and regression tests. */
public final class BatchSelectionState {
    private BatchSelectionState() {}

    public static List<String> updateOrder(List<String> previousOrder,
                                           Set<String> selectedIds,
                                           Iterable<String> currentCandidates) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (previousOrder != null) {
            for (String id : previousOrder) {
                if (selectedIds != null && selectedIds.contains(id)) result.add(id);
            }
        }
        if (currentCandidates != null) {
            for (String id : currentCandidates) {
                if (selectedIds != null && selectedIds.contains(id)) result.add(id);
            }
        }
        if (selectedIds != null) result.addAll(selectedIds);
        return new ArrayList<>(result);
    }

    /** Puts visible selected rows first in selection order and retains candidate order after them. */
    public static List<String> selectedFirst(Iterable<String> candidates, List<String> selectionOrder) {
        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        if (candidates != null) {
            for (String id : candidates) if (id != null && !id.isEmpty()) candidateIds.add(id);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (selectionOrder != null) {
            for (String id : selectionOrder) if (candidateIds.contains(id)) result.add(id);
        }
        result.addAll(candidateIds);
        return new ArrayList<>(result);
    }

    /** Selects the currently visible IDs, or clears only those IDs when they are already selected. */
    public static List<String> toggleVisible(List<String> selectionOrder, Iterable<String> visibleIds) {
        LinkedHashSet<String> current = new LinkedHashSet<>();
        if (selectionOrder != null) current.addAll(selectionOrder);
        LinkedHashSet<String> visible = new LinkedHashSet<>();
        if (visibleIds != null) {
            for (String id : visibleIds) if (id != null && !id.isEmpty()) visible.add(id);
        }
        boolean allSelected = !visible.isEmpty() && current.containsAll(visible);
        if (allSelected) current.removeAll(visible);
        else current.addAll(visible);
        return new ArrayList<>(current);
    }
}
