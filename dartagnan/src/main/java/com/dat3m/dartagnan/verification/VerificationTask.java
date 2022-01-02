package com.dat3m.dartagnan.verification;

import com.dat3m.dartagnan.encoding.MemoryEncoder;
import com.dat3m.dartagnan.encoding.ProgramEncoder;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Label;
import com.dat3m.dartagnan.program.processing.ProcessingManager;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.utils.equivalence.BranchEquivalence;
import com.dat3m.dartagnan.utils.symmetry.SymmetryBreaking;
import com.dat3m.dartagnan.utils.symmetry.ThreadSymmetry;
import com.dat3m.dartagnan.witness.WitnessGraph;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.alias.AliasAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.List;
import java.util.Set;

import static com.dat3m.dartagnan.program.processing.Compilation.TARGET;
import static com.dat3m.dartagnan.program.processing.LoopUnrolling.BOUND;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/*
Represents a verification task.
 */
@Options
public class VerificationTask {

	public static final String TIMEOUT = "verification.timeout";
	public static final String OPTION_DEBUG_PRINT = "program.debugPrint";
	
    private static final Logger logger = LogManager.getLogger(VerificationTask.class);

    private final Program program;
    private final Wmm memoryModel;
    private final WitnessGraph witness;
    private final Configuration config;
    private BranchEquivalence branchEquivalence;
	private AliasAnalysis aliasAnalysis;
    private ThreadSymmetry threadSymmetry;

    private final ProgramEncoder progEncoder;
    private final MemoryEncoder memoryEncoder;

	@Option(
		name=OPTION_DEBUG_PRINT,
		description="Prints the program after all processing steps and before verification for debug purposes.",
		secure=true)
	private boolean debugPrint;

    protected VerificationTask(Program program, Wmm memoryModel, VerificationTaskBuilder builder) {
        this.program = checkNotNull(program);
        this.memoryModel = checkNotNull(memoryModel);
        this.witness = checkNotNull(builder.witness);

        try {
            this.config = builder.config.build();
            config.recursiveInject(this);
            progEncoder = ProgramEncoder.fromConfig(config);
            memoryEncoder = MemoryEncoder.fromConfig(config);
        } catch (InvalidConfigurationException ex) {
            // TODO: We throw a RuntimeException for now to avoid forcing the calling code
            //  to be adapted (we convert from checked exception to unchecked exception)
            throw new RuntimeException(ex);
        }
    }

    public static VerificationTaskBuilder builder() {
        return new VerificationTaskBuilder();
    }

    public static class VerificationTaskBuilder {
        protected WitnessGraph witness = new WitnessGraph();
        protected ConfigurationBuilder config = Configuration.builder();

        protected VerificationTaskBuilder() { }

        public VerificationTaskBuilder withWitness(WitnessGraph witness) {
            this.witness = checkNotNull(witness, "Witness may not be null.");
            return this;
        }

        public VerificationTaskBuilder withTarget(Arch target) {
            this.config.setOption(TARGET, target.toString());
            return this;
        }

		public VerificationTaskBuilder withBound(int k) {
			this.config.setOption(BOUND, Integer.toString(k));
			return this;
		}

		public VerificationTaskBuilder withSolverTimeout(int t) {
			this.config.setOption(TIMEOUT, Integer.toString(t));
			return this;
		}

        public VerificationTaskBuilder withConfig(Configuration config) {
            this.config.copyFrom(config);
            return this;
        }

        public VerificationTask build(Program program, Wmm memoryModel) {
            return new VerificationTask(program,memoryModel,this);
        }
    }

    public Configuration getConfig() {
        return this.config;
    }

    public Program getProgram() {
    	return program;
    }
    
    public Wmm getMemoryModel() {
    	return memoryModel;
    }
    
    public WitnessGraph getWitness() {
    	return witness;
    }

    public Set<Relation> getRelations() {
    	return memoryModel.getRelationRepository().getRelations();
    }

    public List<Axiom> getAxioms() {
    	return memoryModel.getAxioms();
    }

    public BranchEquivalence getBranchEquivalence() {
        return branchEquivalence;
    }

	public AliasAnalysis getAliasAnalysis() {
		return aliasAnalysis;
	}

    public DependencyGraph<Relation> getRelationDependencyGraph() {
        return memoryModel.getRelationDependencyGraph();
    }

    public ThreadSymmetry getThreadSymmetry() {
        if (threadSymmetry == null) {
            checkState(program.isCompiled(), "Thread symmetry can only be computed after compilation.");
            threadSymmetry = new ThreadSymmetry(program);
        }
        return threadSymmetry;
    }

    public ProgramEncoder getProgramEncoder() {
        return progEncoder;
    }

    public MemoryEncoder getMemoryEncoder() { return memoryEncoder; }


    // ===================== Utility Methods ====================

    public void preprocessProgram() {
        logger.info("#Events: " + program.getEvents().size());
        try {
            ProcessingManager.fromConfig(config).run(program);
            branchEquivalence = new BranchEquivalence(program, config);
        } catch (InvalidConfigurationException ex) {
            logger.warn("Configuration error when processing program. Some processing steps may have been skipped.");
        }

		aliasAnalysis = new AliasAnalysis();
		try {
			config.inject(aliasAnalysis);
		} catch(InvalidConfigurationException e) {
			logger.warn(e.getMessage());
		}
		aliasAnalysis.calculateLocationSets(program);

        if (debugPrint) {
            for (Thread t : program.getThreads()) {
                System.out.println("========== Thread " + t.getId() + " ==============");
                for (Event e : t.getEntry().getSuccessors()) {
                    String indent = ((e instanceof Label) ? "" : "   ");
                    System.out.printf("%4d: %s%s%n", e.getCId(), indent, e);
                }
            }
        }
    }

    public void initialiseEncoding(SolverContext ctx) {
        progEncoder.initialise(this, ctx);
        memoryEncoder.initialise(this, ctx);
        memoryModel.initialise(this, ctx);
    }

    public BooleanFormula encodeProgram(SolverContext ctx) {
        BooleanFormula memEncoding = memoryEncoder.encodeMemory(ctx);
    	BooleanFormula cfEncoding = progEncoder.encodeControlFlow(ctx);
    	BooleanFormula finalRegValueEncoding = progEncoder.encodeFinalRegisterValues(ctx);
        return ctx.getFormulaManager().getBooleanFormulaManager().and(memEncoding, cfEncoding, finalRegValueEncoding);
    }

    public BooleanFormula encodeWmmRelations(SolverContext ctx) {
        return memoryModel.encodeRelations( ctx);
    }

    public BooleanFormula encodeWmmConsistency(SolverContext ctx) {
        return memoryModel.encodeConsistency(ctx);
    }

	public BooleanFormula encodeSymmetryBreaking(SolverContext ctx) {
		return new SymmetryBreaking(this).encode(ctx);
	}

    public BooleanFormula encodeAssertions(SolverContext ctx) {
        return progEncoder.encodeAssertions(ctx);
    }

    public BooleanFormula encodeWitness(SolverContext ctx) {
    	return witness.encode(program, ctx);
    }
}