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

public class BabelChainableLedsHSBDemo extends GenericProtocol implements BabelDemo {

	public static final String LED_PORT = "led.line";
	public static final String LED_PORT_DEFAULT = "26";
	
	private DeviceHandle chainableLeds;
		
	public final static String ledAlias = "leds";
	private int deviceLine;
	private final Random r;
	
	private int numberOfLeds;
	private float[][] ledColors;
	
	private final int maxSteps;
	private int step;
			
	public BabelChainableLedsHSBDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		this.r = new SecureRandom((System.currentTimeMillis() + "").getBytes());
		this.maxSteps = 100;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, 
				this::handleRegisterIoTDeviceReply);
		
		if(props.containsKey(DigitalOutputControlProtocol.RGB_LED_COUNT))
			this.numberOfLeds = Integer.parseInt(props.getProperty(DigitalOutputControlProtocol.RGB_LED_COUNT));
		else
			this.numberOfLeds = 1;
		
		System.err.println("Number of leds in chain: " + this.numberOfLeds);
		
		this.deviceLine = Integer.parseInt(props.getProperty(LED_PORT, LED_PORT_DEFAULT));
		
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_CHAINABLE_RGB, 
				ledAlias, this.deviceLine), DigitalOutputControlProtocol.PROTOCOL_ID);
		
		this.ledColors = new float[numberOfLeds][3];
		for(int i = 0; i < numberOfLeds; i++)
			for(int j = 0; j < 3; j++)
				this.ledColors[i][j] = 0;
	}

	
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
			
			setupPeriodicTimer(new DemoTimer(), 700, 700); //700 Milliseconds wait
				
		} else {
			System.err.println("Failed to register ChainableLed Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}
	
	private void updateLedsColors() {
		SetMultipleChainableLEDColorHSBRequest req = new SetMultipleChainableLEDColorHSBRequest(chainableLeds);
		for (byte i = (byte) (numberOfLeds - 1); i >= 0; i--)
			req.addValuesForPosition(i,this.ledColors[i]);

		sendRequest(req, DigitalOutputControlProtocol.PROTOCOL_ID);
	}
	
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
