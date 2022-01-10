package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.analysis.AliasAnalysis;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;

import java.math.BigInteger;

import static com.dat3m.dartagnan.expression.utils.Utils.generalEqual;
import static com.dat3m.dartagnan.wmm.utils.Utils.edge;
import static com.dat3m.dartagnan.wmm.utils.Utils.intVar;
import static com.google.common.base.Preconditions.*;


public class PropertyEncoder implements Encoder {

    private static final Logger logger = LogManager.getLogger(PropertyEncoder.class);

    private final Program program;
    private final Wmm memoryModel;
    //TODO: We misuse the <task> object as information pool for static analyses here
    // We ignore the program, wmm etc. that is contained in <task>.
    private final VerificationTask task;

    // =====================================================================

    private PropertyEncoder(Program program, Wmm wmm, VerificationTask task) {
        this.program = checkNotNull(program);
        this.memoryModel = checkNotNull(wmm);
        this.task = checkNotNull(task);
        checkArgument(program.isCompiled(),
                "The program must get compiled first before its properties can be encoded.");
    }

    public static PropertyEncoder fromConfig(Program program, Wmm wmm, VerificationTask task, Configuration config) throws InvalidConfigurationException {
        return new PropertyEncoder(program, wmm, task);
    }

    @Override
    public void initializeEncoding(SolverContext context) { }

    public BooleanFormula encodeBoundEventExec(SolverContext ctx){
        logger.info("Encoding bound events execution");

        BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();
        return program.getCache().getEvents(FilterBasic.get(EType.BOUND))
                .stream().map(Event::exec).reduce(bmgr.makeFalse(), bmgr::or);
    }

    public BooleanFormula encodeAssertions(SolverContext ctx) {
        logger.info("Encoding assertions");

        BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();

        BooleanFormula assertionEncoding = program.getAss().encode(ctx);
        if (program.getAssFilter() != null) {
            assertionEncoding = bmgr.and(assertionEncoding, program.getAssFilter().encode(ctx));
        }
        return assertionEncoding;
    }

    public BooleanFormula encodeDataRaces(SolverContext ctx) {
        final String hb = "hb";
        checkState(memoryModel.getAxioms().stream().anyMatch(ax ->
                        ax.isAcyclicity() && ax.getRelation().getName().equals(hb)),
                "The provided WMM needs an 'acyclic(hb)' axiom to encode data races.");
        logger.info("Encoding data-races");

        AliasAnalysis alias = task.getAnalysisContext().requires(AliasAnalysis.class);
        BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();
        IntegerFormulaManager imgr = ctx.getFormulaManager().getIntegerFormulaManager();

        BooleanFormula enc = bmgr.makeFalse();
        for(Thread t1 : program.getThreads()) {
            for(Thread t2 : program.getThreads()) {
                if(t1.getId() == t2.getId()) {
                    continue;
                }
                for(Event e1 : t1.getCache().getEvents(FilterMinus.get(FilterBasic.get(EType.WRITE), FilterBasic.get(EType.INIT)))) {
                    MemEvent w = (MemEvent)e1;
                    for(Event e2 : t2.getCache().getEvents(FilterMinus.get(FilterBasic.get(EType.MEMORY), FilterBasic.get(EType.INIT)))) {
                        MemEvent m = (MemEvent)e2;
                        if(w.hasFilter(EType.RMW) && m.hasFilter(EType.RMW)) {
                            continue;
                        }
                        if(w.canRace() && m.canRace() && alias.mayAlias(w, m)) {
                            BooleanFormula conflict = bmgr.and(m.exec(), w.exec(), edge(hb, m, w, ctx),
                                    generalEqual(w.getMemAddressExpr(), m.getMemAddressExpr(), ctx),
                                    imgr.equal(intVar(hb, w, ctx),
                                            imgr.add(intVar(hb, m, ctx), imgr.makeNumber(BigInteger.ONE))));
                            enc = bmgr.or(enc, conflict);
                        }
                    }
                }
            }
        }
        return enc;
    }


}
