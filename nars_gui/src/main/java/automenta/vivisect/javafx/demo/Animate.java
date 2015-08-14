package automenta.vivisect.javafx.demo;

import javafx.animation.AnimationTimer;

import java.util.function.Consumer;

/**
 * Created by me on 8/13/15.
 */
public class Animate extends AnimationTimer {

    private final Consumer<Animate> run;
    private long periodMS;
    private long last;

    public Animate(long periodMS, Consumer<Animate> r) {
        super();
        this.periodMS = periodMS;
        this.run = r;
    }

    @Override
    public synchronized void handle(final long now) {
        if (now - last > periodMS) {
            run.accept(this);
            last = now;
        }
    }
}
