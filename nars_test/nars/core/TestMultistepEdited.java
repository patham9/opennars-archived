package nars.core;

import nars.NAR;
import nars.config.Plugins;
import nars.io.handlers.TextOutputHandler;
import nars.core.NALTest;
import org.junit.Test;

/**
 * Example-MultiStep-edited.txt
 * @author me
 */
public class TestMultistepEdited {

    @Test
    public void testMultistepEndState() {
        NAR n = new NAR(new Plugins());
        n.addInputFile("nal/Examples/Example-MultiStep-edited.txt");        
        new TextOutputHandler(n, System.out);
        /*InferenceLogger logger = new InferenceLogger(System.out);
        n.memory.setRecorder(logger);*/

        
        n.cycles(1000);
        //System.out.println(n.memory.concepts);
        
    }
            
}
