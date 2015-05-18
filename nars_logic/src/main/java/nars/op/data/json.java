package nars.op.data;

import nars.nal.nal3.SetExt;
import nars.nal.nal4.Product;
import nars.nal.nal8.TermFunction;
import nars.nal.term.Atom;
import nars.nal.term.Compound;
import nars.nal.term.Term;
import nars.util.language.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by me on 5/18/15.
 */
public class json {

    public static class jsonto extends TermFunction {

        @Override
        public Object function(Term... x) {
            String j = Atom.unquote(x[0]);
            Map<String, Object> jj = JSON.toMap(j);
            if (jj==null) return null;

            return termize(jj);
        }


        public static Term termize(Object x) {
            if (x instanceof Map) {
                Map<String, Object> jj = (Map<String, Object>) x;
                List<Term> tt = new ArrayList();

                for (Map.Entry<String,Object> e : jj.entrySet()) {
                    String key = e.getKey();
                    Object data = e.getValue();
                    tt.add(Product.make(Atom.get(key), termize(data)));
                }

                Compound s = SetExt.make(tt);
                return s;
            }
            else if (x instanceof String) {
                return Atom.get((String)x);
            }
            else /*if (x instanceof Number)*/ {
                return Atom.get("\"" + x.toString() + "\"");
            }
        }

    }
    public static class jsonfrom extends TermFunction {

        @Override
        public Object function(Term... x) {
            return Atom.quoted(JSON.stringFrom(x[0]));
        }

    }


}
