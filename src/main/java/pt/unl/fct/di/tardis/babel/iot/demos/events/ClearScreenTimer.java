package pt.unl.fct.di.tardis.babel.iot.demos.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class ClearScreenTimer extends ProtoTimer {

	public final static short TIMER_ID = 667;
	
	private final long timestamp;
	
	public ClearScreenTimer() {
		super(TIMER_ID);
		this.timestamp = System.currentTimeMillis();
	}
	
	public ClearScreenTimer(long ts) {
		super(TIMER_ID);
		this.timestamp = ts;
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}
	
	public long getTimestamp() {
		return this.timestamp;
	}
}