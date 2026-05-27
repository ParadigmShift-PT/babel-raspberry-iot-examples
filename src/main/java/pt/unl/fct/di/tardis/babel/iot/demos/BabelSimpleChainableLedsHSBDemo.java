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
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.DigitalOutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetChainableLEDColorHSBRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

public class BabelSimpleChainableLedsHSBDemo
    extends GenericProtocol implements BabelDemo {

    public static final String LED_PORT = "led.line";
    public static final String LED_PORT_DEFAULT = "26";

    private DeviceHandle chainableLeds;

    public final static String ledAlias = "leds";
    private int deviceLine;
    float lastColor;

    private final int steps = 100;
    private int i = 0;

    private int numberOfLeds;

    public BabelSimpleChainableLedsHSBDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);

        if (props.containsKey(DigitalOutputControlProtocol.RGB_LED_COUNT))
            this.numberOfLeds = Integer.parseInt(
                props.getProperty(DigitalOutputControlProtocol.RGB_LED_COUNT));
        else
            this.numberOfLeds = 1;

        System.err.println("Number of leds in chain: " + this.numberOfLeds);

        this.deviceLine =
            Integer.parseInt(props.getProperty(LED_PORT, LED_PORT_DEFAULT));

        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_CHAINABLE_RGB,
                                                 ledAlias, this.deviceLine),
                    DigitalOutputControlProtocol.PROTOCOL_ID);
    }

    public void handleDemoTimer(DemoTimer t, long time) { updateLedsColors(); }

    public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep,
                                             short protocolId) {
        System.err.println("Received RegisterIoTDeviceReply. Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            this.chainableLeds = rep.getDeviceHandle();
            // Safety verification
            if (!this.chainableLeds.getDeviceAlias().equals(ledAlias)) {
                System.err.println(
                    "Incorrect answer received, expected alias '" + ledAlias +
                    "' received '" + this.chainableLeds.getDeviceAlias() +
                    "'");
                System.exit(1);
            }

            updateLedsColors();

            setupPeriodicTimer(new DemoTimer(), 50, 50); // 50 Milliseconds wait

        } else {
            System.err.println("Failed to register ChainableLed Device: " +
                               rep.getErrorMessage());
            System.exit(1);
        }
    }

    private void updateLedsColors() {
        if (i == steps) {
            i = 0; // so it never overflows
        }

        float ratio = (float)(i % steps) / steps;

        float hue = ratio;
        float saturation = 0.7f + 0.3f * (float)Math.sin(2 * Math.PI * ratio);
        float brightness =
            0.7f + 0.3f * (float)Math.sin(2 * Math.PI * ratio * 2);

        for (byte j = 0; j < numberOfLeds; j++) {

            sendRequest(new SetChainableLEDColorHSBRequest(
                            this.chainableLeds, j, hue, saturation, brightness),
                        DigitalOutputControlProtocol.PROTOCOL_ID);
        }

        i++;
    }

    @Override
    public void execute() throws Exception {
        Babel b = Babel.getInstance();

        Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelSimpleChainableLedsHSBDemo demo = this;

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
