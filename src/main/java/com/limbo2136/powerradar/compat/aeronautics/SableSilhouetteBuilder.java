package com.limbo2136.powerradar.compat.aeronautics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class SableSilhouetteBuilder {
    private SableSilhouetteBuilder() {
    }

    static Result build(Set<Long> occupied, double anchorX, double anchorZ) {
        Map<Integer, List<Integer>> horizontal = new TreeMap<>();
        Map<Integer, List<Integer>> vertical = new TreeMap<>();
        for (long packed : occupied) {
            int x = unpackX(packed);
            int z = unpackZ(packed);
            if (!occupied.contains(pack(x, z - 1))) {
                horizontal.computeIfAbsent(z, ignored -> new ArrayList<>()).add(x);
            }
            if (!occupied.contains(pack(x, z + 1))) {
                horizontal.computeIfAbsent(z + 1, ignored -> new ArrayList<>()).add(x);
            }
            if (!occupied.contains(pack(x - 1, z))) {
                vertical.computeIfAbsent(x, ignored -> new ArrayList<>()).add(z);
            }
            if (!occupied.contains(pack(x + 1, z))) {
                vertical.computeIfAbsent(x + 1, ignored -> new ArrayList<>()).add(z);
            }
        }

        List<SableSilhouetteLine> lines = new ArrayList<>();
        horizontal.forEach((z, starts) -> mergeHorizontal(lines, starts, z, anchorX, anchorZ));
        vertical.forEach((x, starts) -> mergeVertical(lines, starts, x, anchorX, anchorZ));
        List<SableSilhouetteLine> immutable = List.copyOf(lines);
        List<SableSilhouetteFill> fills = buildFills(occupied, anchorX, anchorZ);
        return new Result(immutable, fills, 31 * immutable.hashCode() + fills.hashCode());
    }

    private static List<SableSilhouetteFill> buildFills(Set<Long> occupied, double anchorX, double anchorZ) {
        Map<Integer, List<Integer>> rows = new TreeMap<>();
        for (long packed : occupied) {
            rows.computeIfAbsent(unpackZ(packed), ignored -> new ArrayList<>()).add(unpackX(packed));
        }
        List<SableSilhouetteFill> fills = new ArrayList<>();
        rows.forEach((z, columns) -> {
            columns.sort(Integer::compareTo);
            int start = columns.getFirst();
            int end = start + 1;
            for (int index = 1; index < columns.size(); index++) {
                int next = columns.get(index);
                if (next <= end) {
                    end = Math.max(end, next + 1);
                } else {
                    fills.add(fill(start, z, end, z + 1, anchorX, anchorZ));
                    start = next;
                    end = next + 1;
                }
            }
            fills.add(fill(start, z, end, z + 1, anchorX, anchorZ));
        });
        return List.copyOf(fills);
    }

    private static SableSilhouetteFill fill(
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            double anchorX,
            double anchorZ
    ) {
        return new SableSilhouetteFill(
                (float) (minX - anchorX),
                (float) (minZ - anchorZ),
                (float) (maxX - anchorX),
                (float) (maxZ - anchorZ));
    }

    static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    private static void mergeHorizontal(
            List<SableSilhouetteLine> lines,
            List<Integer> starts,
            int z,
            double anchorX,
            double anchorZ
    ) {
        starts.sort(Integer::compareTo);
        int start = starts.getFirst();
        int end = start + 1;
        for (int index = 1; index < starts.size(); index++) {
            int next = starts.get(index);
            if (next <= end) {
                end = Math.max(end, next + 1);
            } else {
                lines.add(line(start, z, end, z, anchorX, anchorZ));
                start = next;
                end = next + 1;
            }
        }
        lines.add(line(start, z, end, z, anchorX, anchorZ));
    }

    private static void mergeVertical(
            List<SableSilhouetteLine> lines,
            List<Integer> starts,
            int x,
            double anchorX,
            double anchorZ
    ) {
        starts.sort(Integer::compareTo);
        int start = starts.getFirst();
        int end = start + 1;
        for (int index = 1; index < starts.size(); index++) {
            int next = starts.get(index);
            if (next <= end) {
                end = Math.max(end, next + 1);
            } else {
                lines.add(line(x, start, x, end, anchorX, anchorZ));
                start = next;
                end = next + 1;
            }
        }
        lines.add(line(x, start, x, end, anchorX, anchorZ));
    }

    private static SableSilhouetteLine line(
            int x1,
            int z1,
            int x2,
            int z2,
            double anchorX,
            double anchorZ
    ) {
        return new SableSilhouetteLine(
                (float) (x1 - anchorX),
                (float) (z1 - anchorZ),
                (float) (x2 - anchorX),
                (float) (z2 - anchorZ));
    }

    record Result(List<SableSilhouetteLine> lines, List<SableSilhouetteFill> fills, int geometryHash) {
    }
}
