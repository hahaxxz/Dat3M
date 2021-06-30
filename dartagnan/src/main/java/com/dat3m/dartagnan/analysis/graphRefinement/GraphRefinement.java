package com.dat3m.dartagnan.analysis.graphRefinement;

import com.dat3m.dartagnan.analysis.graphRefinement.coreReason.CoreLiteral;
import com.dat3m.dartagnan.analysis.graphRefinement.coreReason.Reasoner;
import com.dat3m.dartagnan.analysis.graphRefinement.graphs.ExecutionGraph;
import com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.EventGraph;
import com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.axiom.GraphAxiom;
import com.dat3m.dartagnan.analysis.graphRefinement.logic.Conjunction;
import com.dat3m.dartagnan.analysis.graphRefinement.logic.DNF;
import com.dat3m.dartagnan.analysis.graphRefinement.logic.SortedClauseSet;
import com.dat3m.dartagnan.analysis.graphRefinement.resolution.TreeResolution;
import com.dat3m.dartagnan.analysis.graphRefinement.searchTree.DecisionNode;
import com.dat3m.dartagnan.analysis.graphRefinement.searchTree.LeafNode;
import com.dat3m.dartagnan.analysis.graphRefinement.searchTree.SearchNode;
import com.dat3m.dartagnan.analysis.graphRefinement.searchTree.SearchTree;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.timeable.Timestamp;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.model.Edge;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.ExecutionModel;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.Context;
import com.microsoft.z3.Model;

import java.math.BigInteger;
import java.util.*;


public class GraphRefinement {

    // ================== Fields ==================

    // --------------- Static data ----------------
    private final VerificationTask task;
    private final ExecutionGraph execGraph;
    private final Reasoner reasoner;

    // ----------- Iteration-specific data -----------
    //TODO: We might want to take an external executionModel to perform refinement on!
    private final ExecutionModel executionModel;
    private final Map<BigInteger, Set<Edge>> possibleCoEdges = new HashMap<>();
    private RefinementStats stats;  // Statistics of the last call to kSearch

    // ============================================

    // =============== Accessors =================

    public VerificationTask getTask() {
        return task;
    }

    // NOTE: The execution graph should not be modified from the outside!
    public ExecutionGraph getExecutionGraph() { return execGraph; }

    public ExecutionModel getCurrentModel() {
        return executionModel;
    }


    // =============================================

    // =========== Construction & Init ==============

    public GraphRefinement(VerificationTask task) {
        this.task = task;
        this.execGraph = new ExecutionGraph(task);
        this.executionModel = new ExecutionModel(task);
        this.reasoner = new Reasoner(execGraph, true);
    }

    // ----------------------------------------------

    private void populateFromModel(Model model, Context ctx) {
        executionModel.initialize(model, ctx, false);
        execGraph.initializeFromModel(executionModel);
        // TODO: Remove testing code
        testIteration();
        testStaticGraphs();
    }

    private void initSearch() {
        Relation co = task.getMemoryModel().getRelationRepository().getRelation("co");
        for (Map.Entry<BigInteger, Set<EventData>> addressedWrites : executionModel.getAddressWritesMap().entrySet()) {
            Set<EventData> writes = addressedWrites.getValue();
            BigInteger address = addressedWrites.getKey();
            Set<Edge> coEdges = new HashSet<>();
            possibleCoEdges.put(address, coEdges);

            for (EventData e1 : writes) {
                for (EventData e2: writes) {
                    Tuple t = new Tuple(e1.getEvent(), e2.getEvent());

                    if (co.getMinTupleSet().contains(t)) {
                        //TODO: Test code
                        execGraph.addCoherenceEdge(new Edge(e1, e2));
                        continue;
                    }

                    // We only add edges in one direction
                    if (e2.getId() >= e1.getId())
                        continue;

                    if (e1.isInit() && !e2.isInit()) {
                        execGraph.addCoherenceEdge(new Edge(e1, e2));
                    } else if (!e1.isInit() && !e2.isInit()) {
                        coEdges.add(new Edge(e1, e2));
                    }
                }
            }
        }
        possibleCoEdges.values().removeIf(Collection::isEmpty);
    }

    /*
        A simple heuristic which moves all coherences to the front, which involve
        some write that was read from.
    */
    private void sortCoSearchList(List<Edge> list) {
        list.sort(Comparator.comparingInt(x -> -(x.getFirst().getImportance() + x.getSecond().getImportance())));
    }


    // ====================================================

    // ==============  Core functionality  =================

    /*
        kSearch performs a sequence of k-Saturations, starting from 0 up to <maxSaturationDepth>
        It returns whether it was successful, what violations where found (if any) and statistics
        about the computation.
     */
    public RefinementResult kSearch(Model model, Context ctx, int maxSaturationDepth) {
        RefinementResult result = new RefinementResult();
        stats = new RefinementStats();
        result.setStats(stats);

        // ====== Populate from model ======
        long curTime = System.currentTimeMillis();
        populateFromModel(model, ctx);
        stats.modelConstructionTime = System.currentTimeMillis() - curTime;
        stats.modelSize = executionModel.getEventList().size();
        // =================================

        // ======= Initialize search =======
        SearchTree sTree = new SearchTree();
        possibleCoEdges.clear();
        initSearch();

        List<Edge> coSearchList = new ArrayList<>();
        for (Set<Edge> coEdges : possibleCoEdges.values()) {
            coSearchList.addAll(coEdges);
        }
        sortCoSearchList(coSearchList);
        // =================================

        // ========= Actual search =========
        curTime = System.currentTimeMillis();
        for (int k = 0; k <= maxSaturationDepth; k++) {
            stats.saturationDepth = k;
            // There should always exist a single empty node unless we found a violation
            SearchNode start = sTree.findNodes(SearchNode::isEmptyNode).get(0);
            Result r = kSaturation(start, Timestamp.ZERO, k, coSearchList, 0);
            if (r != Result.UNKNOWN) {
                result.setResult(r);
                if (r == Result.FAIL) {
                    long temp = System.currentTimeMillis();
                    result.setViolations(computeResolventsFromTree(sTree));
                    stats.resolutionTime = System.currentTimeMillis() - temp;
                }
                break;
            }
            if (k > 0) {
                // For k=0, it is impossible to exclude coherences since no search is happening at all
                coSearchList.removeIf(this::coExists);
            }
            /*TODO: Maybe reduce k, whenever progress is made?
                if e.g. 2-SAT makes progress (finds some edge), then 1-SAT might be able to
                make use of that progress.
             */
        }
        // ==============================

        stats.searchTime = System.currentTimeMillis() - curTime;
        if (result.getResult() == Result.PASS) {
            testCoherence();
        }
        return result;
    }

    // ----------------------------------------------

    /*
        <searchList> is a list of coherences that need to be tested. It is assumed
        that for each write-pair (w1,w2) there is exactly one edge in the list, either co(w1, w2) or
        co(w2, w1).
     */
    private Result kSaturation(SearchNode node, Timestamp curTime, int k, List<Edge> searchList, int searchStart) {
        searchList = searchList.subList(searchStart, searchList.size());
        if (k == 0) {
            // 0-SAT amounts to a simple violation check
            if (checkViolations()) {
                long time = System.currentTimeMillis();
                node.replaceBy(new LeafNode(computeViolationList()));
                stats.violationComputationTime += (System.currentTimeMillis() - time);
                return Result.FAIL;
            } else if (searchList.stream().allMatch(this::coExists)) {
                // All remaining edges in the search list are already in the graph (due to transitivity and totality of co)
                return Result.PASS;
            } else {
                return Result.UNKNOWN;
            }
        }

        searchList = new ArrayList<>(searchList);
        boolean progress = true;
        while (progress) {
            progress = false;

            for (int i = 0; i < searchList.size(); i++) {
                Edge coEdge = searchList.get(i);
                if (coExists(coEdge))
                    continue;

                Timestamp nextTime = curTime.next();
                coEdge = coEdge.withTimestamp(nextTime);
                DecisionNode decNode = new DecisionNode(coEdge);

                execGraph.addCoherenceEdge(coEdge);
                stats.numGuessedCoherences++;
                Result r = kSaturation(decNode.getPositive(), nextTime, k - 1, searchList, i + 1);
                if (r == Result.PASS && searchList.stream().allMatch(this::coExists)) {
                    return Result.PASS;
                }
                backtrackOn(nextTime);

                if (r == Result.FAIL) {
                    node.replaceBy(decNode);
                    node = decNode.getNegative();
                    execGraph.addCoherenceEdge(coEdge.getInverse().withTimestamp(curTime));
                    r = kSaturation(decNode.getNegative(), curTime, k - 1, searchList, i + 1);
                    if (r == Result.FAIL) {
                        return r;
                    } else if (r == Result.PASS && searchList.stream().allMatch(this::coExists)) {
                        return r;
                    }
                    // We made progress
                    //TODO: We might want to restart the search or do some other heuristic
                    // to guide our search.
                    progress = true;
                } else {
                    nextTime = curTime.next();
                    Edge coEdgeInv = coEdge.getInverse().withTimestamp(nextTime);
                    execGraph.addCoherenceEdge(coEdgeInv);
                    stats.numGuessedCoherences++;
                    r = kSaturation(decNode.getNegative(), nextTime, k - 1, searchList, i + 1);
                    if (r == Result.PASS && searchList.stream().allMatch(this::coExists)) {
                        return Result.PASS;
                    }
                    backtrackOn(nextTime);

                    if(r == Result.FAIL) {
                        node.replaceBy(decNode);
                        node = decNode.getPositive();
                        execGraph.addCoherenceEdge(coEdge.withTimestamp(curTime));
                        progress = true;
                    }
                }
                searchList.removeIf(this::coExists);
            }
        }
        return Result.UNKNOWN;
    }

    private void backtrackOn(Timestamp time) {
        time.invalidate();
        execGraph.backtrack();
    }

    private boolean coExists(Edge coEdge) {
        return execGraph.getCoGraph().contains(coEdge) || execGraph.getCoGraph().contains(coEdge.getInverse());
    }

    // ============= Violations + Resolution ================

    private boolean checkViolations() {
        boolean hasViolation = false;
        for (GraphAxiom axiom : execGraph.getGraphAxioms()) {
            hasViolation |= axiom.checkForViolations();
        }

        return hasViolation;
    }

    private List<Conjunction<CoreLiteral>> computeViolationList() {
        List<Conjunction<CoreLiteral>> violations = new ArrayList<>();
        for (GraphAxiom axiom : execGraph.getGraphAxioms()) {
            violations.addAll(reasoner.computeViolationReasons(axiom).getCubes());
        }
        // Important code: We only retain those violations with the least number of co-literals
        // this heavily boosts the performance of the resolution!!!
        int minComplex = violations.stream().mapToInt(Conjunction::getResolutionComplexity).min().getAsInt();
        violations.removeIf(x -> x.getResolutionComplexity() > minComplex);
        // TODO: The following is ugly, but we convert to DNF again to remove dominated clauses and duplicates
        violations = new ArrayList<>(new DNF<>(violations).getCubes());

        stats.numComputedViolations += violations.size();

        return violations;
    }

    private DNF<CoreLiteral> computeResolventsFromTree(SearchTree tree) {
        //TOOD: This is also ugly code
        SortedClauseSet<CoreLiteral> res = new TreeResolution(tree).computeViolations();
        SortedClauseSet<CoreLiteral> res2 = new SortedClauseSet<>();
        res.forEach(clause -> res2.add(reasoner.simplifyReason(clause)));
        res2.simplify();
        return res2.toDNF();
    }

    // ====================================================

    // ===================== TESTING ======================

    private final static boolean DEBUG = false;
    private void testIteration() {
        if (!DEBUG)
            return;
        for (EventGraph g : execGraph.getEventGraphs()) {
            int size = g.size();
            for (Edge e : g) {
                size--;
                if (size < 0) {
                    throw new RuntimeException();
                }
            }
            if (size > 0) {
                throw new RuntimeException();
            }

            if (g.edgeStream().count() != g.size()) {
                throw new RuntimeException();
            }
        }
    }

    private void testStaticGraphs() {
        if (!DEBUG)
            return;

        for (Relation relData : task.getRelationDependencyGraph().getNodeContents()) {
            if (relData.getName().equals("co")) {
                continue;
            }
            if (relData.isStaticRelation() || relData.isRecursiveRelation()) {
                EventGraph g = execGraph.getEventGraph(relData);
                if (g == null) {
                    continue;
                }
                for (Tuple t : relData.getMinTupleSet()) {
                    if (executionModel.eventExists(t.getFirst()) && executionModel.eventExists(t.getSecond())) {
                        if (!g.contains(executionModel.getEdge(t))) {
                            throw new RuntimeException();
                        }
                    }
                }
            }
        }
    }

    private void testCoherence() {
        if (!DEBUG)
            return;
        TupleSet tSet = new TupleSet();
        for (Edge e : execGraph.getWoGraph()) {
            tSet.add(new Tuple(e.getFirst().getEvent(), e.getSecond().getEvent()));
        }
        Map<Event, Set<Event>> map = tSet.transMap();
        for (Event e1 : map.keySet()) {
            for (Event e2 : map.get(e1)) {
                Edge edge = executionModel.getEdge(new Tuple(e1,e2));
                if (!execGraph.getCoGraph().contains(edge)) {
                    throw new RuntimeException();
                }
            }
        }

        for (Set<EventData> writes : executionModel.getAddressWritesMap().values()) {
            for (EventData e1 : writes) {
                for (EventData e2 : writes) {
                    if (e1 == e2) {
                        continue;
                    }

                    if (!coExists(new Edge(e1,e2))) {
                        throw new RuntimeException();
                    }
                }
            }
        }
    }

    // ====================================================

}
