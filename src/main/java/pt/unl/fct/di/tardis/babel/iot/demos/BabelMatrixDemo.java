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

public class BabelMatrixDemo extends GenericProtocol implements BabelDemo {

	private DeviceHandle matrixDevice;
	
	private final double chanceAnimation = 0.35;
	private final double changeImage = 0.35;
	//private final double changeTaRDIS = 1 - (chanceAnimation + changeImage);
	
	private SecureRandom r;
	
	private int sequencePosition;
	
	
	public BabelMatrixDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		r = new SecureRandom((System.currentTimeMillis()+"").getBytes());
		this.sequencePosition = 0;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, 
				this::handleRegisterIoTDeviceReply);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX, 
				"Matrix"), I2COutputControlProtocol.PROTOCOL_ID);
	}

	
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
				this.sequencePosition = -1;
				sendRequest( new SetDisplayColorRequest(this.matrixDevice,255,0,0) , I2COutputControlProtocol.PROTOCOL_ID);
				setupTimer(new DemoTimer(), 2000); //2 seconds wait
				return;
			}
			
			if(encodingType == 0)
				matrix = LedMatrixUtils.encodeLetter(c);
			else if(encodingType == 1)
				matrix = LedMatrixUtils.encodeInvertedLetter(c);
			else if(encodingType == 3)
				matrix = LedMatrixUtils.encodeLetterMosaic(c);
			else
				matrix = LedMatrixUtils.encodeInvertedLetterMosaic(c);
			
			sendRequest(new ShowDisplayRequest(matrixDevice, matrix), I2COutputControlProtocol.PROTOCOL_ID);
			
			this.sequencePosition++;
			if(this.sequencePosition==6)
				this.sequencePosition=-1;
			
			setupTimer(new DemoTimer(), 2100); //2 seconds wait
			return;
		} else {
			double dice = r.nextDouble();
			if(dice < this.chanceAnimation) {
				Emoji[] emojis = Emoji.values();
				sendRequest(new ShowEmojiRequest(matrixDevice, emojis[r.nextInt(emojis.length)]), I2COutputControlProtocol.PROTOCOL_ID);
				setupTimer(new DemoTimer(), 1700); //1.7 seconds wait
			} else if (dice < (this.chanceAnimation + this.changeImage)) {
				Animation[] animations = Animation.values();
				sendRequest(new ShowAnimationRequest(matrixDevice, animations[r.nextInt(animations.length)]), I2COutputControlProtocol.PROTOCOL_ID);
				setupTimer(new DemoTimer(), 3500); //3.5 seconds wait
			} else {
				//Show TARDIS
				this.sequencePosition = 0;
				this.handleDemoTimer(t, time);
			}
		}
	}
	
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply. Success: " + rep.isSuccessful());
		if(rep.isSuccessful()) {
			this.matrixDevice = rep.getDeviceHandle();
			//Safety verification
			if(!this.matrixDevice.getDeviceAlias().equals("Matrix")) {
				System.err.println("Incorrect answer received, expected alias 'Matrix' received '" + this.matrixDevice.getDeviceAlias() + "'");
				System.exit(1);
			}
			
			sendRequest( new SetDisplayColorRequest(this.matrixDevice,0,-1,0) , I2COutputControlProtocol.PROTOCOL_ID);
			
			setupTimer(new DemoTimer(), 2000); //2 seconds wait
				
		} else {
			System.err.println("Failed to register LedMatrix Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}
	
	@Override
	public void execute() throws Exception {
		Babel b = Babel.getInstance();
		
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");
		
		BabelMatrixDemo lcd = this;

		I2COutputControlProtocol i2cout = new I2COutputControlProtocol();

		b.registerProtocol(i2cout);
		b.registerProtocol(lcd);

		i2cout.init(props);
		lcd.init(props);

		System.out.println("Setup is complete.");

		b.start();

		System.out.println("System is running.");
	}

}
