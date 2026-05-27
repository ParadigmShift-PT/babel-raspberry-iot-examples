package pt.paradigmshift.iot.demos.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

/**
 * Periodic timer that drives the "send" side of the LoRa and ZigBee radio
 * demos: on every tick the demo broadcasts one small packet.
 *
 * <p>Developed by ParadigmShift, Lda.
 *
 * @author ParadigmShift, Lda (info@paradigmshift.pt)
 */
public class RadioSendTimer extends ProtoTimer {

    /** Babel timer id used inside the demo protocol. <b>ID:</b> {@value}. */
    public static final short TIMER_ID = 667;

    public RadioSendTimer() {
        super(TIMER_ID);
    }

    // Stateless timer: cloning can safely alias the same instance.
    @Override
    public ProtoTimer clone() {
        return this;
    }
}
