package dartagnan.program.event.linux.rcu;

import dartagnan.program.event.Event;
import dartagnan.program.utils.linux.EType;

public class RCUReadLock extends Event {

    public RCUReadLock(){
        this.condLevel = 0;
        this.addFilters(EType.ANY, EType.RCU_LOCK);
    }

    @Override
    public String toString() {
        return "rcu_read_lock";
    }

    @Override
    public String label(){
        return EType.RCU_LOCK;
    }

    @Override
    public RCUReadLock clone() {
        if(clone == null){
            clone = new RCUReadLock();
            afterClone();
        }
        return (RCUReadLock)clone;
    }
}
