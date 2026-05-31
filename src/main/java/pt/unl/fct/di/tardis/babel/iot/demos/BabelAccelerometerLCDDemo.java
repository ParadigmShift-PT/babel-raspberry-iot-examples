package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.iot.device.i2c.Grove3AxisAccelerometer.AccelData;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceHandle;
import pt.unl.fct.di.tardis.babel.iot.api.DeviceType;
import pt.unl.fct.di.tardis.babel.iot.api.InputType;
import pt.unl.fct.di.tardis.babel.iot.api.replies.RegisterIoTDeviceReply;
import pt.unl.fct.di.tardis.babel.iot.api.requests.RegisterIoTDeviceRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2CInputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.I2COutputControlProtocol;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.replies.AccelerometerInputReply;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.input.GetAccelerometerDataRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowTextRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

public class BabelAccelerometerLCDDemo
    extends GenericProtocol implements BabelDemo {

    private DeviceHandle lcdDevice;
    private DeviceHandle accelDevice;

    private String LCD_ALIAS = "LCD";
    private String ACCEL_ALIAS = "Accelerometer";

    private AtomicInteger round;

    public BabelAccelerometerLCDDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);
        registerReplyHandler(AccelerometerInputReply.REPLY_ID,
                             this::handleAccelerometerInputReply);

        sendRequest(
            new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD, LCD_ALIAS),
            I2COutputControlProtocol.PROTOCOL_ID);

        sendRequest(new RegisterIoTDeviceRequest(
                        DeviceType.GROVE_3AXIS_ACCELEROMETER, ACCEL_ALIAS),
                    I2CInputControlProtocol.PROTOCOL_ID);

        this.round = new AtomicInteger(0);
    }

    public void handleDemoTimer(DemoTimer t, long time) {
        int r = round.getAndIncrement(); // round robin way to fetch different
                                         // types of measuremnts
        if (r == 0) {
            sendRequest(
                new GetAccelerometerDataRequest(
                    accelDevice, InputType.Accelerometer.ACCELERATION_DATA),
                I2CInputControlProtocol.PROTOCOL_ID);

            return;
        } else if (r == 1) {
            sendRequest(
                new GetAccelerometerDataRequest(
                    accelDevice, InputType.Accelerometer.ACCELERATION_SIMPLE),
                I2CInputControlProtocol.PROTOCOL_ID);

            return;
        } else if (r == 2) {
            sendRequest(new GetAccelerometerDataRequest(
                            accelDevice, InputType.Accelerometer.XYZ),
                        I2CInputControlProtocol.PROTOCOL_ID);
        }

        round.set(0);
    }

    public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep,
                                             short protocolId) {
        System.err.println("Received RegisterIoTDeviceReply. Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            if (rep.getDeviceType().equals(DeviceType.GROVE_LCD)) {
                this.lcdDevice = rep.getDeviceHandle();
                // Safety verification
                if (!this.lcdDevice.getDeviceAlias().equals(LCD_ALIAS)) {
                    System.err.println("Incorrect answer received, expected "
                                       + "alias " + LCD_ALIAS + ", received '" +
                                       this.lcdDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                sendRequest(
                    new ShowTextRequest(lcdDevice, "waiting for accelerometer"),
                    I2COutputControlProtocol.PROTOCOL_ID);

            } else if (rep.getDeviceType().equals(
                           DeviceType.GROVE_3AXIS_ACCELEROMETER)) {
                this.accelDevice = rep.getDeviceHandle();

                if (!this.accelDevice.getDeviceAlias().equals(ACCEL_ALIAS)) {
                    System.err.println("Incorrect answer received, expected "
                                       + "alias " + ACCEL_ALIAS +
                                       ", received '" +
                                       this.accelDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                setupPeriodicTimer(new DemoTimer(), 10000,
                                   5000); // 5 second timer
            }

        } else {
            System.err.println("Failed to register device " +
                               rep.getDeviceAlias() + ": " +
                               rep.getErrorMessage());
            System.exit(1);
        }
    }

    @SuppressWarnings("rawtypes")
    public void handleAccelerometerInputReply(AccelerometerInputReply rep,
                                              short protocolId) {
        System.err.println("Received AccelerometerInputReply Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            switch (rep.getInputType()) {
            case ACCELERATION_DATA:
                AccelData ad = (AccelData)rep.getMeasurement();
                System.out.println(ad);
                sendRequest(new ShowTextRequest(lcdDevice, ad.toString()),
                            I2COutputControlProtocol.PROTOCOL_ID);
                break;
            case ACCELERATION_SIMPLE:
                float[] as = (float[])rep.getMeasurement();
                String as_str = Arrays.toString(as);
                System.out.println(as_str);
                sendRequest(new ShowTextRequest(
                                lcdDevice,
                                as_str), // this might not display well but alas
                            I2COutputControlProtocol.PROTOCOL_ID);
                break;
            case XYZ:
                int[] xyz = (int[])rep.getMeasurement();
                String xyz_str = Arrays.toString(xyz);
                System.out.println(xyz_str);
                sendRequest(new ShowTextRequest(lcdDevice, xyz_str),
                            I2COutputControlProtocol.PROTOCOL_ID);
                break;
            default:
                System.err.println(
                    "Invalid measurement type for accelerometer" +
                    rep.getErrorMessage());
                break;
            }
        } else {
            System.err.println("Failed to receive accelerometer info: " +
                               rep.getErrorMessage());
            System.exit(1);
        }
    }

    @Override
    public void execute() throws Exception {
        Babel b = Babel.getInstance();
        
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelAccelerometerLCDDemo gDemo = this;

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
