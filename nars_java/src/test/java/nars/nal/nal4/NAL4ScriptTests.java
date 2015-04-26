package nars.nal.nal4;


import nars.prototype.Curve;
import nars.prototype.Default;
import nars.ProtoNAR;
import nars.nal.ScriptNALTest;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static nars.io.ExampleFileInput.getParams;

public class NAL4ScriptTests extends ScriptNALTest {

    public NAL4ScriptTests(ProtoNAR b, String input) {
        super(b, input);
    }

    @Parameterized.Parameters(name= "{1} {0}")
    public static Collection configurations() {
        return getParams(new String[]{"test4"},
                new Default(),
                new Default().setInternalExperience(null),
                new Default().level(4),
                new Curve(),
                new Curve().setInternalExperience(null)
                //new Solid(1, 64, 1, 4, 1, 3).setInternalExperience(null)
        );

    }

    public int getMaxCycles() { return 700; }


}

