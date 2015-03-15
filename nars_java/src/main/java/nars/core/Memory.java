/*
 * Memory.java
 *
 * Copyright (C) 2008  Pei Wang
 *
 * This file is part of Open-NARS.
 *
 * Open-NARS is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Open-NARS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open-NARS.  If not, see <http://www.gnu.org/licenses/>.
 */
package nars.core;

import nars.core.Events.ResetStart;
import nars.core.Events.Restart;
import nars.core.Events.TaskRemove;
import nars.event.EventEmitter;
import nars.event.Reaction;
import nars.io.Symbols;
import nars.io.meter.EmotionMeter;
import nars.io.meter.LogicMeter;
import nars.io.meter.ResourceMeter;
import nars.logic.*;
import nars.logic.entity.*;
import nars.logic.entity.stamp.Stamp;
import nars.logic.nal1.Inheritance;
import nars.logic.nal1.Negation;
import nars.logic.nal2.Similarity;
import nars.logic.nal3.*;
import nars.logic.nal4.Image;
import nars.logic.nal4.ImageExt;
import nars.logic.nal4.ImageInt;
import nars.logic.nal4.Product;
import nars.logic.nal5.Conjunction;
import nars.logic.nal5.Disjunction;
import nars.logic.nal5.Equivalence;
import nars.logic.nal5.Implication;
import nars.logic.nal7.Interval;
import nars.logic.nal7.TemporalRules;
import nars.logic.nal8.ImmediateOperation;
import nars.logic.nal8.Operation;
import nars.logic.nal8.Operator;
import nars.operator.app.plan.MultipleExecutionManager;
import reactor.function.Supplier;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memory consists of the run-time state of a NAR, including: * term and concept
 * memory * clock * reasoner state * etc.
 *
 * Excluding input/output channels which are managed by a NAR.
 *
 * A memory is controlled by zero or one NAR's at a given time.
 *
 * Memory is serializable so it can be persisted and transported.
 */
public class Memory implements Serializable {

    @Deprecated
    public final MultipleExecutionManager executive; //used for implication graph and for planner plugin, todo 
    //get it out to plugin somehow

    private boolean enabled = true;

    private long timeRealStart;
    private long timeRealNow;
    private long timePreviousCycle;
    private long timeSimulation;
    private int level;

    public NALRuleEngine rules;
    private Term self;

    public void setLevel(int nalLevel) {
        if ((nalLevel < 1) || (nalLevel > 8))
            throw new RuntimeException("NAL level must be between 1 and 8 (inclusive)");
        this.level = nalLevel;
    }

    public int nal() {
        return level;
    }

    public Concept concept(CharSequence t) {
        return concept(Term.get(t));
    }


    /** provides fast iteration to concepts with questions */
    public Set<Concept> getQuestionConcepts() {
        return questionConcepts;
    }

    /** provides fast iteration to concepts with goals */
    public Set<Concept> getGoalConcepts() {
        return goalConcepts;
    }

    /** allows an external component to signal to the memory that data is available.
     * default implementation now just absorbs all the data but different policies
     * could be implemented (ex: round robin) which will be
     * more important when heavier data flow occurrs
     */
    public void taskAdd(final Supplier<Task> source) {
        Task next;
        while ((next = source.get())!=null) {
            taskAdd(next);
        }
    }

    public void taskAdd(final Iterable<Task> source) {
        for (final Task t : source)
            taskAdd(t);
    }

    public Term getSelf() {
        return self;
    }

    public void setSelf(Term t) {
        this.self = t;
    }



    @Deprecated public static enum Forgetting {
        @Deprecated Iterative,
        Periodic
    }

    public enum Timing {

        /**
         * internal, subjective time (logic steps)
         */
        Iterative,

        //TODO absolute realtime - does not cache a value throughout a cycle, but each call to time() yields an actual realtime measurement so that multiple time() calls during cycle may return different (increasing) values

        /**
         * actual real-time, uses system clock; time value is cached at beginning of each cycle for the remainder of it
         */
        Real,
        /**
         * simulated real-time, uses controlled simulation time
         */
        Simulation
    }

    Timing timing;



    private static long defaultRandomSeed = 1;
    public static final Random randomNumber = new Random(defaultRandomSeed);
            //new XORShiftRandom(defaultRandomSeed); //not thread safe but faster


    public static void resetStatic(long randomSeed) {
        randomNumber.setSeed(randomSeed);
    }


    private final Deque<Runnable> nextTasks = new ConcurrentLinkedDeque();

    public final Perception perception;
    public final Core concepts;
    private final Set<Concept> questionConcepts = Parameters.newHashSet(16);
    private final Set<Concept> goalConcepts = Parameters.newHashSet(16);

    public final EventEmitter event;

    /* InnateOperator registry. Containing all registered operators of the system */
    public final HashMap<CharSequence, Operator> operators;

    private long currentStampSerial = 0;



    //public final Term self;
    public final EmotionMeter emotion = new EmotionMeter();
    public final LogicMeter logic;
    public final ResourceMeter resource;

    /**
     * The remaining number of steps to be carried out (stepLater mode)
     */
    private int inputPausedUntil = -1;

    /**
     * System clock, relatively defined to guarantee the repeatability of
     * behaviors
     */
    private long cycle;

    public final Param param;

    ExecutorService laterTasks = null;

    /* ---------- Constructor ---------- */
    /**
     * Create a new memory
     *
     * added during runtime
     */
    public Memory(int nalLevel, Param param, Core concepts) {

        this.level = nalLevel;

        this.param = param;

        this.perception = new Perception();

        this.rules = new NALRuleEngine(this);

        this.concepts = concepts;
        this.concepts.init(this);

        this.self = Symbols.DEFAULT_SELF; //default value

        this.operators = new HashMap<>();

        this.resource = new ResourceMeter();
        this.logic = new LogicMeter();

        this.event = new EventEmitter();


        this.executive = new MultipleExecutionManager(this);

        //after this line begins actual logic, now that the essential data strucures are allocated
        //------------------------------------ 
        reset(false);

        this.event.set(conceptIndices, true, Events.ConceptQuestionAdd.class, Events.ConceptQuestionRemove.class, Events.ConceptGoalAdd.class, Events.ConceptGoalRemove.class, Events.ConceptRemember.class, Events.ConceptForget.class, Events.ConceptNew.class );
    }


    /** handles maintenance of concept question/goal indices when concepts change according to reports by certain events */
    private final Reaction conceptIndices = new Reaction() {

        @Override
        public void event(Class event, Object[] args) {

            if ((event == Events.ConceptForget.class) || (event == Events.ConceptNew.class) || (event == Events.ConceptRemember.class)) {
                Concept c = (Concept)args[0];
                if (c.questions.isEmpty()) questionConcepts.remove(c);
                else questionConcepts.add(c);
                if (c.goals.isEmpty())  goalConcepts.remove(c);
                else goalConcepts.add(c);
                return;
            }


            //TODO this may also be triggered by Quests; may want to distinguish them with a different event for Quests
            if ((event == Events.ConceptQuestionAdd.class) || (event == Events.ConceptQuestionRemove.class)) {
                Concept c = (Concept)args[0];
                Task incoming = args.length > 2 ? (Task)args[2] : null; //non-null indicates that a Add will be following this removal event
                if (incoming==null && c.questions.isEmpty())
                    questionConcepts.remove(c);
                else if (c.questions.size() == 1)
                    questionConcepts.add(c);
            }
            if ((event == Events.ConceptGoalAdd.class) || (event == Events.ConceptGoalRemove.class)) {
                Concept c = (Concept)args[0];
                Task incoming = args.length > 2 ? (Task)args[2] : null; //non-null indicates that a Add will be following this removal event
                if (incoming==null && c.goals.isEmpty())
                    goalConcepts.remove(c);
                else if (c.goals.size() == 1)
                    goalConcepts.add(c);
            }

        }
    };

    public void reset(boolean resetInputs) {

        if (resetInputs)
            perception.reset();

        event.emit(ResetStart.class);

        concepts.reset();

        timing = param.getTiming();
        cycle = 0;
        timeRealStart = timeRealNow = System.currentTimeMillis();
        timePreviousCycle = time();

        inputPausedUntil = -1;

        questionConcepts.clear();
        goalConcepts.clear();

        emotion.set(0.5f, 0.5f);

        event.emit(Restart.class);


    }

    public long time() {
        switch (timing) {
            case Iterative:
                return timeCycle();
            case Real:
                return timeReal();
            case Simulation:
                return timeSimulation();
        }
        return 0;
    }

    public int duration() {
        return param.duration.get();
    }

    /**
     * internal, subjective time (logic steps)
     */
    public long timeCycle() {
        return cycle;
    }

    /**
     * hard real-time, uses system clock
     */
    public long timeReal() {
        return timeRealNow - timeRealStart;
    }

    /**
     * soft real-time, uses controlled simulation time
     */
    public long timeSimulation() {
        return timeSimulation;
    }

    public void timeSimulationAdd(long dt) {
        timeSimulation += dt;
    }

    /**
     * difference in time since last cycle
     */
    public long timeSinceLastCycle() {
        return time() - timePreviousCycle;
    }


    /* ---------- conversion utilities ---------- */
    /**
     * Get an existing Concept for a given name
     * <p>
     * called from Term and ConceptWindow.
     *
     * @param t the name of a concept
     * @return a Concept or null
     */
    public Concept concept(Term t) {
        if (!t.isNormalized()) {
            t = ((CompoundTerm)t).cloneNormalized();
        }
        return concepts.concept(t);
    }



    /**
     * Get the Concept associated to a Term, or create it.
     *
     * Existing concept: apply tasklink activation (remove from bag, adjust
     * budget, reinsert) New concept: set initial activation, insert Subconcept:
     * extract from cache, apply activation, insert
     *
     * If failed to insert as a result of null bag, returns null
     *
     * A displaced Concept resulting from insert is forgotten (but may be stored
     * in optional subconcept memory
     *
     * @param term indicating the concept
     * @return an existing Concept, or a new one, or null
     */
    public Concept conceptualize(final BudgetValue budget, final Term term) {

        if ((term instanceof Variable) || (term instanceof Interval))
            return null;

        /*Concept c = concept(term);
         if (c!=null)
         System.out.print(c.budget + "   ");
         System.out.println(term + " conceptualize: " + budget);*/
        return concepts.conceptualize(budget, term, true);
    }

    /**
     * Get the current activation level of a concept.
     *
     * @param t The Term naming a concept
     * @return the priority value of the concept
     */
    public float conceptPriority(final Term t) {
        final Concept c = concept(t);
        return (c == null) ? 0f : c.getPriority();
    }


    /* static methods making new compounds, which may return null */
    /**
     * Try to make a compound term from a template and a list of term
     *
     * @param compound The template
     * @param components The term
     * @return A compound term or null
     */
    public static Term term(final CompoundTerm compound, final Term[] components) {
        if (compound instanceof ImageExt) {
            return new ImageExt(components, ((Image) compound).relationIndex);
        } else if (compound instanceof ImageInt) {
            return new ImageInt(components, ((Image) compound).relationIndex);
        } else {
            return term(compound.operator(), components);
        }
    }

    public static Term term(final CompoundTerm compound, Collection<Term> components) {
        Term[] c = components.toArray(new Term[components.size()]);
        return term(compound, c);
    }

    private static boolean ensureTermLength(int num, Term[] a) {
        return (a.length==num);
        /*if (a.length!=num)
            throw new CompoundTerm.InvalidTermConstruction("Expected " + num + " args to create Term from " + Arrays.toString(a));*/
    }

    /**
     * Try to make a compound term from an operator and a list of term
     * <p>
     * Called from StringParser
     *
     * @param op Term operator
     * @return A term or null
     */
    public static Term term(final NALOperator op, final Term... a) {


        switch (op) {

            case SET_EXT_OPENER:
                return SetExt.make(a);
            case SET_INT_OPENER:
                return SetInt.make(a);
            case INTERSECTION_EXT:
                return IntersectionExt.make(a);
            case INTERSECTION_INT:
                return IntersectionInt.make(a);
            case DIFFERENCE_EXT:
                return DifferenceExt.make(a);
            case DIFFERENCE_INT:
                return DifferenceInt.make(a);
            case PRODUCT:
                return new Product(a);
            case IMAGE_EXT:
                return ImageExt.make(a);
            case IMAGE_INT:
                return ImageInt.make(a);
            case NEGATION:
                return Negation.make(a);
            case DISJUNCTION:
                return Disjunction.make(a);
            case CONJUNCTION:
                return Conjunction.make(a);
            case SEQUENCE:
                return Conjunction.make(a, TemporalRules.ORDER_FORWARD);
            case PARALLEL:
                return Conjunction.make(a, TemporalRules.ORDER_CONCURRENT);

            case OPERATION:
                throw new RuntimeException("Can not use this static method to instantiate an Operation, because a Memory instance is required to provide its Operator");

            //STATEMENTS --------------------------
            case INHERITANCE:
                if (ensureTermLength(2, a)) return Inheritance.makeTerm(a[0], a[1]); break;

            case SIMILARITY:
                if (ensureTermLength(2, a)) return Similarity.makeTerm(a[0], a[1]); break;

            case IMPLICATION:
                if (ensureTermLength(2, a)) return Implication.makeTerm(a[0], a[1]); break;
            case IMPLICATION_AFTER:
                if (ensureTermLength(2, a)) return Implication.make(a[0], a[1], TemporalRules.ORDER_FORWARD); break;
            case IMPLICATION_BEFORE:
                if (ensureTermLength(2, a)) return Implication.make(a[0], a[1], TemporalRules.ORDER_BACKWARD); break;
            case IMPLICATION_WHEN:
                if (ensureTermLength(2, a)) return Implication.make(a[0], a[1], TemporalRules.ORDER_CONCURRENT); break;

            case EQUIVALENCE:
                if (ensureTermLength(2, a)) return Equivalence.makeTerm(a[0], a[1]); break;
            case EQUIVALENCE_WHEN:
                if (ensureTermLength(2, a)) return Equivalence.make(a[0], a[1], TemporalRules.ORDER_CONCURRENT); break;
            case EQUIVALENCE_AFTER:
                if (ensureTermLength(2, a)) return Equivalence.make(a[0], a[1], TemporalRules.ORDER_FORWARD); break;

            default:
                throw new RuntimeException("Unknown Term operator: " + op + " (" + op.name() + ')');
        }

        return null;
    }

    /**
     * this will not remove a concept. it is not good to use directly because it
     * can disrupt the bag's priority order. it should only be used after it has
     * been removed then before inserted
     */
    public void forget(final Item x, final float forgetCycles, final float relativeThreshold) {
        /*switch (param.forgetting) {
            case Iterative:
                BudgetFunctions.forgetIterative(x.budget, forgetCycles, relativeThreshold);
                break;
            case Periodic:*/
                BudgetFunctions.forgetPeriodic(x.budget, forgetCycles, relativeThreshold, time());
                //break;
        //}
    }

    public boolean taskAdd(final Task t) {
        return taskAdd(t, null);
    }


    /* ---------- new task entries ---------- */
    /**
     * add new task that waits to be processed next
     */
    public boolean taskAdd(final Task t, final String reason) {

        if (Parameters.DEBUG) {
            if (t.sentence != null && t.sentence.stamp.getOccurrenceTime() < -999999 && !t.sentence.isEternal())
                throw new RuntimeException("Probably invalid occurence time:\n" + t.getExplanation());
        }

        if (reason!=null)
            t.addHistory(reason);

        /* process ImmediateOperation and Operations of ImmediateOperators */
        final Term taskTerm = t.getTerm();
        if (taskTerm instanceof Operation) {
            Operation o = (Operation) taskTerm;
            o.setTask(t);


            if (o instanceof ImmediateOperation) {
                if (t.sentence!=null && t.getPunctuation()!= Symbols.GOAL)
                    throw new RuntimeException("ImmediateOperation " + o + " was not specified with goal punctuation");

                ImmediateOperation i = (ImmediateOperation) t.getTerm();
                i.execute(this);
                return false;
            }
            else if (o.getOperator().isImmediate()) {
                if (t.sentence!=null && t.getPunctuation()!= Symbols.GOAL)
                    throw new RuntimeException("ImmediateOperator call " + o + " was not specified with goal punctuation");

                o.getOperator().call(o, this);
                return false;
            }
        }

        if (!t.budget.aboveThreshold()) {
            taskRemoved(t, "Insufficient budget");
            return false;
        }

        if (!Terms.levelValid(t.sentence, nal())) {
            taskRemoved(t, "Insufficient NAL level");
            return false;
        }


        concepts.addTask(t);


        emit(Events.TaskAdd.class, t, reason);
        logic.TASK_ADD_NEW.hit();

        return true;
    }

    /* There are several types of new tasks, all added into the
     newTasks list, to be processed in the next cycleMemory.
     Some of them are reported and/or logged. */
    /**
     * Input task processing. Invoked by the outside or inside environment.
     * Outside: StringParser (addInput); Inside: InnateOperator (feedback).
     * Input tasks with low priority are ignored, and the others are put into
     * task buffer.
     *
     * @param task The addInput task
     * @return how many tasks were queued to newTasks
     */
    public int taskInput(final Task task) {

        if (task.sentence!=null) {
            //if a task has an unperceived creationTime,
            // set it to the memory's current time here,
            // and adjust occurenceTime if it's not eternal
            Stamp s = task.sentence.stamp;
            if (s.getCreationTime() == Stamp.UNPERCEIVED) {
                final long now = time();
                long oc = s.getOccurrenceTime();
                if (oc!=Stamp.ETERNAL)
                    oc += now;
                task.getStamp().setTime(now, oc);
            }
        }


        emit(Events.IN.class, task);

        if (taskAdd(task, "Perceived"))
            return 1;

        return 0;
    }

    /** called anytime a task has been removed, deleted, discarded, ignored, etc. */
    public void taskRemoved(final Task task, final String removalReason) {
        task.addHistory(removalReason);
        emit(TaskRemove.class, task, removalReason);
        task.end();
    }


    /** sends an event signal to listeners subscribed to channel 'c' */
    final public void emit(final Class c, final Object... signal) {
        event.emit(c, signal);
    }

    /** tells whether a given channel has any listeners that might react to something emitted to it */
    final public boolean emitting(final Class channel) {
        return event.isActive(channel);
    }

    /**
     * enable/disable all I/O and memory processing. CycleStart and CycleStop
     * events will continue to be generated, allowing the memory to be used as a
     * clock tick while disabled.
     */
    public void enable(boolean e) {
        this.enabled = e;
    }

    public boolean isEnabled() {
        return enabled;
    }



    /** attempts to perceive the next input from perception, and
     *  handle it by immediately acting on it, or
     *  adding it to the new tasks queue for future reasoning.
     * @return how many tasks were generated as a result of perceiving, or -1 if no percept was available */
    public int perceiveNext() {
        if (!thinking()) return -1;

        Task t = perception.get();
        if (t != null)
            return taskInput(t);

        return -1;
    }

    /** attempts to perceive at most N perceptual tasks.
     *  this allows Attention to regulate input relative to other kinds of mental activity
     *  if N == -1, continue perceives until perception buffer is emptied
     *  @return how many tasks perceived
     */
    public int perceiveNext(int maxPercepts) {
        if (!thinking()) return 0;

        boolean inputEverything;

        if (maxPercepts == -1) { inputEverything = true; maxPercepts = 1; }
        else inputEverything = false;

        int perceived = 0;
        while (perceived < maxPercepts) {
            int p = perceiveNext();
            if (p == -1) break;
            else if (!inputEverything) perceived += p;
        }
        return perceived;
    }

    /** executes one complete memory cycle (if not disabled) */
    public /*synchronized*/ void cycle() {

        if (!isEnabled()) {
            return;
        }

        event.emit(Events.CycleStart.class);

        concepts.cycle();

        event.emit(Events.CycleEnd.class);

        timeUpdate();

    }

    /**
     * automatically called each cycle
     */
    protected void timeUpdate() {
        timePreviousCycle = time();
        cycle++;
        if (getTiming()==Timing.Real)
            timeRealNow = System.currentTimeMillis();
    }

    /** queues a task to (hopefully) be executed at an unknown time in the future,
     *  in its own thread in a thread pool */
    public void taskLater(Runnable t) {
        if (laterTasks==null) {
            laterTasks = Executors.newFixedThreadPool(1);
        }

        laterTasks.submit(t);
        laterTasks.execute(t);
    }


    /** adds a task to the queue of task which will be executed in batch
     *  at the end of the current cycle.     */
    public void taskNext(Runnable t) {
        nextTasks.addLast(t);
    }

    /** runs all the tasks in the 'Next' queue */
    public void runNextTasks() {
        int originalSize = nextTasks.size();
        if (originalSize == 0) return;

        Core.run(nextTasks, originalSize);
    }

    /** signals an error through one or more event notification systems */
    protected void error(Throwable ex) {
        emit(Events.ERR.class, ex);

        ex.printStackTrace();

        if (Parameters.DEBUG && Parameters.EXIT_ON_EXCEPTION) {
            //throw the exception to the next lower stack catcher, or cause program exit if none exists
            throw new RuntimeException(ex);
        }

    }

    /** returns the operator identified by its name, or null if none such exists */
    public Operator operator(final CharSequence name) {
        return operators.get(name);
    }

    /** adds a operator directly to Memory; the preferred way to do this from
     * external code is through a NAR's Plugin registry (since Operator extends Plugin)*
     */
    Operator operatorAdd(final Operator op) {
        operators.put(op.name(), op);
        return op;
    }

    Operator operatorRemove(final Operator op) {
        return operators.remove(op.name());
    }

    @Override
    public String toString() {
        //final StringBuilder sb = new StringBuilder(1024);
        //sb.append(toStringLongIfNotNull(novelTasks, "novelTasks"))
        //      .append(toStringIfNotNull(newTasks, "newTasks"));
        //.append(toStringLongIfNotNull(getCurrentTask(), "currentTask"))
        //.append(toStringLongIfNotNull(getCurrentBeliefLink(), "currentBeliefLink"))
        //.append(toStringIfNotNull(getCurrentBelief(), "currentBelief"));
        //return sb.toString();
        return super.toString();
    }


    /** produces a new stamp serial #, used to uniquely identify inputs */
    public long newStampSerial() {
        return currentStampSerial++;
    }

    public boolean thinking() {
        return time() >= inputPausedUntil;
    }

    /**
     * Queue additional cycle()'s to the logic process during which no new input will
     * be perceived.  Analogous to closing one's eyes to focus internally for a brief
     * or extended amount of time - but not necessarily sleeping.
     *
     * @param cycles The number of logic steps to think for, will end thinking at time() + cycles unless more thinking is queued
     */
    public void think(final long cycles) {
        inputPausedUntil = (int) (time() + cycles);
    }

    /**
     * get all tasks in the system by iterating all newTasks, novelTasks; does not change or remove any
     * Concept TaskLinks
     */
    public Set<Task> getTasks(boolean includeTaskLinks, boolean includeNewTasks, boolean includeNovelTasks) {

        //TODO estimate size
        Set<Task> t = Parameters.newHashSet(1000);

        if (includeTaskLinks) {
            for (Concept c : concepts) {
                for (TaskLink tl : c.taskLinks) {
                    t.add(tl.targetTask);
                }
            }
        }

        /*
        if (includeNewTasks) {
            t.addAll(newTasks);
        }

        if (includeNovelTasks) {
            for (Task n : novelTasks) {
                t.add(n);
            }
        }
        */

        return t;
    }

    public <T extends CompoundTerm> NewTask<T> newTask(T t) {
        return new NewTask(this, t);
    }
    public <T extends CompoundTerm> NewTask<T> newTask(Sentence<T> s) {
        return new NewTask(this, s);
    }

//    @Deprecated public Task newTask(CompoundTerm content, char sentenceType, float freq, float conf, float priority, float durability) {
//        return newTask(content, sentenceType, freq, conf, priority, durability, (Task) null);
//    }
//
//    @Deprecated public Task newTask(CompoundTerm content, char sentenceType, float freq, float conf, float priority, float durability, final Task parentTask) {
//        return newTask(content, sentenceType, freq, conf, priority, durability, parentTask, Tense.Present);
//    }
//
//    @Deprecated public Task newTask(CompoundTerm content, char sentenceType, float freq, float conf, float priority, float durability, Tense tense) {
//        return newTask(content, sentenceType, freq, conf, priority, durability, null, tense);
//    }
//
//    @Deprecated public Task newTask(CompoundTerm content, char sentenceType, float freq, float conf, float priority, float durability, Task parentTask, Tense tense) {
//        return newTaskAt(content, sentenceType, freq, conf, priority, durability, parentTask, tense, time());
//    }
//
//    @Deprecated public Task newTaskAt(CompoundTerm content, char sentenceType, float freq, float conf, float priority, float durability, Task parentTask, Tense tense, long ocurrenceTime) {
//
//        TruthValue truth = new TruthValue(freq, conf);
//        Sentence sentence = new Sentence(
//                content,
//                sentenceType,
//                truth,
//                new Stamp(this, ocurrenceTime, tense));
//        BudgetValue budget = new BudgetValue(Parameters.DEFAULT_JUDGMENT_PRIORITY, Parameters.DEFAULT_JUDGMENT_DURABILITY, truth);
//        return new Task(sentence, budget, parentTask);
//    }

    /**
     * samples a next concept for processing;
     * may return null if no concept is available depending on the control system
     */
    public Concept conceptNext() {
        return concepts.nextConcept();
    }

    public Timing getTiming() {
        return timing;
    }


    public interface MemoryAware {
        public void setMemory(Memory m);
    }


//    private String toStringLongIfNotNull(Bag<?, ?> item, String title) {
//        return item == null ? "" : "\n " + title + ":\n"
//                + item.toString();
//    }
//
//    private String toStringLongIfNotNull(Item item, String title) {
//        return item == null ? "" : "\n " + title + ":\n"
//                + item.toStringLong();
//    }
//
//    private String toStringIfNotNull(Object item, String title) {
//        return item == null ? "" : "\n " + title + ":\n"
//                + item.toString();
//    }
}