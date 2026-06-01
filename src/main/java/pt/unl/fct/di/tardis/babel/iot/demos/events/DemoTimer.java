package pt.unl.fct.di.tardis.babel.iot.demos.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

/**
 * The periodic "tick" timer that the Grove device demos use to do something
 * over and over — e.g. {@code BabelLcdDemo} sets one up to scroll a new line of
 * text onto the LCD every 1.5 s.
 *
 * <p>In Babel a timer is just an event you schedule on your own protocol with
 * {@code setupTimer} / {@code setupPeriodicTimer}; when it fires, Babel delivers
 * it back to the handler you registered for {@link #TIMER_ID} via
 * {@code registerTimerHandler}. This class carries no behaviour — it is only a
 * typed marker the event loop hands back to that handler. The optional
 * {@code timestamp} lets a handler tell instances apart if it cares (the demos
 * mostly don't).
 */
public class DemoTimer extends ProtoTimer {

	/** Babel timer id for this demo timer (matches {@code DemoTimer.TIMER_ID} in the handler registration). */
	public final static short TIMER_ID = 666;

	private final long timestamp;

	/** Creates a tick stamped with the current wall-clock time. */
	public DemoTimer() {
		super(TIMER_ID);
		this.timestamp = System.currentTimeMillis();
	}

	/** Creates a tick carrying a caller-supplied timestamp. */
	public DemoTimer(long ts) {
		super(TIMER_ID);
		this.timestamp = ts;
	}

	/**
	 * Babel clones a timer each time a periodic timer re-fires. Because this
	 * timer is immutable (stateless apart from a read-only timestamp), there is
	 * nothing to copy: returning {@code this} is the Babel convention for a
	 * stateless {@link ProtoTimer} and avoids a needless allocation per tick.
	 */
	@Override
	public ProtoTimer clone() {
		return this;
	}

	/** @return the timestamp this tick was created with. */
	public long getTimestamp() {
		return this.timestamp;
	}
}