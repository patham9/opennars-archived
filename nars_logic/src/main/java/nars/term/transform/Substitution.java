package nars.term.transform;

import com.gs.collections.impl.map.mutable.UnifiedMap;
import nars.nal.meta.Ellipsis;
import nars.nal.nal4.Product;
import nars.term.Compound;
import nars.term.Term;

import java.util.Map;
import java.util.function.Function;

/** holds a substitution and any metadata that can eliminate matches as early as possible */
public class Substitution implements Function<Compound,Term> {

    public Map<? extends Term, Term> subs;


    int appliesTo;

    /** creates a substitution of one variable; more efficient than supplying a Map */
    public Substitution(Term termFrom, Term termTo) {
        this(UnifiedMap.newWithKeysValues(termFrom, termTo));
    }

    public Substitution() {
        reset();
    }

    public Substitution(final Map<? extends Term, Term> subs) {
        reset(subs);
    }

    /** reset but keep the same map */
    public Substitution reset() {
        this.appliesTo = -1;
        return this;
    }

    public Substitution reset(final Map<? extends Term, Term> subs) {
        this.subs = subs;
        return reset();
    }

//    /** call if the map has changed (ex: during re-use) */
//    public void prepare() {
//        int appliesTo = 0;
//        for (final Map.Entry<? extends Term, Term> e : subs.entrySet()) {
//            Op op = e.getKey().op();
//            if (op!=Op.VAR_PATTERN)
//                appliesTo |= op.bit();
//        }
//        this.appliesTo = appliesTo;
//    }

    /** if eliminates all conditions with regard to a specific compound */
    public final boolean isApplicable(final Term t) {
        //there exist variable types that can match, and the term can theoretically equal or contain it
        return t.hasAny(appliesTo);
    }


    /** gets the substitute */
    final public Term get(final Term t) {
        return subs.get(t);
    }

    @Override public final Term apply(final Compound c) {
        //TODO optimization exclusion conditions, currently broke
        /*if (appliesTo < 0) {
            prepare();
            if (!isApplicable(c))
                return c;
        }*/

        return _apply(c);
    }

    public final Term _apply(final Compound c) {

        /*if (!isApplicable(c))
            return c;*/




        final int len = c.size();
        final int targetLen = getResultSize(c);
        if (len == -1) return c;

        /** result */
        final Term[] sub = new Term[targetLen];
        boolean changed = targetLen!=len;

        int j = 0;
        for (int i = 0; i < len; i++) {
            //t holds the
            final Term t = c.term(i);

            if (t instanceof Ellipsis) {

                changed = true;

                Term[] expansion = ((Product)get(t)).terms();

                final int es = expansion.length;

                for (int e = 0; e < es; ) {
                    Term xx = expansion[e++];
                    if (xx==Ellipsis.Expand)
                        continue; //ignore any '..' which may be present in the expansion
                    sub[j++] = xx;
                }
            } else if (t == Ellipsis.Expand) {
                continue; //skip
            } else {
                // s holds a replacement substitution for t (i-th subterm of c)
                Term s;

                //attempt 1: apply known substitution
                if ((s = get(t)) != null) {

                    //prevents infinite recursion
                    if (s.containsTerm(t))
                        s = null;
                }

                //attempt 2: if substitution still not found, recurse if subterm is compound term
                if (s == null && (t instanceof Compound)) { //additional constraint here?
                    s = _apply((Compound) t);

                    if (s == null) {
                        //null means the clone at the end of this method failed,
                        //so the resulting substituted term would be invalid
                        return c;
                    }

                }

                //if substitute found
                if (s != null) {
                    //replace the value at the current index
                    changed |= !(t.equals(s));
                }
                else {
                    s = t;
                }

                sub[j++] = s;
            }
        }

        if (!changed) {
            //a new Term[] was not created, meaning nothing changed. return the input term
            return c;
        }

        return c.clone(sub);
    }

    private int getResultSize(Compound c) {
        int s = c.size();
        int n = s;
        for (int i = 0; i < s; i++) {
            Term t = c.term(i);
            if (t == Ellipsis.Expand) n--; //skip expansion placeholder terms
            if (t instanceof Ellipsis) {
                Term expanded = subs.get(t);
                if (expanded == null) return -1; //missing ellipsis match
                n += expanded.size() - 1; //-1 for the existing term already accounted for
            }
        }
        return n;
    }

    @Override
    public String toString() {
        return "Substitution{" +
                "subs=" + subs +
                '}';
    }
}


//        /* collapse a substitution map to each key's ultimate destination
//         *  in the case of values that are equal to other keys */
//            if (numSubs >= 2) {
//                final Term o = e.getValue(); //what the original mapping of this entry's key
//
//                Term k = o, prev = o;
//                int hops = 1;
//                while ((k = subs.getOrDefault(k, k)) != prev) {
//                    prev = k;
//                    if (hops++ == numSubs) {
//                        //cycle detected
//                        throw new RuntimeException("Cyclical substitution map: " + subs);
//                    }
//                }
//                if (!k.equals(o)) {
//                    //replace with the actual final mapping
//                    e.setValue(k);
//                }
//            }
