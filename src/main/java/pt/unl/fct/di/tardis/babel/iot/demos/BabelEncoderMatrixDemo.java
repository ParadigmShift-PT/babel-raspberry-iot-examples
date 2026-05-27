package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.iot.device.digital.GroveEncoder.Rotation;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceHandle;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceType;
import pt.unl.fct.di.tardis.babel.iot.api.Threshold;
import pt.unl.fct.di.tardis.babel.iot.api.replies.RegisterIoTDeviceReply;
import pt.unl.fct.di.tardis.babel.iot.api.requests.RegisterIoTDeviceRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.DigitalInputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2COutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.notifications.EncoderNotification;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.input.GetReactiveEncoderRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.DisplayBarRequest;

public class BabelEncoderMatrixDemo
    extends GenericProtocol implements BabelDemo {

    public static final String ENCODER_PORT = "encoder.line";
    public static final String ENCODER_PORT_DEFAULT = "5";

    private DeviceHandle matrixDevice;
    private DeviceHandle encoderDevice;

    private String MATRIX_ALIAS = "Matrix";
    private String ENCODER_ALIAS = "Encoder";

    private AtomicInteger level;
    private int deviceLine;

    public BabelEncoderMatrixDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        this.level = new AtomicInteger(0);

        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);

        subscribeNotification(EncoderNotification.NOTIFICATION_ID,
                              this::handleEncoderNotification);

        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX,
                                                 MATRIX_ALIAS),
                    I2COutputControlProtocol.PROTOCOL_ID);

        this.deviceLine = Integer.parseInt(
            props.getProperty(ENCODER_PORT, ENCODER_PORT_DEFAULT));

        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_ENCODER,
                                                 ENCODER_ALIAS,
                                                 this.deviceLine),
                    DigitalInputControlProtocol.PROTOCOL_ID);
    }

    public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep,
                                             short protocolId) {
        System.err.println("Received RegisterIoTDeviceReply. Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            if (rep.getDeviceType().equals(DeviceType.GROVE_LED_MATRIX)) {
                this.matrixDevice = rep.getDeviceHandle();
                // Safety verification
                if (!this.matrixDevice.getDeviceAlias().equals(MATRIX_ALIAS)) {
                    System.err.println(
                        "Incorrect answer received, expected "
                        + "alias " + MATRIX_ALIAS + ", received '" +
                        this.matrixDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

            } else if (rep.getDeviceType().equals(DeviceType.GROVE_ENCODER)) {
                this.encoderDevice = rep.getDeviceHandle();

                if (!this.encoderDevice.getDeviceAlias().equals(
                        ENCODER_ALIAS)) {
                    System.err.println(
                        "Incorrect answer received, expected "
                        + "alias " + ENCODER_ALIAS + ", received '" +
                        this.encoderDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                Threshold<Rotation> t = Threshold.none();
                sendRequest(new GetReactiveEncoderRequest(encoderDevice, t),
                            DigitalInputControlProtocol.PROTOCOL_ID);
            }

        } else {
            System.err.println("Failed to register device " +
                               rep.getDeviceAlias() + ": " +
                               rep.getErrorMessage());
            System.exit(1);
        }
    }

    private void handleEncoderNotification(EncoderNotification not,
                                           short protocolId) {
        int lev = 0;
        switch (not.getValue()) {
        case CLOCKWISE:
            lev = this.level.incrementAndGet();
            break;
        case COUNTER_CLOCKWISE:
            lev = this.level.decrementAndGet();
            break;
        default:
            lev = this.level.get();
            break;
        }
        sendRequest(new DisplayBarRequest(matrixDevice, lev),
                    I2COutputControlProtocol.PROTOCOL_ID);
    }

    @Override
    public void execute() throws Exception {
        Babel b = Babel.getInstance();

        Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelEncoderMatrixDemo gDemo = this;

        I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
        DigitalInputControlProtocol digin = new DigitalInputControlProtocol();

        b.registerProtocol(i2cout);
        b.registerProtocol(digin);
        b.registerProtocol(gDemo);

        i2cout.init(props);
        digin.init(props);
        gDemo.init(props);

        System.out.println("Setup is complete.");

        b.start();

        System.out.println("System is running.");
    }
}
