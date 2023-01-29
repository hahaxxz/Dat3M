package com.dat3m.dartagnan.c;

import com.dat3m.dartagnan.configuration.Arch;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.rules.CSVLogger;
import com.dat3m.dartagnan.utils.rules.Provider;
import com.dat3m.dartagnan.verification.solving.AssumeSolver;
import com.dat3m.dartagnan.verification.solving.RefinementSolver;
import com.dat3m.dartagnan.wmm.Wmm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.dat3m.dartagnan.configuration.Arch.IMM;
import static com.dat3m.dartagnan.utils.ResourceHelper.CAT_RESOURCE_PATH;
import static com.dat3m.dartagnan.utils.ResourceHelper.TEST_RESOURCE_PATH;
import static com.dat3m.dartagnan.utils.Result.*;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class IMMLocksTest extends AbstractCTest {

    public IMMLocksTest(String name, Arch target, Result expected) {
        super(name, target, expected);
    }

    @Override
    protected Provider<String> getProgramPathProvider() {
        return Provider.fromSupplier(() -> TEST_RESOURCE_PATH + "locks/" + name + ".bpl");
    }

    @Override
    protected long getTimeout() {
        return 60000;
    }

    @Override
    protected Provider<Wmm> getWmmProvider() {
        return Provider.fromSupplier(() -> new ParserCat().parse(new File(CAT_RESOURCE_PATH + "cat/imm.cat")));
    }

    @Parameterized.Parameters(name = "{index}: {0}, target={1}")
    public static Iterable<Object[]> data() throws IOException {
        return Arrays.asList(new Object[][]{
                {"ttas", IMM, UNKNOWN},
                {"ttas-acq2rx", IMM, FAIL},
                {"ttas-rel2rx", IMM, FAIL},
                {"ticketlock", IMM, PASS},
                {"ticketlock-acq2rx", IMM, FAIL},
                {"ticketlock-rel2rx", IMM, FAIL},
                {"mutex", IMM, UNKNOWN},
                {"mutex-acq2rx_futex", IMM, UNKNOWN},
                {"mutex-acq2rx_lock", IMM, FAIL},
                {"mutex-rel2rx_futex", IMM, UNKNOWN},
                {"mutex-rel2rx_unlock", IMM, FAIL},
                {"spinlock", IMM, PASS},
                {"spinlock-acq2rx", IMM, FAIL},
                {"spinlock-rel2rx", IMM, FAIL},
                {"linuxrwlock", IMM, UNKNOWN},
                {"linuxrwlock-acq2rx", IMM, FAIL},
                {"linuxrwlock-rel2rx", IMM, FAIL},
                {"mutex_musl", IMM, UNKNOWN},
                {"mutex_musl-acq2rx_futex", IMM, UNKNOWN},
                {"mutex_musl-acq2rx_lock", IMM, FAIL},
                {"mutex_musl-rel2rx_futex", IMM, UNKNOWN},
                {"mutex_musl-rel2rx_unlock", IMM, FAIL},
                {"seqlock", IMM, PASS},
        });
    }

    //    @Test
    @CSVLogger.FileName("csv/assume")
    public void testAssume() throws Exception {
        AssumeSolver s = AssumeSolver.run(contextProvider.get(), proverProvider.get(), taskProvider.get());
        assertEquals(expected, s.getResult());
    }

    @Test
    @CSVLogger.FileName("csv/refinement")
    public void testRefinement() throws Exception {
        RefinementSolver s = RefinementSolver.run(contextProvider.get(), proverProvider.get(), taskProvider.get());
        assertEquals(expected, s.getResult());
    }
}