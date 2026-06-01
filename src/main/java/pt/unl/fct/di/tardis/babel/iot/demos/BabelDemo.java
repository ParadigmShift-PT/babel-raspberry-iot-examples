package pt.unl.fct.di.tardis.babel.iot.demos;

/**
 * The common contract every demo in this project implements.
 *
 * <p>A demo is both a Babel {@code GenericProtocol} (so it can register handlers,
 * send requests, and react to replies/timers/notifications) <em>and</em> a
 * {@code BabelDemo} (so {@code Main} can construct it and kick it off uniformly).
 * {@code Main} selects exactly one demo by command-line name and calls
 * {@link #execute()} on it; from there the demo bootstraps its own Babel runtime.
 *
 * <p>Every concrete demo reuses the same protocol identity below. That is safe
 * here because only one demo ever runs per JVM (see {@code Main}), so there is no
 * risk of two protocols claiming id {@link #PROTO_ID} at once. The same id also
 * doubles as the {@code sourceProto} tag the radio demos stamp onto outgoing
 * packets so a receiver can recognise its own traffic.
 *
 * <p>Demos in package {@code pt.unl.fct.di.tardis.babel.iot.demos} are derived
 * from work originally developed at NOVA FCT for the TaRDIS project; the LoRa /
 * ZigBee demos under {@code pt.paradigmshift.iot.demos} were authored by
 * ParadigmShift, Lda. (see this repository's README "Credits" section).
 */
public interface BabelDemo {

	/** Human-readable protocol name passed to {@code GenericProtocol}'s constructor by every demo. */
	public final static String PROTO_NAME = "BabelDemo";
	/** Shared Babel protocol id used by every demo (one demo runs per process, so reuse is safe). */
	public final static short PROTO_ID = 666;

	/**
	 * Builds and starts this demo's Babel runtime: obtain {@code Babel.getInstance()},
	 * load config, instantiate and register the control/radio protocols the demo
	 * needs plus the demo itself, {@code init(...)} each in dependency order, and
	 * finally {@code Babel.start()} the event loop. {@code Main} calls this once.
	 */
	public void execute() throws Exception;

}
