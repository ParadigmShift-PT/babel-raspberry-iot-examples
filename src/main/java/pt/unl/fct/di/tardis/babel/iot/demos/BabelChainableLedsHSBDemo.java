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
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetMultipleChainableLEDColorHSBRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Demo: a strip of Grove <em>chainable</em> RGB LEDs animated as a travelling
 * colour wave, expressed in the <strong>HSB</strong> (hue/saturation/brightness)
 * colour model. On every tick a fresh colour is computed for the head of the
 * strip (LED 0) and the previous colours shift one position down the chain, so
 * the colour appears to flow along the strip.
 *
 * <p>This is the HSB twin of {@link BabelChainableLedsRGBDemo}: same hardware,
 * same animation shape, but colours are described as three floats (hue,
 * saturation, brightness) instead of red/green/blue bytes. The control protocol
 * converts HSB to the wire format. Comparing the two demos shows that the colour
 * <em>model</em> is just a choice of request type — the rest of the flow is
 * identical.
 *
 * <p><strong>Devices &amp; control protocols used.</strong> One Grove chainable
 * RGB LED strip ({@link DeviceType#GROVE_CHAINABLE_RGB}), driven through the
 * {@link DigitalOutputControlProtocol} (protocol id 2300).
 *
 * <p><strong>The teaching point.</strong> This application protocol never
 * touches Pi4J, GPIO or the LED wire format directly. It only sends Babel
 * requests ({@link RegisterIoTDeviceRequest},
 * {@link SetMultipleChainableLEDColorHSBRequest}) to the control protocol, which
 * performs the actual GPIO work.
 *
 * <p><strong>To run:</strong> {@code java -jar <jar> ledsHSB} (see
 * {@code Main.java}).
 *
 * <p><strong>Configuration.</strong> The strip length is read from the
 * {@code rgb.led.count} property (via
 * {@link DigitalOutputControlProtocol#RGB_LED_COUNT}, default 1) and the GPIO
 * data line from {@code led.line} (default {@code 24}) — both in
 * {@code paradigmshift.config}. See the project README for why line 26 must be
 * avoided alongside a LoRa HAT.
 *
 * <p>Based on IoT-control demos originally developed at NOVA FCT for the TaRDIS
 * project; provided and evolved independently by ParadigmShift.
 */
public class BabelChainableLedsHSBDemo extends GenericProtocol implements BabelDemo {

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
	/** Per-LED colour state: {@code [ledIndex][0..2]} = hue/saturation/brightness floats. */
	private float[][] ledColors;

	private final int maxSteps;
	private int step;

	/**
	 * Sets the protocol identity shared by all demos ({@link BabelDemo#PROTO_NAME}
	 * / {@link BabelDemo#PROTO_ID}) and seeds the colour RNG. Babel handlers are
	 * wired later in {@link #init(Properties)}.
	 */
	public BabelChainableLedsHSBDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		this.r = new SecureRandom((System.currentTimeMillis() + "").getBytes());
		this.maxSteps = 100;
	}

	/**
	 * Wires this protocol's event handlers and starts device registration.
	 *
	 * <p>Reply and timer handlers are registered <em>before</em> the request is
	 * sent, so the {@link RegisterIoTDeviceReply} cannot beat its handler into
	 * place. We then ask the {@link DigitalOutputControlProtocol} to register a
	 * chainable RGB strip on {@code deviceLine}; the handle arrives asynchronously
	 * in {@link #handleRegisterIoTDeviceReply}. Finally the colour buffer is
	 * zeroed (all LEDs off).
	 */
	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		// Register handlers BEFORE issuing the request so the reply can't race us.
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
				this::handleRegisterIoTDeviceReply);

		if(props.containsKey(DigitalOutputControlProtocol.RGB_LED_COUNT))
			this.numberOfLeds = Integer.parseInt(props.getProperty(DigitalOutputControlProtocol.RGB_LED_COUNT));
		else
			this.numberOfLeds = 1;
		
		System.err.println("Number of leds in chain: " + this.numberOfLeds);
		
		this.deviceLine = Integer.parseInt(props.getProperty(LED_PORT, LED_PORT_DEFAULT));

		// Digital devices need the GPIO line; the control protocol owns the wiring.
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_CHAINABLE_RGB,
				ledAlias, this.deviceLine), DigitalOutputControlProtocol.PROTOCOL_ID);

		this.ledColors = new float[numberOfLeds][3];
		for(int i = 0; i < numberOfLeds; i++)
			for(int j = 0; j < 3; j++)
				this.ledColors[i][j] = 0;
	}


	/**
	 * Periodic-timer handler: advances the animation one step. Shifts every LED's
	 * colour one slot down the chain, then computes a new HSB colour for the head
	 * (LED 0) from a random step value, and pushes the whole strip out via
	 * {@link #updateLedsColors()}. Babel invokes this on its event loop because the
	 * timer was armed with {@code setupPeriodicTimer} once the device was ready.
	 */
	public void handleDemoTimer(DemoTimer t, long time) {
		for(int i = (numberOfLeds - 1); i > 0; i--) {
			this.ledColors[i][0] = this.ledColors[i-1][0];
			this.ledColors[i][1] = this.ledColors[i-1][1];
			this.ledColors[i][2] = this.ledColors[i-1][2];
		}
	
		this.step = r.nextInt(100);
		float ratio = (float)(step % maxSteps) / maxSteps;
		
		this.ledColors[0][0] = ratio;
		this.ledColors[0][1] = 0.7f + 0.3f * (float)Math.sin(2 * Math.PI * ratio);
		this.ledColors[0][2] = 0.7f + 0.3f * (float)Math.sin(2 * Math.PI * ratio * 2);
		System.err.println("Generated a total of " + this.ledColors[0].length + " bytes: " + 
				this.ledColors[0][0] + " : " + this.ledColors[0][1] + " : " + this.ledColors[0][2]);
		
		updateLedsColors();
		
	}
	
	/**
	 * Reply handler for the device registration. Babel routes the
	 * {@link RegisterIoTDeviceReply} here once the control protocol has claimed the
	 * hardware.
	 *
	 * <p>The pattern: check {@link RegisterIoTDeviceReply#isSuccessful()}; on
	 * failure, bail out. On success, keep the {@link DeviceHandle} (our only
	 * reference to the strip), paint an initial frame, and arm the periodic timer
	 * that drives the animation. The alias check guards against a mismatched reply.
	 */
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply. Success: " + rep.isSuccessful());
		if(rep.isSuccessful()) {
			this.chainableLeds = rep.getDeviceHandle();
			//Safety verification
			if(!this.chainableLeds.getDeviceAlias().equals(ledAlias)) {
				System.err.println("Incorrect answer received, expected alias '" + ledAlias + "' received '" + this.chainableLeds.getDeviceAlias() + "'");
				System.exit(1);
			}

			updateLedsColors();

			// Drive repeated colour changes off a periodic Babel timer.
			setupPeriodicTimer(new DemoTimer(), 700, 700); //700 Milliseconds wait

		} else {
			System.err.println("Failed to register ChainableLed Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}

	/**
	 * Pushes the current {@link #ledColors} buffer to the strip as a single
	 * {@link SetMultipleChainableLEDColorHSBRequest} carrying the device handle and
	 * one HSB colour per position, sent to the {@link DigitalOutputControlProtocol}.
	 */
	private void updateLedsColors() {
		SetMultipleChainableLEDColorHSBRequest req = new SetMultipleChainableLEDColorHSBRequest(chainableLeds);
		for (byte i = (byte) (numberOfLeds - 1); i >= 0; i--)
			req.addValuesForPosition(i,this.ledColors[i]);

		sendRequest(req, DigitalOutputControlProtocol.PROTOCOL_ID);
	}

	/**
	 * Entry point for this demo (called from {@code Main}). Bootstraps Babel: grab
	 * the {@link Babel} singleton, load {@code paradigmshift.config}, instantiate
	 * the one control protocol this demo needs ({@link DigitalOutputControlProtocol}),
	 * register it plus this demo, {@code init} them in dependency order (control
	 * protocol first so its handlers exist before we send to it), then start the
	 * event loop.
	 */
	@Override
	public void execute() throws Exception {
		Babel b = Babel.getInstance();

		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");
		
		BabelChainableLedsHSBDemo demo = this;

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
