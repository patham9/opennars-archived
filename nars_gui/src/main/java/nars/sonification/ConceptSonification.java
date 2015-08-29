package nars.sonification;

import automenta.vivisect.Audio;
import automenta.vivisect.Video;
import automenta.vivisect.audio.SoundProducer;
import automenta.vivisect.audio.granular.Granulize;
import automenta.vivisect.audio.synth.SineWave;
import nars.Events;
import nars.Global;
import nars.NAR;
import nars.concept.Concept;
import nars.event.ConceptReaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sonifies the activity of concepts being activated and forgotten
 */
public class ConceptSonification extends ConceptReaction {

    List<String> samples;

    private final Audio sound;
    Map<Concept, SoundProducer> playing = Global.newHashMap();
    float audiblePriorityThreshold = 0.8f;


    public ConceptSonification(NAR nar, Audio sound) throws IOException {
        super(nar.memory, true, Events.FrameEnd.class);

        this.sound = sound;

        //Events.ConceptProcessed.class,
            /*Premise f = (Premise)args[0];
            update(f.getConcept());*/


        updateSamples();

        //TODO update all existing concepts on start?
    }

    protected void updateSamples() throws IOException {

        samples = Files.list(Paths.get("/home/me/share/wav")).
                map(p -> p.toAbsolutePath().toString() ).filter( x -> x.endsWith(".wav") ).collect(Collectors.toList());

        Collections.shuffle(samples);
    }

    /** returns file path to load sample */
    String getSample(Concept c) {
        return samples.get(Math.abs(c.hashCode()) % samples.size());
    }

    public void update(Concept c) {
        if (c.getPriority() > audiblePriorityThreshold) {
            SoundProducer g = playing.get(c);
            if (g == null) {
                if (!samples.isEmpty()) {
                    String sp = getSample(c);
                    //do {
                        try {
                            //g = new Granulize(SampleLoader.load(sp), 0.1f, 0.1f);
                            g = new SineWave(Video.hashFloat(c.hashCode()));
                        } catch (Exception e) {
                            samples.remove(sp);
                            g = null;
                            return;
                        }
                    //} while ((g == null) && (!samples.isEmpty()));

                    playing.put(c, g);
                    sound.play(g, 1f, 1);
                }
            }

            if (g!=null)
                update(c, g);
        }
        else {
            SoundProducer g = playing.remove(c);
            if (g!=null)
                g.stop();
        }
    }

    private void update(Concept c, SoundProducer g) {
        if (g instanceof Granulize) {
            ((Granulize)g).setStretchFactor(1f + 4f * (1f - c.getQuality()));
        }
        if (g instanceof SoundProducer.Amplifiable) {
            ((SoundProducer.Amplifiable)g).setAmplitude((c.getPriority() - audiblePriorityThreshold) / (1f - audiblePriorityThreshold));
        }
    }

    protected void updateConceptsPlaying() {
        for (Map.Entry<Concept, SoundProducer> e : playing.entrySet()) {
            update(e.getKey(), e.getValue());
        }
    }

    @Override
    public void event(Class event, Object[] args) {

        if (event == Events.FrameEnd.class) {
            updateConceptsPlaying();
        }

        /*/else if (event == Events.ConceptProcessed.class) {
            Premise f = (Premise)args[0];
            update(f.getConcept());
        }*/


    }

    @Override
    public void onConceptActive(Concept c) {
        update(c);
    }

    @Override
    public void onConceptForget(Concept c) {
        update(c);
    }
}
