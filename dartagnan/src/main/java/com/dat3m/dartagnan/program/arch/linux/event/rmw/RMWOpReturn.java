package com.dat3m.dartagnan.program.arch.linux.event.rmw;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.expression.IExprBin;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Seq;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.event.rmw.RMWLoad;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.wmm.utils.Arch;

public class RMWOpReturn extends RMWAbstract implements RegWriter, RegReaderData {

    private IOpBin op;

    public RMWOpReturn(IExpr address, Register register, ExprInterface value, IOpBin op, String atomic) {
        super(address, register, value, atomic);
        this.op = op;
    }

    @Override
    public Thread compile(Arch target) {
        if(target == Arch.NONE) {
            Register dummy = new Register(null, resultRegister.getThreadId());
            RMWLoad load = new RMWLoad(dummy, address, getLoadMO());
            Local local = new Local(resultRegister, new IExprBin(dummy, op, value));
            RMWStore store = new RMWStore(load, address, resultRegister, getStoreMO());

            compileBasic(load);
            compileBasic(store);

            Thread result = new Seq(load, new Seq(local, store));
            return insertFencesOnMb(result);
        }
        return super.compile(target);
    }

    @Override
    public String toString() {
        return nTimesCondLevel() + resultRegister + " := atomic_" + op.toLinuxName() + "_return" + atomicToText(atomic) + "(" + value + ", " + address + ")";
    }

    @Override
    public RMWOpReturn clone() {
        if(clone == null){
            clone = new RMWOpReturn(address, resultRegister, value, op, atomic);
            afterClone();
        }
        return (RMWOpReturn)clone;
    }
}
