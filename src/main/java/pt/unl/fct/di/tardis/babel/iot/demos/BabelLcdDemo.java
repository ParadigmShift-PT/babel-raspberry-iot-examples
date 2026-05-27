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

public class BabelLcdDemo extends GenericProtocol implements BabelDemo {

	private DeviceHandle lcdDevice;
	
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
	
	private int nextSentece;
	
	public BabelLcdDemo() {
		super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
		this.nextSentece = contents.length;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
		registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, 
				this::handleRegisterIoTDeviceReply);
		sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD, 
				"LCD"), I2COutputControlProtocol.PROTOCOL_ID);
	}

	private int getNextSentence() {
		this.nextSentece++;
		if(this.nextSentece >= contents.length)
			this.nextSentece = 0;
		return this.nextSentece;
	}
	
	
	public void handleDemoTimer(DemoTimer t, long time) {
		sendRequest(new ShowTextRequest(lcdDevice, contents[this.getNextSentence()]), I2COutputControlProtocol.PROTOCOL_ID);
	}
	
	public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep, short protocolId) {
		System.err.println("Received RegisterIoTDeviceReply. Success: " + rep.isSuccessful());
		if(rep.isSuccessful()) {
			this.lcdDevice = rep.getDeviceHandle();
			//Safety verification
			if(!this.lcdDevice.getDeviceAlias().equals("LCD")) {
				System.err.println("Incorrect answer received, expected alias 'LCD' received '" + this.lcdDevice.getDeviceAlias() + "'");
				System.exit(1);
			}
			
			sendRequest(new ShowTextRequest(lcdDevice, contents[this.getNextSentence()]), I2COutputControlProtocol.PROTOCOL_ID);
			
			setupPeriodicTimer(new DemoTimer(), 10000, 1500); //1,5 second timer
				
		} else {
			System.err.println("Failed to register LCD Device: " + rep.getErrorMessage());
			System.exit(1);
		}
	}
	
	@Override
	public void execute() throws Exception {
		Babel b = Babel.getInstance();
		
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");
		
		BabelLcdDemo lcd = this;

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
