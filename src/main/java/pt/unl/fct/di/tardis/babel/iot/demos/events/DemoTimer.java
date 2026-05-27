package pt.unl.fct.di.tardis.babel.iot.demos.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class DemoTimer extends ProtoTimer {

	public final static short TIMER_ID = 666;
	
	private final long timestamp;
	
	public DemoTimer() {
		super(TIMER_ID);
		this.timestamp = System.currentTimeMillis();
	}
	
	public DemoTimer(long ts) {
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