package nars.nal;

import nars.nar.Default;
import nars.term.TermContainer;
import org.junit.Ignore;
import org.junit.Test;

import static nars.$.$;
import static nars.$.sete;
import static org.junit.Assert.assertEquals;

/**
 * Created by me on 12/12/15.
 */
public class TrieDeriverTest {

    @Ignore
    @Test
    public void testNAL3Rule() {
        /* outputs = */ testRule(
            "((|,X,A..+) --> M), M |- (X --> M), (Truth:StructuralDeduction)",
            "<(|, boy, girl) --> youth>."
        );
    }

    public void testRule(String rule, String... inputs) {
        TrieDeriver d = new TrieDeriver(
            rule
        );

        /*d.rules.forEach((Consumer<? super PremiseRule>) x -> {
            System.out.println(x);
            System.out.println("\t" + x.source);
        });
        System.out.println(d);*/

        assertEquals(2, d.rules.size());

        Default x = new Default() {
            @Override
            protected Deriver newDeriver() {
                return d;
            }
        };
        x.log();
        for (String i : inputs)
            x.input(i);

        x.frame(100);

    }

    @Test public void testDifference() {
        /*tester.believe("<planetX --> {Mars,Pluto,Venus}>",0.9f,0.9f); //.en("PlanetX is Mars, Pluto, or Venus.");
        tester.believe("<planetX --> {Pluto,Saturn}>", 0.1f, 0.9f); //.en("PlanetX is probably neither Pluto nor Saturn.");
        tester.mustBelieve(cycles, "<planetX --> {Mars,Venus}>", 0.81f ,0.81f); //.en("PlanetX is either Mars or Venus.");*/


        assertEquals(
            $("{Mars,Venus}"),
            sete(TermContainer.difference(
                $("{Mars,Pluto,Venus}"),
                $("{Pluto,Saturn}")
            ))
        );



//        //test identity does not create new instance, single term
//        Compound b = $("{Mars}");
//        assertTrue(
//                b ==
//                TermContainer.difference(
//                        b,
//                        $("{Pluto}")
//                )
//        );
//
//        //test identity does not create new instance, multiterm
//        Compound a = $("{Mars,Venus}");
//        assertTrue(
//                a ==
//                        TermContainer.difference(
//                                a,
//                                $("{Pluto,PlanetX}")
//                        )
//        );
    }

}