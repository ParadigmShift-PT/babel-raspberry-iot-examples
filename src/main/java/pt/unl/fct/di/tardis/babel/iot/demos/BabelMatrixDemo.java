package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Properties;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.iot.device.i2c.GroveLedMatrix.Animation;
import pt.unl.fct.di.novasys.iot.device.i2c.GroveLedMatrix.Emoji;
import pt.unl.fct.di.novasys.iot.device.i2c.utils.LedMatrixUtils;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceHandle;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceType;
import pt.unl.fct.di.tardis.babel.iot.api.replies.RegisterIoTDeviceReply;
import pt.unl.fct.di.tardis.babel.iot.api.requests.RegisterIoTDeviceRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2COutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetDisplayColorRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowAnimationRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowDisplayRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowEmojiRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Demo that cycles a sequence of images on the Grove RGB LED matrix: it spells
 * out "TARDIS" letter by letter, then randomly alternates between built-in
 * emojis, built-in animations, and another pass of the "TARDIS" spelling.
 *
 * <p>It drives a single output device — the LED matrix — through
 * {@link I2COutputControlProtocol} (id 2000). The matrix is an I²C peripheral,
 * so {@link RegisterIoTDeviceRequest} carries only a device type and an alias
 * (no GPIO line). Once registered, the demo paints frames with three typed
 * requests served by that protocol:
 * <ul>
 *   <li>{@link ShowDisplayRequest} — a raw 8×8 colour bitmap (here built by
 *       {@code LedMatrixUtils} from a character);</li>
 *   <li>{@link ShowEmojiRequest} / {@link ShowAnimationRequest} — built-in
 *       {@code Emoji} / {@code Animation} catalogue entries;</li>
 *   <li>{@link SetDisplayColorRequest} — fills the whole panel with one colour.</li>
 * </ul>
 *
 * <p>The Babel concept on show is a <b>self-rescheduling one-shot timer</b>: each
 * frame arms a fresh {@link DemoTimer} with its own delay (a long animation gets
 * more time than a static glyph), so the cadence adapts to what was just shown.
 * Contrast this with the fixed-rate {@code setupPeriodicTimer} used by the LCD
 * demo.
 *
 * <p>The application never touches Pi4J or the I²C bus directly — it only sends
 * Babel requests; {@link I2COutputControlProtocol} does the hardware work.
 *
 * <p>Run with the {@code LedMatrix} command-line name (see {@code Main}).
 */
public class BabelMatrixDemo extends GenericProtocol implements BabelDemo {

	/** Handle to the LED matrix once {@link I2COutputControlProtocol} has registered it. */
	private DeviceHandle matrixDevice;

	/** Probability of showing a random emoji on a non-"TARDIS" tick. */
	private final double chanceAnimation = 0.35;
	/** Probability of showing a random animation on a non-"TARDIS" tick. */
	private final double changeImage = 0.35;
	//private final double changeTaRDIS = 1 - (chanceAnimation + changeImage);

	/** Source of randomness for picking frames and encodings. */
	private SecureRandom r;

	/** Index into the "TARDIS" spelling (0..5); {@code -1} means "not spelling". */
	private int sequencePosition;


	/**
	 * Sets the protocol identity Babel uses to route events here and seeds the
	 * randomness. No hardware is touched yet — registration happens in {@link #init}.
	 */
	public BabelMatrixDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		r = new SecureRandom((System.currentTimeMillis()+"").getBytes());
		this.sequencePosition = 0;
	}

	/**
	 * Babel lifecycle hook: register handlers, then request the device.
	 *
	 * <p>Canonical IoT bootstrap, in order: register the timer handler that will
	 * paint frames, register the {@link RegisterIoTDeviceReply} handler that
	 * brings back the matrix's {@link DeviceHandle}, and only then send the
	 * {@link RegisterIoTDeviceRequest} to the I²C output protocol (2000). The
	 * reply handler must exist before the request is sent.
	 */
	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		// Handler invoked on every (re-)armed DemoTimer tick.
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		// Handler that receives the DeviceHandle for the matrix.
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
				this::handleRegisterIoTDeviceReply);
		// Ask the I²C output protocol for the LED matrix (I²C device: no GPIO line).
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX,
				"Matrix"), I2COutputControlProtocol.PROTOCOL_ID);
	}


	/**
	 * Timer handler that paints one frame and reschedules itself. While
	 * {@code sequencePosition >= 0} it spells the next "TARDIS" letter (with a
	 * randomly chosen glyph encoding); once the word is done it flips to the
	 * random emoji/animation/respell branch. Each branch arms a new one-shot
	 * {@link DemoTimer} sized to how long that frame should linger — the
	 * adaptive-cadence pattern this demo illustrates.
	 */
	public void handleDemoTimer(DemoTimer t, long time) {
		if(this.sequencePosition >= 0) {
			byte[] matrix = null;
			int encodingType = r.nextInt(4);
			char c = 'T';
			switch(this.sequencePosition) {
			case 0:
				c = 'T';
				break;
			case 1:
				c = 'A';
				break;
			case 2:
				c = 'R';
				break;
			case 3:
				c = 'D';
				break;
			case 4:
				c = 'I';
				break;
			case 5:
				c = 'S';
				break;
			default:
				// Word finished: leave spelling mode, flash the panel red, and
				// re-arm the timer to enter the random-frame branch next tick.
				this.sequencePosition = -1;
				sendRequest( new SetDisplayColorRequest(this.matrixDevice,255,0,0) , I2COutputControlProtocol.PROTOCOL_ID);
				setupTimer(new DemoTimer(), 2000); //2 seconds wait
				return;
			}

			// Pick one of four glyph encodings at random for variety.
			if(encodingType == 0)
				matrix = LedMatrixUtils.encodeLetter(c);
			else if(encodingType == 1)
				matrix = LedMatrixUtils.encodeInvertedLetter(c);
			else if(encodingType == 3)
				matrix = LedMatrixUtils.encodeLetterMosaic(c);
			else
				matrix = LedMatrixUtils.encodeInvertedLetterMosaic(c);

			// Push the encoded glyph to the panel as a raw bitmap frame.
			sendRequest(new ShowDisplayRequest(matrixDevice, matrix), I2COutputControlProtocol.PROTOCOL_ID);

			this.sequencePosition++;
			if(this.sequencePosition==6)
				this.sequencePosition=-1;

			// Re-arm for the next letter; ~2.1s lets each glyph stay readable.
			setupTimer(new DemoTimer(), 2100); //2 seconds wait
			return;
		} else {
			// Random-frame branch: weighted choice between emoji, animation, or
			// restarting the "TARDIS" spelling.
			double dice = r.nextDouble();
			if(dice < this.chanceAnimation) {
				// Show a random catalogue emoji.
				Emoji[] emojis = Emoji.values();
				sendRequest(new ShowEmojiRequest(matrixDevice, emojis[r.nextInt(emojis.length)]), I2COutputControlProtocol.PROTOCOL_ID);
				setupTimer(new DemoTimer(), 1700); //1.7 seconds wait
			} else if (dice < (this.chanceAnimation + this.changeImage)) {
				// Show a random catalogue animation; give it longer to play out.
				Animation[] animations = Animation.values();
				sendRequest(new ShowAnimationRequest(matrixDevice, animations[r.nextInt(animations.length)]), I2COutputControlProtocol.PROTOCOL_ID);
				setupTimer(new DemoTimer(), 3500); //3.5 seconds wait
			} else {
				//Show TARDIS
				// Re-enter spelling mode and paint the first letter immediately by
				// re-invoking this handler (which will arm its own timer).
				this.sequencePosition = 0;
				this.handleDemoTimer(t, time);
			}
		}
	}

	/**
	 * Reply handler for the device registration: keeps the matrix
	 * {@link DeviceHandle} and kicks off the animation loop. Always checks
	 * {@link RegisterIoTDeviceReply#isSuccessful()} and verifies the alias before
	 * trusting the handle, then paints an initial green fill and arms the first
	 * {@link DemoTimer} that drives {@link #handleDemoTimer}.
	 */
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply. Success: " + rep.isSuccessful());
		if(rep.isSuccessful()) {
			// Keep the handle; every later display request carries it.
			this.matrixDevice = rep.getDeviceHandle();
			//Safety verification
			if(!this.matrixDevice.getDeviceAlias().equals("Matrix")) {
				System.err.println("Incorrect answer received, expected alias 'Matrix' received '" + this.matrixDevice.getDeviceAlias() + "'");
				System.exit(1);
			}

			// Initial full-panel green fill (note: -1 == 0xFF, i.e. full green).
			sendRequest( new SetDisplayColorRequest(this.matrixDevice,0,-1,0) , I2COutputControlProtocol.PROTOCOL_ID);

			// Arm the first one-shot timer; the handler re-arms itself thereafter.
			setupTimer(new DemoTimer(), 2000); //2 seconds wait

		} else {
			System.err.println("Failed to register LedMatrix Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}

	/**
	 * Application bootstrap (the entry point {@code Main} calls for the
	 * {@code LedMatrix} demo). Standard Babel start-up: get the {@link Babel}
	 * singleton, load config, instantiate the one control protocol it needs
	 * ({@link I2COutputControlProtocol}), register both protocols, {@code init}
	 * them in dependency order (controller before demo), then {@code b.start()}
	 * to enter the event loop.
	 */
	@Override
	public void execute() throws Exception {
		Babel b = Babel.getInstance();

		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

		BabelMatrixDemo lcd = this;

		// The control protocol that owns the matrix hardware.
		I2COutputControlProtocol i2cout = new I2COutputControlProtocol();

		// Register both protocols with Babel before initialising either.
		b.registerProtocol(i2cout);
		b.registerProtocol(lcd);

		// Controller first so it can service the demo's register request.
		i2cout.init(props);
		lcd.init(props);

		System.out.println("Setup is complete.");

		// Hand control to the Babel event loop.
		b.start();

		System.out.println("System is running.");
	}

}
