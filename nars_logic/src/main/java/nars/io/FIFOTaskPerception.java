package nars.io;

import nars.NAR;
import nars.task.Task;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * accumulates a buffer of iasks which can be delivered at a specific rate.
 * <p>
 * consists of 2 buffers which are sampled in some policy each cycle
 * <p>
 * "input" - a dequeue in which input tasks are appended
 * in the order they are received
 * <p>
 * "newTasks" - a priority buffer, emptied in batches,
 * in which derived tasks and other feedback accumulate
 * <p>
 * Sub-interfaces
 * <p>
 * Storage
 * <p>
 * Delivery (procedure for cyclical input policy
 */
public class FIFOTaskPerception extends TaskPerception {


    /* ?? public interface Storage { void put(Task t); }*/

    //public final ItemAccumulator<Task> newTasks;

    public final Deque<Task> buffer = new ArrayDeque();


    public FIFOTaskPerception(NAR nar, Predicate<Task> filter, Consumer<Task> receiver) {
        super(nar.memory(), filter, receiver);


    }

    @Override
    public void accept(Task t) {
        if (filter == null || filter.test(t)) {

                if (t.isDeleted()) {
                    throw new RuntimeException("task deleted");
                }

            buffer.add(t);
        }
    }

    @Override
    public void clear() {
        buffer.clear();
    }



    //        @Override
//        public void accept(Task t) {
//            if (t.isInput())
//                percepts.add(t);
//            else {
////                if (t.getParentTask() != null && t.getParentTask().getTerm().equals(t.getTerm())) {
////                } else {
//                    newTasks.add(t);
//                }
//            }
//        }

    /** sends the next batch of tasks to the receiver */
    @Override
    public void send() {


        int s = buffer.size();
        int n = Math.min(s, inputsMaxPerCycle.get()); //counts down successful sends
        int r = n; //actual cycles counted


        //n will be equal to or greater than r
        for (; n > 0 && r > 0; r--) {
            final Task t = buffer.removeFirst();

            if (t.isDeleted()) {
                //the task became deleted while this was in the buffer. no need to repeat Memory.removed
                continue;
            }

            receiver.accept(t);
            n--;
        }

    }

//        protected void runNewTasks() {
//            runNewTasks(newTasks.size()); //all
//        }
//
//        protected void runNewTasks(int max) {
//
//            int numNewTasks = Math.min(max, newTasks.size());
//            if (numNewTasks == 0) return;
//
//            //queueNewTasks();
//
//            for (int n = newTasks.size() - 1; n >= 0; n--) {
//                Task highest = newTasks.removeHighest();
//                if (highest == null) break;
//                if (highest.isDeleted()) continue;
//
//                run(highest);
//            }
//            //commitNewTasks();
//        }


}