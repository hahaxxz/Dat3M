package com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.stat;

import com.dat3m.dartagnan.analysis.graphRefinement.util.EdgeDirection;
import com.dat3m.dartagnan.verification.model.Edge;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.ExecutionModel;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

// NOTE: Unlike LocRel, this graph is reflexive!
public class LocationGraph extends StaticEventGraph {

    private Map<BigInteger, Set<EventData>> addrEventsMap;

    @Override
    public boolean contains(Edge edge) {
        return edge.isLocEdge();
    }

    @Override
    public boolean contains(EventData a, EventData b) {
        return a.isMemoryEvent() && b.isMemoryEvent() && a.getAccessedAddress().equals(b.getAccessedAddress());
    }

    @Override
    public int getMinSize(EventData e, EdgeDirection dir) {
        if (!e.isMemoryEvent())
            return 0;
        return addrEventsMap.get(e.getAccessedAddress()).size();
        /*return context.getAddressWritesMap().get(e.getAccessedAddress()).size()
                + context.getAddressReadsMap().get(e.getAccessedAddress()).size() - 1;*/
    }


    @Override
    public void constructFromModel(ExecutionModel context) {
        super.constructFromModel(context);
        addrEventsMap = new HashMap<>(context.getAddressReadsMap().size());
        for (BigInteger addr : context.getAddressReadsMap().keySet()) {
            // TODO: This can be improved via a disjoint union class
            Set<EventData> events = new HashSet<>(context.getAddressReadsMap().get(addr));
            events.addAll(context.getAddressWritesMap().get(addr));
            size += events.size() * events.size();
            addrEventsMap.put(addr, events);
        }
    }

    @Override
    public Stream<Edge> edgeStream() {
        return addrEventsMap.values().stream().flatMap(Collection::stream)
                .flatMap(x -> edgeStream(x, EdgeDirection.Outgoing));
    }

    @Override
    public Stream<Edge> edgeStream(EventData e, EdgeDirection dir) {
        if (!e.isMemoryEvent()) {
            return Stream.empty();
        }
        Function<EventData, Edge> edgeMapping = dir == EdgeDirection.Outgoing ?
                (x -> new Edge(e, x)) : (x -> new Edge(x, e));
        return addrEventsMap.get(e.getAccessedAddress()).stream().map(edgeMapping);
    }

}
