package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.util.Properties;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceHandle;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceType;
import pt.unl.fct.di.tardis.babel.iot.api.replies.RegisterIoTDeviceReply;
import pt.unl.fct.di.tardis.babel.iot.api.requests.RegisterIoTDeviceRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2COutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowTextRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * The reference demo for this project, and the one the README walks through
 * end-to-end under "Anatomy of a demo". It drives a <b>Grove LCD</b> (an I²C
 * device) through the {@code I2COutputControlProtocol} (protocol id {@code 2000},
 * the control protocol that owns LCD and LED-matrix output), cycling a new line
 * of text onto the display every 1.5 seconds. Run it with:
 *
 * <pre>{@code java -jar babel-raspberry-iot-examples.jar Lcd}</pre>
 *
 * <p><b>One class, two roles.</b> As a {@link GenericProtocol} it lives on
 * Babel's single-threaded event loop, holding the device handle and reacting to
 * replies and timers; as a {@link BabelDemo} it knows how to bootstrap its own
 * runtime in {@link #execute()}.
 *
 * <p><b>The pattern every Grove demo follows</b> (see README "Pattern 1"):
 * <ol>
 *   <li><b>register</b> a reply handler, then send a {@link RegisterIoTDeviceRequest}
 *       asking the control protocol to claim the device for us;</li>
 *   <li>when the {@link RegisterIoTDeviceReply} arrives, <b>keep the
 *       {@link DeviceHandle}</b> — that opaque token is our reference to the
 *       device from then on;</li>
 *   <li><b>drive</b> the device by sending typed requests that carry the handle
 *       (here {@link ShowTextRequest}), often on a {@link DemoTimer} so it
 *       repeats.</li>
 * </ol>
 *
 * <p><b>The application never touches Pi4J, I²C, or GPIO directly.</b> It only
 * sends Babel requests; the {@code I2COutputControlProtocol} translates them into
 * the actual hardware operations. That separation is the whole point of the
 * layering and is what lets this code stay short and portable.
 *
 * <p>This demo is derived from NOVA FCT / TaRDIS work (see the README "Credits").
 */
public class BabelLcdDemo extends GenericProtocol implements BabelDemo {

	/**
	 * Our opaque reference to the registered LCD. Populated <em>asynchronously</em>
	 * when the {@link RegisterIoTDeviceReply} arrives, not in the constructor — so
	 * it is {@code null} until then. Every later request that targets the LCD
	 * carries this handle.
	 */
	private DeviceHandle lcdDevice;

	/** The lines this demo cycles through on the LCD, one per timer tick. */
	private String[] contents = {
			"Hello from TaRDIS! We hope you enjoy this demo.", "Tá turbinada", "Tá toda turbinada",
			"Tá turbinada", "E não lhe falta nada", "Não, eu nunca apoiaria a guerra", "A guerra não é vencedora",
			"Sou uma máquina, sim", "La máquina de fiesta", "Súbelo", "Tá turbinada", "Está toda turbinada",
			"Tá turbinada","E não lhe falta nada","Tá turbinada","Está toda turbinada","Tá turbinada",
			"La máquina está quitada","Súbelo","Vem com o D. Snow e a Ana Malhoa","Empezó la rumba",
			"A música do povo","A música da rua","Raaaa","Tú nunca viste",
			"Una chica como yo (La máquina)","Tão sexy, atrevida","Caliente como yo (Wow)","Um par de formes",
			"Bonitas como yo","O una muñequita","Tan bomba como yo","Que sube, sube","Todo mi calor (Súbelo)",
			"Que a ti te pone","Fuera de control","Tá turbinada","Está toda turbinada","Tá turbinada",
			"E não lhe falta nada","Tá turbinada","Está toda turbinada","Tá turbinada","La máquina está quitada XX",
			"Bring me on, bring me on, bring me on","(Una máquina increíble, Ana Malhoa)","(La máquina de fiesta)",
			"Tú nunca viste","Una chica como yo","Tão sexy, atrevida","Caliente como yo","Um par de formes",
			"Bonitas como yo","O una muñequita","Tan bomba como yo","Que sube, sube","Todo mi calor","Que a ti te pone",
			"Fuera de control","Tá turbinada","Está toda turbinada","Tá turbinada","E não lhe falta nada",
			"Tá turbinada","Está toda turbinada","Tá turbinada","La máquina está quitada","Huh, D. Snow","She's so hot, she turns me on",
			"When I take off what she's got on","The days are short and nights are long",
			"When I'm right here she always want","I never seen a girl like this","You move away, so whine and twist",
			"What we got is endless","Please do anything about you sexy...","Tá turbinada","Está toda turbinada",
			"Tá turbinada","E não lhe falta nada","Tá turbinada","Está toda turbinada","Tá turbinada","La máquina está kitada",
			"Tá turbinada","Está toda turbinada","Tá turbinada","E não lhe falta nada","Tá turbinada",
			"Está toda turbinada","Tá turbinada","La máquina está quitada (Fiesta)","Tamo' junto nessa", "Vamo' que vamo'"
	};
	
	/** Cursor into {@link #contents}; advanced by {@link #getNextSentence()} and wraps around. */
	private int nextSentece;

	/**
	 * Sets the demo's Babel identity (shared {@link BabelDemo#PROTO_NAME} /
	 * {@link BabelDemo#PROTO_ID}). Note that no device work happens here — the
	 * device is requested later in {@link #init(Properties)}, once Babel is wired
	 * up and able to deliver the reply.
	 */
	public BabelLcdDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		// Start the cursor at the end so the first getNextSentence() wraps to 0.
		this.nextSentece = contents.length;
	}

	/**
	 * Babel calls {@code init} once this protocol is registered. This is where the
	 * register-then-drive pattern starts.
	 *
	 * <p>Order matters: we wire up our handlers <em>before</em> sending any
	 * request, so a reply can never arrive before the handler that consumes it is
	 * in place.
	 */
	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		// Register the periodic-tick handler so DemoTimer firings reach handleDemoTimer.
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		// Register the reply handler BEFORE sending the request below, so the
		// RegisterIoTDeviceReply cannot land before its handler is wired.
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
				this::handleRegisterIoTDeviceReply);
		// Ask the I²C output control protocol (id 2000) to claim a Grove LCD for
		// us under the alias "LCD". For an I²C device no GPIO line is needed; the
		// alias is just a label we use to sanity-check the reply. The control
		// protocol will probe the bus and answer with a RegisterIoTDeviceReply.
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD,
				"LCD"), I2COutputControlProtocol.PROTOCOL_ID);
	}

	/** Advances the rotating cursor over {@link #contents}, wrapping at the end, and returns it. */
	private int getNextSentence() {
		this.nextSentece++;
		if(this.nextSentece >= contents.length)
			this.nextSentece = 0;
		return this.nextSentece;
	}


	/**
	 * Periodic-timer handler: invoked by Babel every time the {@link DemoTimer}
	 * fires. Each tick drives the LCD by sending a {@link ShowTextRequest} (carrying
	 * the {@link #lcdDevice} handle and the next line of text) to the control
	 * protocol — the recurring "act repeatedly on a device" half of the pattern.
	 */
	public void handleDemoTimer(DemoTimer t, long time) {
		// Send the next line to the LCD via the control protocol; we never poke the
		// I²C bus ourselves — the request carries the handle and the protocol does it.
		sendRequest(new ShowTextRequest(lcdDevice, contents[this.getNextSentence()]), I2COutputControlProtocol.PROTOCOL_ID);
	}

	/**
	 * Reply handler for {@link RegisterIoTDeviceReply}: Babel delivers this once the
	 * control protocol has tried to register our LCD. This is where we capture the
	 * {@link DeviceHandle} and only then begin driving the device.
	 */
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply. Success: " + rep.isSuccessful());
		// Always check success first: if the hardware was missing the bus probe
		// failed and there is no handle to use.
		if(rep.isSuccessful()) {
			// Keep the opaque handle — every later request targeting the LCD uses it.
			this.lcdDevice = rep.getDeviceHandle();
			//Safety verification
			// Confirm the control protocol handed back the device we asked for
			// (the alias we registered under). A mismatch means a wiring bug.
			if(!this.lcdDevice.getDeviceAlias().equals("LCD")) {
				System.err.println("Incorrect answer received, expected alias 'LCD' received '" + this.lcdDevice.getDeviceAlias() + "'");
				System.exit(1);
			}

			// Draw one line immediately so the screen isn't blank until the first tick.
			sendRequest(new ShowTextRequest(lcdDevice, contents[this.getNextSentence()]), I2COutputControlProtocol.PROTOCOL_ID);

			// Now that we hold a valid handle, start the recurring driver: a
			// periodic timer that fires after 10 s and then every 1.5 s, each
			// firing handled by handleDemoTimer above.
			setupPeriodicTimer(new DemoTimer(), 10000, 1500); //1,5 second timer

		} else {
			// Registration failed (e.g. no LCD on the bus): report and stop.
			System.err.println("Failed to register LCD Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}

	/**
	 * The bootstrap, invoked once by {@code Main}. It builds the Babel runtime this
	 * demo needs and starts the event loop.
	 *
	 * <p>The recipe is the same in every demo: get the {@code Babel} singleton, load
	 * config, instantiate <em>only</em> the control protocol(s) this demo uses (here
	 * just the I²C output protocol), register both that protocol and the demo,
	 * {@code init} each in dependency order (control protocol first so its request
	 * handlers exist before the demo sends to it), then {@code start()}.
	 */
	@Override
	public void execute() throws Exception {
		// The single Babel runtime for this JVM (Babel is a process-wide singleton).
		Babel b = Babel.getInstance();

		// Load properties from paradigmshift.config (device lines, addresses, etc.).
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

		BabelLcdDemo lcd = this;

		// The control protocol that actually talks to the I²C LCD; the demo only
		// ever sends it requests. Its no-arg constructor fetches the one shared
		// Pi4J Context internally (SharedPi4J) — we must not build our own.
		I2COutputControlProtocol i2cout = new I2COutputControlProtocol();

		// Register both protocols with Babel so they can exchange events.
		b.registerProtocol(i2cout);
		b.registerProtocol(lcd);

		// Init in dependency order: the control protocol first (so its request
		// handlers are ready), then the demo (whose init sends the first request).
		i2cout.init(props);
		lcd.init(props);

		System.out.println("Setup is complete.");

		// Hand control to Babel's event loop; from here everything is event-driven.
		b.start();

		System.out.println("System is running.");
	}

}
