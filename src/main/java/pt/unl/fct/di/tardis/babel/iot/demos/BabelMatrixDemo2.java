package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.util.Properties;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.iot.device.i2c.GroveLedMatrix;
import pt.unl.fct.di.novasys.iot.device.i2c.utils.LedMatrixUtils;
import pt.unl.fct.di.novasys.iot.device.i2c.utils.LedMatrixUtils.Arrow;
import pt.unl.fct.di.novasys.iot.device.i2c.utils.LedMatrixUtils.Symbol;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceHandle;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceType;
import pt.unl.fct.di.tardis.babel.iot.api.replies.RegisterIoTDeviceReply;
import pt.unl.fct.di.tardis.babel.iot.api.requests.RegisterIoTDeviceRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2COutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetDisplayColorRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowDisplayRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Extended LED-matrix demo: a companion to {@link BabelMatrixDemo} that steps
 * through a longer, fixed catalogue of frames added by ParadigmShift — coloured
 * letters, the full set of arrow glyphs (plain, inverted, mosaic), and the
 * standard symbols (forbidden / OK / wrong / mandatory direction signs), several
 * of them painted with explicit or randomised colours.
 *
 * <p>Like {@link BabelMatrixDemo} it drives the single I²C LED matrix through
 * {@link I2COutputControlProtocol} (id 2000), registering it with a
 * {@link RegisterIoTDeviceRequest} (no GPIO line — it is an I²C device) and
 * painting each frame as a raw 8×8 bitmap via {@link ShowDisplayRequest}, with
 * {@link SetDisplayColorRequest} used to clear the panel between cycles.
 *
 * <p>The Babel concept this variant illustrates is the <b>fixed-rate periodic
 * timer</b>: instead of re-arming a one-shot timer per frame, it advances one
 * {@code sequencePosition} step on every {@link DemoTimer} tick from a single
 * {@code setupPeriodicTimer}. (Compare {@link BabelMatrixDemo}'s self-rescheduling
 * one-shot approach.)
 *
 * <p>The application never touches Pi4J or the I²C bus directly — it only sends
 * Babel requests; {@link I2COutputControlProtocol} does the hardware work.
 *
 * <p>Run with the {@code LedMatrix2} command-line name (see {@code Main}).
 */
public class BabelMatrixDemo2 extends GenericProtocol implements BabelDemo {

	/** Handle to the LED matrix once {@link I2COutputControlProtocol} has registered it. */
	private DeviceHandle matrixDevice;

	/** Index of the next frame in the fixed catalogue; wraps back to 0 at the end. */
	private int sequencePosition;

	/**
	 * Sets the protocol identity Babel uses to route events here. No hardware is
	 * touched yet — registration happens in {@link #init}.
	 */
	public BabelMatrixDemo2() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		this.sequencePosition = 0;
	}

	/**
	 * Babel lifecycle hook: register the timer and reply handlers, then request
	 * the matrix. The {@link RegisterIoTDeviceReply} handler is registered before
	 * the {@link RegisterIoTDeviceRequest} is sent so the reply always lands on a
	 * handler.
	 */
	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		// Handler invoked on each periodic tick to paint the next frame.
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		// Handler that receives the DeviceHandle for the matrix.
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, this::handleRegisterIoTDeviceReply);
		// Ask the I²C output protocol for the LED matrix (I²C device: no GPIO line).
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX, "Matrix"),
				I2COutputControlProtocol.PROTOCOL_ID);
	}

	/**
	 * Periodic timer handler that paints one catalogue frame per tick. It selects
	 * the frame by {@code sequencePosition}, encodes it into an 8×8 bitmap with
	 * {@code LedMatrixUtils}, and sends it as a {@link ShowDisplayRequest}; when
	 * the sequence runs out it clears the panel and wraps back to the start. The
	 * tick cadence is owned by the {@code setupPeriodicTimer} armed in the reply
	 * handler, so this method just advances state and emits one display request.
	 */
	public void handleDemoTimer(DemoTimer t, long time) {

		byte[] matrix = null;
		switch (this.sequencePosition) {
		case 0:
			matrix = LedMatrixUtils.encodeLetter('T', GroveLedMatrix.yellow, GroveLedMatrix.red);
			break;
		case 1:
			matrix = LedMatrixUtils.encodeLetter('A', GroveLedMatrix.blue);
			break;
		case 2:
			matrix = LedMatrixUtils.encodeInvertedLetter('R', GroveLedMatrix.purple);
			break;
		case 3:
			matrix = LedMatrixUtils.encodeLetterMosaic('D');
			break;
		case 4:
			matrix = LedMatrixUtils.encodeInvertedLetterMosaic('I');
			break;
		case 5:
			matrix = LedMatrixUtils.encodeLetter('S');
			break;
		case 6:
			matrix = LedMatrixUtils.encodeArrow(Arrow.ARROW_UP);
			break;
		case 7:
			matrix = LedMatrixUtils.encodeArrow(Arrow.ARROW_RIGTH, GroveLedMatrix.red, GroveLedMatrix.yellow);
			break;
		case 8:
			matrix = LedMatrixUtils.encodeArrow(Arrow.ARROW_DOWN, GroveLedMatrix.cyan);
			break;
		case 9:
			matrix = LedMatrixUtils.encodeInvertedArrow(Arrow.ARROW_LEFT);
			break;
		case 10:
			matrix = LedMatrixUtils.encodeInvertedArrow(Arrow.ARROW_UP, GroveLedMatrix.orange);
			break;
		case 11:
			matrix = LedMatrixUtils.encodeInvertedMosaicArrow(Arrow.ARROW_RIGTH);
			break;
		case 12:
			matrix = LedMatrixUtils.encodeArrow(Arrow.ARROW_DOWN, GroveLedMatrix.pink, GroveLedMatrix.green);
			break;
		case 13:
			matrix = LedMatrixUtils.encodeMosaicArrow(Arrow.ARROW_LEFT);
			break;
		case 14:
			matrix = LedMatrixUtils.encodeArrow(Arrow.ARROW_UP);
			break;
		case 15:
			matrix = LedMatrixUtils.encodeForbidden();
			break;
		case 16:
			matrix = LedMatrixUtils.encodeOk();
			break;
		case 17:
			matrix = LedMatrixUtils.encodeWrong();
			break;
		case 18:
			matrix = LedMatrixUtils.encodeMandatoryFront();
			break;
		case 19:
			matrix = LedMatrixUtils.encodeMandatoryRight();
			break;
		case 20:
			matrix = LedMatrixUtils.encodeMandatoryBack();
			break;
		case 21:
			matrix = LedMatrixUtils.encodeMandatoryLeft();
			break;
		case 22:
			matrix = LedMatrixUtils.encodeSymbol(Symbol.FORBIDDEN, GroveLedMatrix.pink, GroveLedMatrix.black,
					GroveLedMatrix.white);
			break;
		case 23:
			matrix = LedMatrixUtils.encodeSymbol(Symbol.OK, GroveLedMatrix.red, GroveLedMatrix.black,
					GroveLedMatrix.white);
			break;
		case 24:
			matrix = LedMatrixUtils.encodeSymbol(Symbol.WRONG, GroveLedMatrix.yellow, GroveLedMatrix.black,
					GroveLedMatrix.white);
			break;
		case 25:
			matrix = LedMatrixUtils.encodeSymbol(Symbol.MANDATORY_FRONT, GroveLedMatrix.red, GroveLedMatrix.green,
					GroveLedMatrix.black);
			break;
		case 26:
			matrix = LedMatrixUtils.encodeSymbol(Symbol.MANDATORY_RIGHT, LedMatrixUtils.randomColor(), 
					LedMatrixUtils.randomColor(), LedMatrixUtils.randomColor());
			break;
		case 27:
			matrix = LedMatrixUtils.encodeSymbol(Symbol.MANDATORY_BACK, LedMatrixUtils.randomColor(), 
					LedMatrixUtils.randomColor(), LedMatrixUtils.randomColor());
			break;
		case 28:
			matrix = LedMatrixUtils.encodeSymbol(Symbol.MANDATORY_LEFT, LedMatrixUtils.randomColor(), 
					LedMatrixUtils.randomColor(), LedMatrixUtils.randomColor());
			break;
		default:
			// End of catalogue: blank the panel and wrap to the first frame.
			this.sequencePosition = 0;
			sendRequest(new SetDisplayColorRequest(matrixDevice, 0, 0, 0), I2COutputControlProtocol.PROTOCOL_ID);
			return;
		}

		// Push the encoded frame to the panel as a raw bitmap.
		sendRequest(new ShowDisplayRequest(matrixDevice, matrix), I2COutputControlProtocol.PROTOCOL_ID);

		this.sequencePosition++; // advance; next tick paints the following frame
	}

	/**
	 * Reply handler for the device registration: keeps the matrix
	 * {@link DeviceHandle} and starts the slideshow. Checks
	 * {@link RegisterIoTDeviceReply#isSuccessful()} and the alias before using the
	 * handle, paints an initial green fill, then arms a fixed-rate periodic timer
	 * that drives {@link #handleDemoTimer} every 2s.
	 */
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply. Success: " + rep.isSuccessful());
		if (rep.isSuccessful()) {
			// Keep the handle; every display request below carries it.
			this.matrixDevice = rep.getDeviceHandle();
			// Safety verification
			if (!this.matrixDevice.getDeviceAlias().equals("Matrix")) {
				System.err.println("Incorrect answer received, expected alias 'Matrix' received '"
						+ this.matrixDevice.getDeviceAlias() + "'");
				System.exit(1);
			}

			// Initial full-panel green fill (-1 == 0xFF, full green).
			sendRequest(new SetDisplayColorRequest(this.matrixDevice, 0, -1, 0), I2COutputControlProtocol.PROTOCOL_ID);

			// Fixed-rate timer: every tick advances the slideshow by one frame.
			setupPeriodicTimer(new DemoTimer(), 2000, 2000); // 2 seconds wait

		} else {
			System.err.println("Failed to register LedMatrix Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}

	/**
	 * Application bootstrap (the entry point {@code Main} calls for the
	 * {@code LedMatrix2} demo). Standard Babel start-up: get the {@link Babel}
	 * singleton, load config, instantiate the one control protocol it needs
	 * ({@link I2COutputControlProtocol}), register both protocols, {@code init}
	 * them in dependency order (controller before demo), then {@code b.start()}
	 * to enter the event loop.
	 */
	@Override
	public void execute() throws Exception {
		Babel b = Babel.getInstance();

		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

		BabelMatrixDemo2 lcd = this;

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
