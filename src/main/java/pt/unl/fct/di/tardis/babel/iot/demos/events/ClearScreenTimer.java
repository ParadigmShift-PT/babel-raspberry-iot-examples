package pt.unl.fct.di.tardis.babel.iot.demos.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

/**
 * A one-shot "wipe the display" timer used by demos that want to clear an output
 * device a little while after they last drew to it (for example, clearing the
 * LED matrix once a reactive gesture has been shown for long enough).
 *
 * <p>Like {@code DemoTimer}, this is purely a typed Babel event: you schedule it
 * with {@code setupTimer} and Babel delivers it back to the handler registered
 * for {@link #TIMER_ID}. Distinct from {@code DemoTimer} only by its id, so a
 * single demo can run both a periodic "tick" timer and a separate "clear"
 * timer without their handlers colliding.
 */
public class ClearScreenTimer extends ProtoTimer {

	/** Babel timer id for the clear-screen timer (kept distinct from {@code DemoTimer.TIMER_ID}). */
	public final static short TIMER_ID = 667;

	private final long timestamp;

	/** Creates a clear-screen timer stamped with the current wall-clock time. */
	public ClearScreenTimer() {
		super(TIMER_ID);
		this.timestamp = System.currentTimeMillis();
	}

	/** Creates a clear-screen timer carrying a caller-supplied timestamp. */
	public ClearScreenTimer(long ts) {
		super(TIMER_ID);
		this.timestamp = ts;
	}

	/**
	 * Stateless timer, so cloning has nothing to copy: returning {@code this} is
	 * the Babel convention for an immutable {@link ProtoTimer} and avoids an
	 * allocation each time the timer re-fires.
	 */
	@Override
	public ProtoTimer clone() {
		return this;
	}

	/** @return the timestamp this timer was created with. */
	public long getTimestamp() {
		return this.timestamp;
	}
}