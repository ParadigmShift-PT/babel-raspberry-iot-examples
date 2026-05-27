package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.util.Properties;
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
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2CInputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2COutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.notifications.GestureNotification;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.replies.GestureInputReply;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.input.GetReactiveGestureRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ClearDisplayRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetDisplayColorRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowDisplayRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

public class BabelArrowGestureMatrixDemo
    extends GenericProtocol implements BabelDemo {

    private DeviceHandle matrixDevice;
    private DeviceHandle gestureDevice;

    private String MATRIX_ALIAS = "Matrix";
    private String GESTURE_ALIAS = "Gesture";

    private long lastActionTimestamp;
    
    public BabelArrowGestureMatrixDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
        this.lastActionTimestamp = 0;
    }

    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);

        registerReplyHandler(GestureInputReply.REPLY_ID,
                             this::handleGestureInputReply);

        subscribeNotification(GestureNotification.NOTIFICATION_ID,
                              this::handleGestureNotification);

        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX,
                                                 MATRIX_ALIAS),
                    I2COutputControlProtocol.PROTOCOL_ID);

        sendRequest(new RegisterIoTDeviceRequest(
                        DeviceType.GROVE_GESTURE_DETECTOR, GESTURE_ALIAS),
                    I2CInputControlProtocol.PROTOCOL_ID);
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
                sendRequest(new ClearDisplayRequest(matrixDevice), 
                		I2COutputControlProtocol.PROTOCOL_ID);
            } else if (rep.getDeviceType().equals(
                           DeviceType.GROVE_GESTURE_DETECTOR)) {
                this.gestureDevice = rep.getDeviceHandle();

                if (!this.gestureDevice.getDeviceAlias().equals(
                        GESTURE_ALIAS)) {
                    System.err.println(
                        "Incorrect answer received, expected "
                        + "alias " + GESTURE_ALIAS + ", received '" +
                        this.gestureDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                Set<PAJ7620GestureType> gestures =
                    Set.of(PAJ7620GestureType.UP, PAJ7620GestureType.DOWN,
                           PAJ7620GestureType.LEFT, PAJ7620GestureType.RIGHT);

                Threshold<PAJ7620GestureType> t = Threshold.any(gestures);
                sendRequest(new GetReactiveGestureRequest(gestureDevice, t),
                            I2CInputControlProtocol.PROTOCOL_ID);
            }

        } else {
            System.err.println("Failed to register device " +
                               rep.getDeviceAlias() + ": " +
                               rep.getErrorMessage());
            System.exit(1);
        }
    }

    public void handleGestureInputReply(GestureInputReply rep,
                                        short protocolId) {
        System.err.println("Received GestureInputReply Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            System.out.println("Got gesture");
            sendRequest(new SetDisplayColorRequest(matrixDevice, 0, 0, 255),
                        I2COutputControlProtocol.PROTOCOL_ID);
            
            this.lastActionTimestamp = System.currentTimeMillis();
            setupTimer(new DemoTimer(this.lastActionTimestamp), 2000);

        } else {
            System.err.println("Failed to receive gesture info: " +
                               rep.getErrorMessage());
        }
    }

    public void handleDemoTimer(DemoTimer t, long time) {
        if(this.lastActionTimestamp == t.getTimestamp()) {
        	sendRequest(new ClearDisplayRequest(matrixDevice), 
                          I2COutputControlProtocol.PROTOCOL_ID);
        }
    }

    private void handleGestureNotification(GestureNotification not,
                                           short protocolId) {
        switch (not.getValue()) {
        case UP:
            sendRequest(new ShowDisplayRequest(matrixDevice, 
            		LedMatrixUtils.encodeArrow(Arrow.ARROW_UP)), 
            		I2COutputControlProtocol.PROTOCOL_ID);
            break;
        case DOWN:
        	sendRequest(new ShowDisplayRequest(matrixDevice, 
            		LedMatrixUtils.encodeArrow(Arrow.ARROW_DOWN)), 
            		I2COutputControlProtocol.PROTOCOL_ID);
            break;
        case LEFT:
        	sendRequest(new ShowDisplayRequest(matrixDevice, 
            		LedMatrixUtils.encodeArrow(Arrow.ARROW_LEFT)), 
            		I2COutputControlProtocol.PROTOCOL_ID);
            break;
        case RIGHT:
        	sendRequest(new ShowDisplayRequest(matrixDevice, 
            		LedMatrixUtils.encodeArrow(Arrow.ARROW_RIGTH)), 
            		I2COutputControlProtocol.PROTOCOL_ID);
            break;
        default:
            return;
        }
        this.lastActionTimestamp = System.currentTimeMillis();
        setupTimer(new DemoTimer(this.lastActionTimestamp), 
        		I2COutputControlProtocol.PROTOCOL_ID);
    }

    @Override
    public void execute() throws Exception {
        Babel b = Babel.getInstance();
        
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelArrowGestureMatrixDemo gDemo = this;

        I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
        I2CInputControlProtocol i2cin = new I2CInputControlProtocol();

        b.registerProtocol(i2cout);
        b.registerProtocol(i2cin);
        b.registerProtocol(gDemo);

        i2cout.init(props);
        i2cin.init(props);
        gDemo.init(props);

        System.out.println("Setup is complete.");

        b.start();

        System.out.println("System is running.");
    }
}
