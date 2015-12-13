package nars.term;

import com.gs.collections.api.block.predicate.primitive.IntObjectPredicate;
import com.gs.collections.api.set.MutableSet;
import com.gs.collections.impl.factory.Sets;
import nars.Global;
import nars.Op;
import nars.term.compound.Compound;
import nars.util.utf8.ByteBuf;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

import static nars.Symbols.ARGUMENT_SEPARATORbyte;
import static nars.term.compound.GenericCompound.COMPOUND;


/**
 * Methods common to both Term and Subterms
 * T = subterm type
 */
public interface TermContainer<T extends Term> extends Termlike, Comparable, Iterable<T> {

    int varDep();

    int varIndep();

    int varQuery();

    int vars();

    /** gets subterm at index i */
    T term(int i);

    T termOr(int index, T resultIfInvalidIndex);

    T[] termsCopy();

    void setNormalized(boolean b);
    boolean isNormalized();

    default Term[] termsCopy(Term... additional) {
        if (additional.length == 0) return termsCopy();
        return Terms.concat(terms(), additional);
    }

    default MutableSet<Term> toSet() {
        return Sets.mutable.of(terms());
    }
    static MutableSet<Term> intersect(TermContainer a, TermContainer b) {
        return Sets.intersect(a.toSet(),b.toSet());
    }

    /** returns null if empty set; not sorted */
    static Term[] difference(TermContainer a, TermContainer b) {
        if (a.size() == 1 && b.size() == 1) {
            //special case
            if (a.term(0).equals(b.term(0))) return null;
            else return a.terms();
        } else {
            MutableSet dd = Sets.difference(a.toSet(), b.toSet());
            if (dd.isEmpty()) return null;
            return Terms.toArray(dd);
        }
    }

    static TreeSet<Term> differenceSorted(TermContainer a, TermContainer b) {
        TreeSet<Term> t = new TreeSet();
        a.addAllTo(t);
        b.addAllTo(t);
        return t;
    }

    void addAllTo(Collection<Term> set);


    /** expected to provide a non-copy reference to an internal array,
     *  if it exists. otherwise it should create such array.
     *  if this creates a new array, consider using .term(i) to access
     *  subterms iteratively.
     */
    T[] terms();


    default Term[] terms(IntObjectPredicate<T> filter) {
        List<T> l = Global.newArrayList(size());
        int s = size();
        for (int i = 0; i < s; i++) {
            T t = term(i);
            if (filter.accept(i, t))
                l.add(t);
        }
        if (l.isEmpty()) return Terms.Empty;
        return l.toArray(new Term[l.size()]);
    }


    void forEach(Consumer<? super T> action, int start, int stop);


    static Term[] copyByIndex(TermContainer c) {
        int s = c.size();
        Term[] x = new Term[s];
        for (int i = 0; i < s; i++) {
            x[i] = c.term(i);
        }
        return x;
    }


    static String toString(TermContainer t) {
        StringBuilder sb = new StringBuilder("{[(");
        int s = t.size();
        for (int i = 0; i < s; i++) {
            sb.append(t.term(i));
            if (i < s-1)
                sb.append(", ");
        }
        sb.append(")]}");
        return sb.toString();

    }

    /** extract a sublist of terms as an array */
    default Term[] terms(int start, int end) {
        Term[] t = new Term[end-start];
        int j = 0;
        for (int i = start; i < end; i++)
            t[j++] = term(i);
        return t;
    }

    /** follows normal indexOf() semantics; -1 if not found */
    default int indexOf(Term t) {
        if (t == null)
            throw new RuntimeException("not found");
            //return -1;

        int s = size();
        for (int i = 0; i < s; i++) {
            if (t.equals(term(i)))
                return i;
        }
        return -1;
    }


    /** writes subterm bytes, including any attached metadata preceding or following it */
    default void appendSubtermBytes(ByteBuf b) {

        int n = size();

        for (int i = 0; i < n; i++) {
            Term t = term(i);

            if (i != 0) {
                b.add(ARGUMENT_SEPARATORbyte);
            }

            try {
                byte[] bb = t.bytes();
                if (bb.length!=t.bytesLength())
                    System.err.println("wtf");
                b.add(bb);
            }
            catch (ArrayIndexOutOfBoundsException a) {
                System.err.println("Wtf");
            }
        }

    }

    @Override
    default boolean containsTermRecursively(Term target) {

        for (Term x : terms()) {
            if (impossibleSubTermOrEquality(target))
                continue;
            if (x.equals(target)) return true;
            if (x instanceof Compound) {
                if (x.containsTermRecursively(target)) {
                    return true;
                }
            }
        }
        return false;

    }

    default boolean equivalent(List<Term> sub) {
        int s = size();
        if (s!=sub.size()) return false;
        for (int i = 0; i < size(); i++) {
            if (!term(i).equals(sub.get(i))) return false;
        }
        return true;
    }

    static Term intersect(Op resultOp, Compound a, Compound b) {
        MutableSet<Term> i = intersect(a, b);
        if (i.isEmpty()) return null;
        return COMPOUND(resultOp, i);
    }
    static Term difference(Op resultOp, Compound a, Compound b) {
        Term[] i = difference(a, b);
        if (i==null) return null;
        return COMPOUND(resultOp, i);
    }

    static Term union(Op op, Compound a, Compound b) {
        MutableSet<Term> s = a.toSet();
        s.addAll(b.toSet());
        return COMPOUND(op, s);
    }

}
