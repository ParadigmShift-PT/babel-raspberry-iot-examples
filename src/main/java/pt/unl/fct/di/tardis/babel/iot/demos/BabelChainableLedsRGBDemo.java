package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Random;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceHandle;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceType;
import pt.unl.fct.di.tardis.babel.iot.api.replies.RegisterIoTDeviceReply;
import pt.unl.fct.di.tardis.babel.iot.api.requests.RegisterIoTDeviceRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.DigitalOutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetMultipleChainableLEDColorRGBRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Demo: a strip of Grove <em>chainable</em> RGB LEDs whose colours animate as a
 * travelling rainbow. On every tick a fresh colour is computed for the head of
 * the strip (LED 0) and the previous colours are shifted one position down the
 * chain, so the colour appears to flow along the strip.
 *
 * <p>Colours are expressed in the <strong>RGB</strong> model here: three bytes
 * (red, green, blue) per LED. The HSB sibling of this demo
 * ({@link BabelChainableLedsHSBDemo}) animates the identical strip using the
 * hue/saturation/brightness model instead — comparing the two side by side is
 * the point of having both.
 *
 * <p><strong>Devices &amp; control protocols used.</strong> One Grove chainable
 * RGB LED strip ({@link DeviceType#GROVE_CHAINABLE_RGB}), driven through the
 * {@link DigitalOutputControlProtocol} (protocol id 2300). The strip lives on a
 * single GPIO data line; the control protocol does all the bit-banging.
 *
 * <p><strong>The teaching point.</strong> This application protocol never
 * touches Pi4J, GPIO or the LED wire format directly. It only sends Babel
 * requests ({@link RegisterIoTDeviceRequest},
 * {@link SetMultipleChainableLEDColorRGBRequest}) to the control protocol, which
 * performs the actual GPIO work. The app reasons purely in terms of "device
 * handles" and "set these colours"; the hardware lives behind that boundary.
 *
 * <p><strong>To run:</strong> {@code java -jar <jar> ledsRGB} (see
 * {@code Main.java}).
 *
 * <p><strong>Configuration.</strong> The strip length is read from the
 * {@code rgb.led.count} property (via
 * {@link DigitalOutputControlProtocol#RGB_LED_COUNT}, default 1) and the GPIO
 * data line from {@code led.line} (default {@code 24}) — both in
 * {@code paradigmshift.config}. Line 24 is chosen so the strip coexists with a
 * seated LoRa HAT; see the project README for why line 26 must be avoided.
 *
 * <p>Based on IoT-control demos originally developed at NOVA FCT for the TaRDIS
 * project; provided and evolved independently by ParadigmShift.
 */
public class BabelChainableLedsRGBDemo extends GenericProtocol implements BabelDemo {

	/** Config key naming the GPIO data line the LED strip is wired to. */
	public static final String LED_PORT = "led.line";
	/** Default GPIO line (BCM 24) — coexists with a seated LoRa HAT. */
	public static final String LED_PORT_DEFAULT = "24";

	/** Opaque reference to the registered strip; populated asynchronously by the reply handler. */
	private DeviceHandle chainableLeds;

	/** Human-readable name we register the strip under, then verify in the reply. */
	public final static String ledAlias = "leds";
	private int deviceLine;
	private final Random r;

	/** Number of LEDs in the chain; read from {@code rgb.led.count}. */
	private int numberOfLeds;
	/** Per-LED colour state: {@code [ledIndex][0..2]} = red/green/blue bytes. */
	private byte[][] ledColors;

	private final int maxSteps = 100;
	private int step;

	/**
	 * Sets the protocol identity shared by all demos ({@link BabelDemo#PROTO_NAME}
	 * / {@link BabelDemo#PROTO_ID}) and seeds the colour RNG. No Babel handlers are
	 * wired here — that happens in {@link #init(Properties)}, which Babel calls
	 * once the protocol is registered.
	 */
	public BabelChainableLedsRGBDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		this.r = new SecureRandom((System.currentTimeMillis() + "").getBytes());
	}

	/**
	 * Wires this protocol's event handlers and kicks off device registration.
	 *
	 * <p>The order matters: we register the reply (and timer) handlers
	 * <em>before</em> sending the request, so the {@link RegisterIoTDeviceReply}
	 * cannot arrive before its handler is in place. We then ask the
	 * {@link DigitalOutputControlProtocol} to register a chainable RGB strip on
	 * {@code deviceLine}; the actual handle comes back asynchronously in
	 * {@link #handleRegisterIoTDeviceReply}. Finally we allocate the all-black
	 * colour buffer.
	 */
	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		// Register handlers BEFORE issuing the request so the reply can't race us.
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, this::handleRegisterIoTDeviceReply);

		if (props.containsKey(DigitalOutputControlProtocol.RGB_LED_COUNT))
			this.numberOfLeds = Integer.parseInt(props.getProperty(DigitalOutputControlProtocol.RGB_LED_COUNT));
		else
			this.numberOfLeds = 1;

		System.err.println("Number of leds in chain: " + this.numberOfLeds);

		this.deviceLine = Integer.parseInt(props.getProperty(LED_PORT, LED_PORT_DEFAULT));

		// Digital devices need the GPIO line; the control protocol owns the wiring.
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_CHAINABLE_RGB, ledAlias, this.deviceLine),
				DigitalOutputControlProtocol.PROTOCOL_ID);

		this.ledColors = new byte[numberOfLeds][3];
		for (int i = 0; i < numberOfLeds; i++)
			for (int j = 0; j < 3; j++)
				this.ledColors[i][j] = (byte) 0b00000000;
	}

	/**
	 * Periodic-timer handler: advances the animation one step. Each tick shifts
	 * every LED's colour one slot toward the tail of the strip, then computes a
	 * new RGB colour for the head (LED 0) by sampling a sine wave at three 120°
	 * phase offsets (the classic rainbow trick). It then pushes the whole strip
	 * out via {@link #updateLedsColors()}.
	 *
	 * <p>Babel calls this on its event loop because the timer was armed with
	 * {@code setupPeriodicTimer} once the device was ready.
	 */
	public void handleDemoTimer(DemoTimer t, long time) {
		for (int i = (numberOfLeds - 1); i > 0; i--) {
			this.ledColors[i][0] = this.ledColors[i - 1][0];
			this.ledColors[i][1] = this.ledColors[i - 1][1];
			this.ledColors[i][2] = this.ledColors[i - 1][2];
		}

		step = r.nextInt(100);

		float ratio = (float) (step % maxSteps) / maxSteps;
		double angle = 2 * Math.PI * ratio;

		this.ledColors[0][0] = (byte) (Math.sin(angle) * 127 + 128); // stays between 0 and 255
		this.ledColors[0][1] = (byte) (Math.sin(angle + (2.0 / 3.0 * Math.PI)) * 127 + 128); // 120° phase shift
		this.ledColors[0][2] = (byte) (Math.sin(angle + (4.0 / 3.0 * Math.PI)) * 127 + 128); // 240° phase shift

		System.err.println("Generated a total of " + this.ledColors[0].length + " bytes: " + this.ledColors[0][0]
				+ " : " + this.ledColors[0][1] + " : " + this.ledColors[0][2]);

		updateLedsColors();

	}

	/**
	 * Reply handler for the device registration. Babel routes the
	 * {@link RegisterIoTDeviceReply} here once the control protocol has probed and
	 * claimed the hardware.
	 *
	 * <p>The pattern: check {@link RegisterIoTDeviceReply#isSuccessful()}; on
	 * failure, bail out (the hardware is missing or busy). On success, keep the
	 * {@link DeviceHandle} — it is our only reference to the strip from now on —
	 * paint an initial frame, and arm the periodic timer that drives the
	 * animation. The alias check is a defensive sanity check that the reply we got
	 * is for the device we asked for.
	 */
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply. Success: " + rep.isSuccessful());
		if (rep.isSuccessful()) {
			this.chainableLeds = rep.getDeviceHandle();
			// Safety verification
			if (!this.chainableLeds.getDeviceAlias().equals(ledAlias)) {
				System.err.println("Incorrect answer received, expected alias '" + ledAlias + "' received '"
						+ this.chainableLeds.getDeviceAlias() + "'");
				System.exit(1);
			}

			updateLedsColors();

			// Drive repeated colour changes off a periodic Babel timer.
			setupPeriodicTimer(new DemoTimer(), 300, 300); // 300 Milliseconds wait

		} else {
			System.err.println("Failed to register ChainableLed Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}

	/**
	 * Pushes the current {@link #ledColors} buffer to the strip. Builds one
	 * {@link SetMultipleChainableLEDColorRGBRequest} carrying the device handle
	 * plus a colour for every position, then hands it to the
	 * {@link DigitalOutputControlProtocol} — a single request updates the whole
	 * chain rather than one LED at a time.
	 */
	private void updateLedsColors() {
		SetMultipleChainableLEDColorRGBRequest req = new SetMultipleChainableLEDColorRGBRequest(chainableLeds);
		for (byte i = (byte) (numberOfLeds - 1); i >= 0; i--)
			req.addValuesForPosition(i,this.ledColors[i]);

		sendRequest(req, DigitalOutputControlProtocol.PROTOCOL_ID);
	}

	/**
	 * Entry point for this demo (called from {@code Main}). Bootstraps the Babel
	 * runtime: grab the {@link Babel} singleton, load {@code paradigmshift.config},
	 * instantiate the one control protocol this demo needs
	 * ({@link DigitalOutputControlProtocol}), register both it and this demo, then
	 * {@code init} them in dependency order (control protocol first so its request
	 * handlers exist before we send to it) and start the event loop.
	 */
	@Override
	public void execute() throws Exception {
		Babel b = Babel.getInstance();

		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

		BabelChainableLedsRGBDemo demo = this;

		DigitalOutputControlProtocol dout = new DigitalOutputControlProtocol();

		b.registerProtocol(dout);
		b.registerProtocol(demo);

		dout.init(props);
		demo.init(props);

		System.out.println("Setup is complete.");

		b.start();

		System.out.println("System is running.");
	}

}
