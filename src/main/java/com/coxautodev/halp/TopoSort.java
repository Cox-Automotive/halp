package com.coxautodev.halp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TopoSort {

    static class CycleDiscovery extends RuntimeException {
        final List<String> cyclePath;
        public CycleDiscovery(List<String> cyclePath) {
            super(""+cyclePath);
            this.cyclePath = cyclePath;
        }
    }

    /**
     * Returns a list of modules sorted topologically (dependency order)
     */
    static <T> List<T> topoSort(Map<T, List<T>> nodes) {
        List<T> processed = new ArrayList();

        NodeMap nm = new NodeMap();
        nm.putAll((Map)nodes);

        for (Object n : nodes.keySet()) {
            visit(nm, processed, n);
        }

        return processed;
    }

    private static class NodeMap extends HashMap<Object, List<Object>> {}

    private static void visit(NodeMap nodes, List processed, Object n) {
        visit(nodes, processed, n, new ArrayList());
    }

    private static void visit(NodeMap nodes, List processed, Object n, List seen) {

        if (seen.contains(n)) {
            throw new CycleDiscovery(conj(seen, n));
        }

        if (!processed.contains(n)) {
            seen = conj(seen, n);
            List<Object> edges = nodes.get(n);
            if (edges != null) {
                for (Object edge : edges) {
                    visit(nodes, processed, edge, seen);
                }
            }
            processed.add(n);
        }
    }

    /**
     * return a new list that contains the contents of l with n appended to it
     */
    private static <T> List<T> conj(List<T> l, T n) {
        List<T> r = new ArrayList(l);
        r.add(n);
        return r;
    }


}
