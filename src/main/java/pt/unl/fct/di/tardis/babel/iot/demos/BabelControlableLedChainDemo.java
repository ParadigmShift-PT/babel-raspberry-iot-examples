package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.iot.device.i2c.GroveGestureDetector.PAJ7620GestureType;
import pt.unl.fct.di.novasys.iot.device.i2c.utils.LedMatrixUtils;
import pt.unl.fct.di.novasys.iot.device.i2c.utils.LedMatrixUtils.Arrow;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceHandle;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceType;
import pt.unl.fct.di.tardis.babel.iot.api.Threshold;
import pt.unl.fct.di.tardis.babel.iot.api.replies.RegisterIoTDeviceReply;
import pt.unl.fct.di.tardis.babel.iot.api.requests.RegisterIoTDeviceRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.DigitalOutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2CInputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2COutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.notifications.GestureNotification;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.replies.GestureInputReply;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.input.GetReactiveGestureRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ClearDisplayRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetDisplayColorRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetMultipleChainableLEDColorRGBRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowDisplayRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowTextRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.ClearScreenTimer;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Flagship multi-device demo — command-line name {@code lightControl}.
 *
 * <p>This is the in-repo proof that <b>one</b> Babel application protocol can
 * drive several Grove devices <em>and</em> react to live sensor input at the
 * same time — exactly the shape of the StoneFlux edge gateway. A single
 * {@code GenericProtocol} (this class) wires four devices together so the user
 * controls an RGB light strip with hand gestures and sees feedback on a matrix
 * and an LCD:
 *
 * <ul>
 *   <li><b>chainable RGB LED strip</b> — output, via
 *       {@link DigitalOutputControlProtocol} (id 2300). The lights to control.</li>
 *   <li><b>gesture detector</b> — input, via {@link I2CInputControlProtocol}
 *       (id 2100). Emits a {@link GestureNotification} for each hand swipe; this
 *       is the reactive control surface.</li>
 *   <li><b>LED matrix</b> — output, via {@link I2COutputControlProtocol}
 *       (id 2000). Shows an arrow/icon acknowledging each gesture.</li>
 *   <li><b>16x2 LCD</b> — output, also via {@link I2COutputControlProtocol}
 *       (id 2000). Prints a human-readable status line.</li>
 * </ul>
 *
 * <p><b>The interaction:</b> swipe <b>UP</b> to turn the lights on, <b>DOWN</b>
 * to turn them off, <b>RIGHT</b> to speed up the colour animation, and
 * <b>LEFT</b> to slow it down. While the lights are on, a periodic timer
 * ({@code DemoTimer}) shifts a rolling rainbow down the strip; a separate
 * one-shot timer ({@code ClearScreenTimer}) wipes the matrix shortly after each
 * gesture so the icon does not linger.
 *
 * <p><b>The Babel patterns this demonstrates</b> (read the README sections
 * "Pattern 1: driving a Grove device" and "Capabilities and limits" alongside
 * this file):
 * <ul>
 *   <li><b>Registering multiple devices</b> — one
 *       {@link RegisterIoTDeviceRequest} per device, each carrying a distinct
 *       <em>alias</em>, sent to the control protocol that owns that device type.
 *       The replies arrive asynchronously and out of order, so the reply handler
 *       matches each one back to the right field by its alias.</li>
 *   <li><b>Holding several {@link DeviceHandle}s</b> — one opaque handle per
 *       device; every later action just references the handle.</li>
 *   <li><b>Reactive input</b> — subscribe to {@link GestureNotification} and
 *       arm it with a {@link GetReactiveGestureRequest} so the gesture protocol
 *       pushes events to us instead of us polling.</li>
 *   <li><b>Timers</b> — a periodic animation tick and a one-shot screen-clear.</li>
 *   <li><b>The {@link #execute()} bootstrap</b> — instantiate and register every
 *       control protocol this demo needs (output digital, output I²C, input I²C)
 *       plus the demo itself, then start Babel.</li>
 * </ul>
 *
 * <p><b>The app never touches Pi4J or the GPIO/I²C buses directly.</b> It only
 * builds Babel requests and lets the control protocols do the hardware work.
 * That indirection is the whole point: the same application code is hardware-,
 * bus-, and Pi4J-context-agnostic.
 *
 * <p>Runs only on a Raspberry Pi with all four Grove devices wired (see the
 * README "Raspberry Pi OS setup" and the {@code led.line} = 24 wiring note).
 *
 * <p>Derived from work originally developed at NOVA FCT for the TaRDIS project;
 * see this repository's README "Credits" section.
 */
public class BabelControlableLedChainDemo extends GenericProtocol implements BabelDemo {

	/** Config key for the GPIO line the chainable RGB strip's clock pin sits on. */
	public static final String LED_PORT = "led.line";
	/**
	 * Default GPIO line for the strip (BCM 24 clock + BCM 25 data). Deliberately
	 * <b>not</b> 26: line+1 = BCM 27 would clash with a seated LoRa HAT's M1 pin
	 * and fail with {@code Device or resource busy} (see README wiring note).
	 */
	public static final String LED_PORT_DEFAULT = "24";

	// One opaque DeviceHandle per registered device. All four are populated
	// asynchronously as the RegisterIoTDeviceReply for each one arrives; until
	// then they are null. The reply handler tells the replies apart by alias.
	private DeviceHandle chainableLeds;
	private DeviceHandle ledMatrix;
	private DeviceHandle lcd;
	private DeviceHandle gesture;

	// A distinct alias per device. Because all four registration requests are
	// in flight at once and their replies can come back in any order, the alias
	// is how the reply handler matches a reply to the correct handle field.
	public final static String ledAlias = "leds";
	public final static String matrixAlias = "matrix";
	public final static String lcdAlias = "text";
	public final static String gestureAlias = "jedi";

	private int deviceLine;             // GPIO line the RGB strip is wired to
	private final Random r;             // source of the rolling-rainbow colours

	private int numberOfLeds;           // strip length, read from config
	private byte[][] ledColors;         // current [led][r,g,b] state of the strip

	private boolean ready;              // all four devices registered yet?
	private boolean active;             // are the lights currently animating?

	private long lastScreenUpdate;      // timestamp of the most recent matrix draw
	private final long minimumUpdateRate = 100;     // fastest allowed animation tick (ms)
	private final long maxUpdateRate = 2000;        // slowest allowed animation tick (ms)

	private final long refreshRateStep = 100;       // how much RIGHT/LEFT changes the rate

	private long refreshRate;           // current animation tick period (ms)

	private final int maxSteps = 100;   // colour-wheel resolution for the rainbow

	/**
	 * Constructs the demo as a Babel {@code GenericProtocol} under the shared
	 * demo identity ({@link BabelDemo#PROTO_NAME} / {@link BabelDemo#PROTO_ID}).
	 * No handlers or hardware here — that all happens in {@link #init(Properties)},
	 * which Babel calls once the protocol is registered. We only seed the colour
	 * RNG and set the initial state (not ready, lights off, 1 s tick).
	 */
	public BabelControlableLedChainDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		this.r = new SecureRandom((System.currentTimeMillis() + "").getBytes());
		this.ready = false;
		this.active = false;
		this.refreshRate = 1000;
	}

	/**
	 * Babel lifecycle hook, called once after the protocol is registered. It does
	 * two things, in the order Babel's contract requires:
	 *
	 * <ol>
	 *   <li><b>Wire up every handler first</b> — two timer handlers (animation
	 *       tick + screen clear), two reply handlers (the device-registration
	 *       reply and the gesture-arming reply), and the gesture notification
	 *       subscription. Registering before sending guarantees no reply can
	 *       arrive before its handler exists.</li>
	 *   <li><b>Then fire one {@link RegisterIoTDeviceRequest} per device.</b>
	 *       Each is addressed to the control protocol that owns its device type
	 *       and carries a distinct alias; the matching {@link RegisterIoTDeviceReply}s
	 *       come back asynchronously and are sorted out in
	 *       {@link #handleRegisterIoTDeviceReply}.</li>
	 * </ol>
	 *
	 * <p>Note the digital RGB strip registration also passes the GPIO line
	 * ({@code this.deviceLine}); I²C devices (gesture, matrix, LCD) need no line.
	 * The strip length comes from the {@code rgb.led.count} config key.
	 */
	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		// Timer handlers: the periodic animation tick and the one-shot screen wipe.
		registerTimerHandler(DemoTimer.TIMER_ID,
				this::handleDemoTimer);
		registerTimerHandler(ClearScreenTimer.TIMER_ID,
				this::handleClearScreenTimer);
		// Reply handlers: one for device registration (shared across all four
		// devices) and one for arming reactive gesture readings.
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
				this::handleRegisterIoTDeviceReply);
        registerReplyHandler(GestureInputReply.REPLY_ID,
                this::handleGestureInputReply);
		// Subscribe to gesture events — this is how the input protocol pushes
		// each detected swipe back to us once we have armed it (below).
		subscribeNotification(GestureNotification.NOTIFICATION_ID,
				this::handleGestureNotification);

		// Strip length: how many chained LEDs to animate (default 1 if unset).
		if (props.containsKey(DigitalOutputControlProtocol.RGB_LED_COUNT))
			this.numberOfLeds = Integer.parseInt(props.getProperty(DigitalOutputControlProtocol.RGB_LED_COUNT));
		else
			this.numberOfLeds = 1;

		System.err.println("Number of leds in chain: " + this.numberOfLeds);

		this.deviceLine = Integer.parseInt(props.getProperty(LED_PORT, LED_PORT_DEFAULT));

		// Register all four devices up front — one request per device, each to
		// the control protocol that owns that device type, each with its own
		// alias. The RGB strip is a digital-output device and needs the GPIO
		// line; the three I²C devices do not.
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_CHAINABLE_RGB, ledAlias, this.deviceLine),
				DigitalOutputControlProtocol.PROTOCOL_ID);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_GESTURE_DETECTOR, gestureAlias),
				I2CInputControlProtocol.PROTOCOL_ID);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX, matrixAlias),
				I2COutputControlProtocol.PROTOCOL_ID);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD, lcdAlias), I2COutputControlProtocol.PROTOCOL_ID);

		// Initialise the strip's colour state to all-off (RGB = 0,0,0 per LED).
		this.ledColors = new byte[numberOfLeds][3];
		for (int i = 0; i < numberOfLeds; i++)
			for (int j = 0; j < 3; j++)
				this.ledColors[i][j] = 0;
	}

	/**
	 * One-shot screen-clear timer handler (Babel timer). Wipes the LED matrix a
	 * short while after a gesture icon was shown — but only if nothing newer has
	 * been drawn meanwhile. The timer carries the {@code lastScreenUpdate} value
	 * it was scheduled with; if that no longer matches the field, a later gesture
	 * already redrew the matrix and owns its own clear timer, so we do nothing.
	 * This timestamp guard is how overlapping clear timers avoid wiping a fresh
	 * icon.
	 */
	public void handleClearScreenTimer(ClearScreenTimer t, long time) {
		if(this.lastScreenUpdate == t.getTimestamp()) {
			sendRequest(new ClearDisplayRequest(ledMatrix), I2COutputControlProtocol.PROTOCOL_ID);
		}
	}

	/**
	 * Animation tick handler (Babel timer). Each fire shifts the rolling rainbow
	 * one position down the strip, generates a fresh colour at the head from a
	 * point on the colour wheel, pushes the new colours to the hardware via
	 * {@link #updateLedsColors()}, and re-arms itself at the current
	 * {@code refreshRate}. Self-rescheduling (rather than a periodic timer) lets
	 * the LEFT/RIGHT gestures change the speed between ticks. Bails out early
	 * unless every device is registered ({@code ready}) and the lights are on
	 * ({@code active}).
	 */
	public void handleDemoTimer(DemoTimer t, long time) {
		if (!ready || !active)
			return;

		// Shift every LED's colour one step toward the tail (a marching effect).
		for (int i = (numberOfLeds - 1); i > 0; i--) {
			this.ledColors[i][0] = this.ledColors[i - 1][0];
			this.ledColors[i][1] = this.ledColors[i - 1][1];
			this.ledColors[i][2] = this.ledColors[i - 1][2];
		}

		// Pick a random point on the colour wheel for the new head LED.
		int step = r.nextInt(this.maxSteps);

		float ratio = (float) (step % maxSteps) / maxSteps;
		double angle = 2 * Math.PI * ratio;

		// Three sine waves 120° apart give a smooth rainbow R/G/B triple.
		this.ledColors[0][0] = (byte) (Math.sin(angle) * 127 + 128); // stays between 0 and 255
		this.ledColors[0][1] = (byte) (Math.sin(angle + (2.0 / 3.0 * Math.PI)) * 127 + 128); // 120° phase shift
		this.ledColors[0][2] = (byte) (Math.sin(angle + (4.0 / 3.0 * Math.PI)) * 127 + 128); // 240° phase shift
		System.err.println("Generated a total of " + this.ledColors[0].length + " bytes: " + this.ledColors[0][0]
				+ " : " + this.ledColors[0][1] + " : " + this.ledColors[0][2]);

		// Push the whole strip's new state to the hardware (one Babel request).
		updateLedsColors();

		// Re-arm at the current rate so a speed change takes effect next tick.
		setupTimer(new DemoTimer(), this.refreshRate);
	}

	/**
	 * Handler for {@link RegisterIoTDeviceReply} — the one place where the four
	 * device registrations get sorted out. Because this demo registers several
	 * devices and all the replies share a single reply id, this handler is
	 * invoked once per device with the replies arriving in arbitrary order. It
	 * therefore <b>dispatches on the device type/alias</b> to stash each
	 * {@link DeviceHandle} in the correct field, and double-checks the returned
	 * alias against the one we asked for (a defensive guard against a misrouted
	 * reply). Once all four handles are non-null the demo is fully wired, so it
	 * primes the displays and arms reactive gesture detection. A failed
	 * registration (missing hardware) aborts the process.
	 */
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply (" +
				rep.getDeviceType() +"). Success: " + rep.isSuccessful());

		if (rep.isSuccessful()) {
			// Dispatch on device type to file the handle under the right field;
			// the alias check confirms this reply really is the one we expected.
			if (rep.getDeviceType() == DeviceType.GROVE_CHAINABLE_RGB) {
				this.chainableLeds = rep.getDeviceHandle();
				// Safety verification
				if (!this.chainableLeds.getDeviceAlias().equals(ledAlias)) {
					System.err.println("Incorrect answer received, expected alias '" + ledAlias + "' received '"
							+ this.chainableLeds.getDeviceAlias() + "'");
					System.exit(1);
				}
				updateLedsColors();
			} else if (rep.getDeviceType() == DeviceType.GROVE_LCD) {
				this.lcd = rep.getDeviceHandle();
				// Safety verification
				if (!this.lcd.getDeviceAlias().equals(lcdAlias)) {
					System.err.println("Incorrect answer received, expected alias '" + lcdAlias + "' received '"
							+ this.lcd.getDeviceAlias() + "'");
					System.exit(1);
				}
				sendRequest(new ShowTextRequest(lcd, ""), protocolId);
			} else if (rep.getDeviceType() == DeviceType.GROVE_LED_MATRIX) {
				this.ledMatrix = rep.getDeviceHandle();
				// Safety verification
				if (!this.ledMatrix.getDeviceAlias().equals(matrixAlias)) {
					System.err.println("Incorrect answer received, expected alias '" + matrixAlias + "' received '"
							+ this.ledMatrix.getDeviceAlias() + "'");
					System.exit(1);
				}
			} else if (rep.getDeviceType() == DeviceType.GROVE_GESTURE_DETECTOR) {
				this.gesture = rep.getDeviceHandle();
				// Safety verification
				if (!this.gesture.getDeviceAlias().equals(gestureAlias)) {
					System.err.println("Incorrect answer received, expected alias '" + gestureAlias + "' received '"
							+ this.gesture.getDeviceAlias() + "'");
					System.exit(1);
				}
			} else {
				System.err.print("Unknown Device was registered: " + rep.getDeviceType() + "(" + rep.getDeviceAlias() + ")");
			}

			// Only once every handle is populated is the demo fully wired. Until
			// then this block is skipped on the earlier (partial) replies.
			if (this.chainableLeds != null && this.lcd != null && this.ledMatrix != null && this.gesture != null) {
				// We are ready to rumble
				System.err.println("All devices are ready");
				sendRequest(new ShowTextRequest(lcd, "Setting Up..."), I2COutputControlProtocol.PROTOCOL_ID);
				sendRequest(new SetDisplayColorRequest(ledMatrix, 0, 128, 128), I2COutputControlProtocol.PROTOCOL_ID);

				// Arm reactive gesture detection: ask the input protocol to notify
				// us whenever ANY of these four cardinal swipes occurs (a Threshold
				// predicate). From here on, swipes arrive as GestureNotifications.
				Set<PAJ7620GestureType> gestures = Set.of(PAJ7620GestureType.UP, PAJ7620GestureType.DOWN,
						PAJ7620GestureType.LEFT, PAJ7620GestureType.RIGHT);

				Threshold<PAJ7620GestureType> t = Threshold.any(gestures);
				this.ready = true;
				sendRequest(new GetReactiveGestureRequest(gesture, t), I2CInputControlProtocol.PROTOCOL_ID);
				sendRequest(new SetDisplayColorRequest(ledMatrix, 0, -1, 0), I2COutputControlProtocol.PROTOCOL_ID);
				sendRequest(new ShowTextRequest(lcd, "Lights Control Ready"), I2COutputControlProtocol.PROTOCOL_ID);
			}

		} else {
			System.err.println("Failed to register Device: " + rep.getDeviceType() + " (" + rep.getDeviceAlias() + ")"
					+ " error: " + rep.getErrorMessage());
			System.exit(1);
		}
	}

	/** Resets every LED to off (RGB 0,0,0) and pushes the dark strip to hardware. */
	private void clearLights() {
		for(int i = 0; i < this.numberOfLeds; i++)
			for(int j = 0; j < 3; j++)
				this.ledColors[i][j] = 0;

		updateLedsColors();
	}

	/**
	 * Pushes the in-memory {@code ledColors} state to the physical strip in a
	 * single Babel request. Builds one {@link SetMultipleChainableLEDColorRGBRequest}
	 * carrying every LED's colour and sends it to
	 * {@link DigitalOutputControlProtocol}, which performs the actual GPIO write —
	 * the app itself never touches Pi4J.
	 */
	private void updateLedsColors() {
		SetMultipleChainableLEDColorRGBRequest req = new SetMultipleChainableLEDColorRGBRequest(chainableLeds);
		for (byte i = (byte) (numberOfLeds - 1); i >= 0; i--)
			req.addValuesForPosition(i,this.ledColors[i]);

		sendRequest(req, DigitalOutputControlProtocol.PROTOCOL_ID);
	}

	/**
	 * Acknowledgement reply for the {@link GetReactiveGestureRequest} that armed
	 * gesture detection. On success there is nothing to do — the events will now
	 * flow to {@link #handleGestureNotification}. On failure the demo surfaces
	 * the error on the matrix (red) and LCD so the bring-up problem is visible on
	 * the hardware itself.
	 */
	public void handleGestureInputReply(GestureInputReply rep, short protocolId) {
		System.err.println("Received GestureInputReply Success: " + rep.isSuccessful());
		if (!rep.isSuccessful()) {
			System.err.println("Failed setting up readings on Gesture Device: " + rep.getErrorMessage());
			sendRequest(new SetDisplayColorRequest(ledMatrix, -1, 0, 0), I2COutputControlProtocol.PROTOCOL_ID);
			sendRequest(new ShowTextRequest(lcd, "Failed to setup readings on Gesture Device"),
					I2COutputControlProtocol.PROTOCOL_ID);
		}
	}

	/**
	 * The reactive heart of the demo: handler for {@link GestureNotification},
	 * the asynchronous events the input protocol pushes whenever the user swipes.
	 * This is where input drives output — each gesture both updates internal
	 * state and sends feedback requests to the matrix and LCD:
	 *
	 * <ul>
	 *   <li><b>UP</b> — turn the lights on and kick off the animation timer;</li>
	 *   <li><b>DOWN</b> — turn the lights off and clear the strip;</li>
	 *   <li><b>RIGHT</b> — speed the animation up (shorten the refresh rate);</li>
	 *   <li><b>LEFT</b> — slow the animation down (lengthen the refresh rate).</li>
	 * </ul>
	 *
	 * A gesture that would have no effect (e.g. UP when already on, or RIGHT at
	 * the speed limit) draws a "wrong" icon instead. Every branch stamps
	 * {@code lastScreenUpdate} and arms a {@link ClearScreenTimer} so the matrix
	 * icon is wiped a moment later.
	 */
	private void handleGestureNotification(GestureNotification not, short protocolId) {
		switch (not.getValue()) {
		case UP:
			// Turn lights ON (or show "wrong" if they already are).
			if(!this.active) {
				this.active = true;
				this.setupTimer(new DemoTimer(), this.refreshRate);
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowTextRequest(lcd, "Lights ON"), 
						I2COutputControlProtocol.PROTOCOL_ID);
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeArrow(Arrow.ARROW_UP)),
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			} else {
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeWrong()),
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			}
			break;
		case DOWN:
			// Turn lights OFF (or show "wrong" if they already are).
			if(this.active) {
				this.active = false;
				this.clearLights();
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowTextRequest(lcd, "Lights OFF"), 
						I2COutputControlProtocol.PROTOCOL_ID);
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeArrow(Arrow.ARROW_DOWN)),
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			} else {
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeWrong()),
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			}
			break;
		case RIGHT:
			// Speed up: shorten the refresh rate (or "wrong" at the fast limit).
			if(this.refreshRate > this.minimumUpdateRate) {
				this.refreshRate = this.refreshRate - this.refreshRateStep;
				if(this.refreshRate < this.minimumUpdateRate)
					this.refreshRate = minimumUpdateRate;
				
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeArrow(Arrow.ARROW_RIGTH)),
						I2COutputControlProtocol.PROTOCOL_ID);
				sendRequest(new ShowTextRequest(lcd, "Speed UP. Refresh Rate set to: " + this.refreshRate + " ms"), 
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			} else {
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeWrong()),
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			}
			break;
		case LEFT:
			// Slow down: lengthen the refresh rate (or "wrong" at the slow limit).
			if(this.refreshRate < this.maxUpdateRate) {
				this.refreshRate = this.refreshRate + this.refreshRateStep;
				if(this.refreshRate > this.maxUpdateRate)
					this.refreshRate = maxUpdateRate;
				
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeArrow(Arrow.ARROW_LEFT)),
						I2COutputControlProtocol.PROTOCOL_ID);
				sendRequest(new ShowTextRequest(lcd, "Speed DOWN. Refresh Rate set to: " + this.refreshRate + " ms"), 
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			} else {
				this.lastScreenUpdate = System.currentTimeMillis();
				sendRequest(new ShowDisplayRequest(ledMatrix, LedMatrixUtils.encodeWrong()),
						I2COutputControlProtocol.PROTOCOL_ID);
				this.setupTimer(new ClearScreenTimer(this.lastScreenUpdate), 1500);
			}
			
			break;
		default:
			// Any non-cardinal gesture is ignored.
			return;
		}
	}

	/**
	 * Bootstraps the demo's Babel runtime — the entry point {@code Main} calls.
	 * Unlike the single-device demos, this one needs <b>three</b> control
	 * protocols, so it instantiates and registers all of them plus this demo:
	 *
	 * <ul>
	 *   <li>{@link DigitalOutputControlProtocol} (2300) — drives the RGB strip;</li>
	 *   <li>{@link I2COutputControlProtocol} (2000) — drives the matrix and LCD;</li>
	 *   <li>{@link I2CInputControlProtocol} (2100) — reads the gesture detector.</li>
	 * </ul>
	 *
	 * <p>The steps are: get the {@code Babel} singleton, load config, register
	 * every protocol, then {@code init} each one (the control protocols set up
	 * their request handlers; this demo's {@code init} fires the registration
	 * requests), and finally {@code Babel.start()} hands control to the event
	 * loop. No Pi4J or GPIO setup here — the control protocols obtain the shared
	 * context themselves; the demo only ever speaks Babel.
	 */
	@Override
	public void execute() throws Exception {
		// Babel is a per-process singleton; grab it and load the config file.
		Babel b = Babel.getInstance();

		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

		BabelControlableLedChainDemo demo = this;

		// Instantiate the three control protocols this demo drives, plus itself.
		DigitalOutputControlProtocol dout = new DigitalOutputControlProtocol();
		I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
		I2CInputControlProtocol i2cin = new I2CInputControlProtocol();

		// Register everything with Babel before initialising any of it.
		b.registerProtocol(dout);
		b.registerProtocol(i2cout);
		b.registerProtocol(i2cin);
		b.registerProtocol(demo);

		// Init the control protocols first (they wire their request handlers),
		// then the demo (whose init sends the device-registration requests those
		// handlers will service once the event loop runs).
		i2cout.init(props);
		i2cin.init(props);
		dout.init(props);
		demo.init(props);

		System.out.println("Setup is complete.");

		// Start the event loop — from here on everything is driven by the
		// replies, notifications, and timers wired up above.
		b.start();

		System.out.println("System is running.");
	}

}
