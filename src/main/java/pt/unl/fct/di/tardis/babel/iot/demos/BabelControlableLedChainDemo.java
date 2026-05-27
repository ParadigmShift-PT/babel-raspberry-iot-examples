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

public class BabelControlableLedChainDemo extends GenericProtocol implements BabelDemo {

	public static final String LED_PORT = "led.line";
	public static final String LED_PORT_DEFAULT = "26";

	private DeviceHandle chainableLeds;
	private DeviceHandle ledMatrix;
	private DeviceHandle lcd;
	private DeviceHandle gesture;

	public final static String ledAlias = "leds";
	public final static String matrixAlias = "matrix";
	public final static String lcdAlias = "text";
	public final static String gestureAlias = "jedi";

	private int deviceLine;
	private final Random r;

	private int numberOfLeds;
	private byte[][] ledColors;

	private boolean ready;
	private boolean active;
	
	private long lastScreenUpdate;
	private final long minimumUpdateRate = 100;
	private final long maxUpdateRate = 2000;
	
	private final long refreshRateStep = 100;
	
	private long refreshRate;
	
	private final int maxSteps = 100;

	public BabelControlableLedChainDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		this.r = new SecureRandom((System.currentTimeMillis() + "").getBytes());
		this.ready = false;
		this.active = false;
		this.refreshRate = 1000;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		registerTimerHandler(DemoTimer.TIMER_ID,
				this::handleDemoTimer);
		registerTimerHandler(ClearScreenTimer.TIMER_ID, 
				this::handleClearScreenTimer);
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, 
				this::handleRegisterIoTDeviceReply);
        registerReplyHandler(GestureInputReply.REPLY_ID,
                this::handleGestureInputReply);
		subscribeNotification(GestureNotification.NOTIFICATION_ID, 
				this::handleGestureNotification);
		
		if (props.containsKey(DigitalOutputControlProtocol.RGB_LED_COUNT))
			this.numberOfLeds = Integer.parseInt(props.getProperty(DigitalOutputControlProtocol.RGB_LED_COUNT));
		else
			this.numberOfLeds = 1;

		System.err.println("Number of leds in chain: " + this.numberOfLeds);

		this.deviceLine = Integer.parseInt(props.getProperty(LED_PORT, LED_PORT_DEFAULT));

		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_CHAINABLE_RGB, ledAlias, this.deviceLine),
				DigitalOutputControlProtocol.PROTOCOL_ID);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_GESTURE_DETECTOR, gestureAlias),
				I2CInputControlProtocol.PROTOCOL_ID);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX, matrixAlias),
				I2COutputControlProtocol.PROTOCOL_ID);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD, lcdAlias), I2COutputControlProtocol.PROTOCOL_ID);

		this.ledColors = new byte[numberOfLeds][3];
		for (int i = 0; i < numberOfLeds; i++)
			for (int j = 0; j < 3; j++)
				this.ledColors[i][j] = 0;
	}

	public void handleClearScreenTimer(ClearScreenTimer t, long time) {
		if(this.lastScreenUpdate == t.getTimestamp()) {
			sendRequest(new ClearDisplayRequest(ledMatrix), I2COutputControlProtocol.PROTOCOL_ID);
		}
	}
	
	public void handleDemoTimer(DemoTimer t, long time) {
		if (!ready || !active)
			return;

		for (int i = (numberOfLeds - 1); i > 0; i--) {
			this.ledColors[i][0] = this.ledColors[i - 1][0];
			this.ledColors[i][1] = this.ledColors[i - 1][1];
			this.ledColors[i][2] = this.ledColors[i - 1][2];
		}

		int step = r.nextInt(this.maxSteps);

		float ratio = (float) (step % maxSteps) / maxSteps;
		double angle = 2 * Math.PI * ratio;

		this.ledColors[0][0] = (byte) (Math.sin(angle) * 127 + 128); // stays between 0 and 255
		this.ledColors[0][1] = (byte) (Math.sin(angle + (2.0 / 3.0 * Math.PI)) * 127 + 128); // 120° phase shift
		this.ledColors[0][2] = (byte) (Math.sin(angle + (4.0 / 3.0 * Math.PI)) * 127 + 128); // 240° phase shift
		System.err.println("Generated a total of " + this.ledColors[0].length + " bytes: " + this.ledColors[0][0]
				+ " : " + this.ledColors[0][1] + " : " + this.ledColors[0][2]);

		updateLedsColors();
		
		setupTimer(new DemoTimer(), this.refreshRate);
	}

	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply (" + 
				rep.getDeviceType() +"). Success: " + rep.isSuccessful());

		if (rep.isSuccessful()) {
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

			if (this.chainableLeds != null && this.lcd != null && this.ledMatrix != null && this.gesture != null) {
				// We are ready to rumble
				System.err.println("All devices are ready");
				sendRequest(new ShowTextRequest(lcd, "Setting Up..."), I2COutputControlProtocol.PROTOCOL_ID);
				sendRequest(new SetDisplayColorRequest(ledMatrix, 0, 128, 128), I2COutputControlProtocol.PROTOCOL_ID);

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

	private void clearLights() {
		for(int i = 0; i < this.numberOfLeds; i++)
			for(int j = 0; j < 3; j++)
				this.ledColors[i][j] = 0;
		
		updateLedsColors();
	}
	
	private void updateLedsColors() {
		SetMultipleChainableLEDColorRGBRequest req = new SetMultipleChainableLEDColorRGBRequest(chainableLeds);
		for (byte i = (byte) (numberOfLeds - 1); i >= 0; i--)
			req.addValuesForPosition(i,this.ledColors[i]);

		sendRequest(req, DigitalOutputControlProtocol.PROTOCOL_ID);
	}

	public void handleGestureInputReply(GestureInputReply rep, short protocolId) {
		System.err.println("Received GestureInputReply Success: " + rep.isSuccessful());
		if (!rep.isSuccessful()) {
			System.err.println("Failed setting up readings on Gesture Device: " + rep.getErrorMessage());
			sendRequest(new SetDisplayColorRequest(ledMatrix, -1, 0, 0), I2COutputControlProtocol.PROTOCOL_ID);
			sendRequest(new ShowTextRequest(lcd, "Failed to setup readings on Gesture Device"), 
					I2COutputControlProtocol.PROTOCOL_ID);
		}
	}

	private void handleGestureNotification(GestureNotification not, short protocolId) {
		switch (not.getValue()) {
		case UP:
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
			return;
		}
	}

	@Override
	public void execute() throws Exception {
		Babel b = Babel.getInstance();

		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

		BabelControlableLedChainDemo demo = this;

		DigitalOutputControlProtocol dout = new DigitalOutputControlProtocol();
		I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
		I2CInputControlProtocol i2cin = new I2CInputControlProtocol();

		b.registerProtocol(dout);
		b.registerProtocol(i2cout);
		b.registerProtocol(i2cin);
		b.registerProtocol(demo);

		i2cout.init(props);
		i2cin.init(props);
		dout.init(props);
		demo.init(props);

		System.out.println("Setup is complete.");

		b.start();

		System.out.println("System is running.");
	}

}
