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

/**
 * Demo that reads the Grove 3-axis accelerometer and mirrors every reading onto
 * the Grove LCD, illustrating how a Babel application combines an <em>input</em>
 * and an <em>output</em> IoT-control protocol in one app.
 *
 * <p>Two devices, two protocols:
 * <ul>
 *   <li>the accelerometer is read through {@link I2CInputControlProtocol}
 *       (id 2100), which here answers point-in-time {@code GetAccelerometerDataRequest}s
 *       with an {@link AccelerometerInputReply};</li>
 *   <li>the LCD is driven through {@link I2COutputControlProtocol} (id 2000) via
 *       {@link ShowTextRequest}.</li>
 * </ul>
 * Both are I²C peripherals, so {@link RegisterIoTDeviceRequest} carries no GPIO
 * line — just a device type and an alias.
 *
 * <p>The recurring IoT pattern this demo teaches is the request/reply
 * <b>polling</b> flow: the app periodically asks the input protocol for a fresh
 * measurement and renders the reply, rather than waiting on a pushed
 * notification. (For the push/notification style instead, see the gesture
 * demos, which subscribe to {@code GestureNotification}.)
 *
 * <p>The application never touches Pi4J or the I²C bus directly: it only sends
 * Babel requests and consumes Babel replies; the two control protocols do all
 * of the hardware work.
 *
 * <p>Run with the {@code Accel} command-line name (see {@code Main}).
 */
public class BabelAccelerometerLCDDemo
    extends GenericProtocol implements BabelDemo {

    /** Handle to the LCD once {@link I2COutputControlProtocol} has registered it. */
    private DeviceHandle lcdDevice;
    /** Handle to the accelerometer once {@link I2CInputControlProtocol} has registered it. */
    private DeviceHandle accelDevice;

    private String LCD_ALIAS = "LCD";
    private String ACCEL_ALIAS = "Accelerometer";

    /** Cycles 0,1,2 so each timer tick requests a different accelerometer reading mode. */
    private AtomicInteger round;

    /**
     * Wires up the protocol identity (name + id) that Babel uses to route events
     * to this protocol. Hardware registration happens later, in {@link #init}.
     */
    public BabelAccelerometerLCDDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    /**
     * Babel lifecycle hook: register handlers, then request the two devices.
     *
     * <p>This is the canonical IoT bootstrap. Handlers MUST be registered before
     * the requests that will eventually trigger them, otherwise the reply could
     * arrive with no handler installed:
     * <ol>
     *   <li>register the timer handler that will poll the accelerometer;</li>
     *   <li>register the {@link RegisterIoTDeviceReply} handler that brings back
     *       the {@link DeviceHandle} for each device;</li>
     *   <li>register the {@link AccelerometerInputReply} handler that carries the
     *       measurements;</li>
     *   <li>only then send the two {@link RegisterIoTDeviceRequest}s, each
     *       addressed to the control protocol that owns that device.</li>
     * </ol>
     * Both requests are fired up front; their replies are demultiplexed by device
     * type inside {@link #handleRegisterIoTDeviceReply}.
     */
    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        // (1) Handler for the periodic poll timer.
        registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
        // (2) Handler for the registration reply that delivers each DeviceHandle.
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);
        // (3) Handler for the accelerometer measurement replies.
        registerReplyHandler(AccelerometerInputReply.REPLY_ID,
                             this::handleAccelerometerInputReply);

        // (4) Ask the I²C output protocol (2000) for the LCD. I²C device, so no GPIO line.
        sendRequest(
            new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD, LCD_ALIAS),
            I2COutputControlProtocol.PROTOCOL_ID);

        // (4) Ask the I²C input protocol (2100) for the accelerometer.
        sendRequest(new RegisterIoTDeviceRequest(
                        DeviceType.GROVE_3AXIS_ACCELEROMETER, ACCEL_ALIAS),
                    I2CInputControlProtocol.PROTOCOL_ID);

        this.round = new AtomicInteger(0);
    }

    /**
     * Periodic timer handler: polls the accelerometer for one reading mode per
     * tick, round-robin. This is the request half of the request/reply polling
     * flow — each {@link GetAccelerometerDataRequest} is answered asynchronously
     * by {@link #handleAccelerometerInputReply}.
     */
    public void handleDemoTimer(DemoTimer t, long time) {
        int r = round.getAndIncrement(); // round robin way to fetch different
                                         // types of measuremnts
        if (r == 0) {
            // Full processed reading (g-force per axis, an AccelData object).
            sendRequest(
                new GetAccelerometerDataRequest(
                    accelDevice, InputType.Accelerometer.ACCELERATION_DATA),
                I2CInputControlProtocol.PROTOCOL_ID);

            return;
        } else if (r == 1) {
            // Simple acceleration as a float[] of per-axis values.
            sendRequest(
                new GetAccelerometerDataRequest(
                    accelDevice, InputType.Accelerometer.ACCELERATION_SIMPLE),
                I2CInputControlProtocol.PROTOCOL_ID);

            return;
        } else if (r == 2) {
            // Raw int[] XYZ sample straight from the sensor.
            sendRequest(new GetAccelerometerDataRequest(
                            accelDevice, InputType.Accelerometer.XYZ),
                        I2CInputControlProtocol.PROTOCOL_ID);
        }

        round.set(0); // wrap the cycle back to mode 0
    }

    /**
     * Reply handler for {@link RegisterIoTDeviceRequest}: receives the
     * {@link DeviceHandle} for whichever device the control protocol just bound.
     *
     * <p>Because both the LCD and the accelerometer share this one reply id, the
     * handler demultiplexes on {@link RegisterIoTDeviceReply#getDeviceType()} and
     * stashes each handle in the matching field. The polling timer is only armed
     * once the accelerometer (the device the timer reads) is ready.
     */
    public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep,
                                             short protocolId) {
        System.err.println("Received RegisterIoTDeviceReply. Success: " +
                           rep.isSuccessful());
        // Always check success before trusting the handle.
        if (rep.isSuccessful()) {
            if (rep.getDeviceType().equals(DeviceType.GROVE_LCD)) {
                // Keep the LCD handle; all future ShowTextRequests carry it.
                this.lcdDevice = rep.getDeviceHandle();
                // Safety verification
                if (!this.lcdDevice.getDeviceAlias().equals(LCD_ALIAS)) {
                    System.err.println("Incorrect answer received, expected "
                                       + "alias " + LCD_ALIAS + ", received '" +
                                       this.lcdDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                // Drive the LCD with a placeholder until the first sample lands.
                sendRequest(
                    new ShowTextRequest(lcdDevice, "waiting for accelerometer"),
                    I2COutputControlProtocol.PROTOCOL_ID);

            } else if (rep.getDeviceType().equals(
                           DeviceType.GROVE_3AXIS_ACCELEROMETER)) {
                // Keep the accelerometer handle; all GetAccelerometerDataRequests carry it.
                this.accelDevice = rep.getDeviceHandle();

                if (!this.accelDevice.getDeviceAlias().equals(ACCEL_ALIAS)) {
                    System.err.println("Incorrect answer received, expected "
                                       + "alias " + ACCEL_ALIAS +
                                       ", received '" +
                                       this.accelDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                // Accelerometer is ready: start polling it every 5s (first tick
                // after a 10s warm-up). Each tick drives handleDemoTimer.
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

    /**
     * Reply handler that completes the polling flow: each
     * {@link GetAccelerometerDataRequest} sent in {@link #handleDemoTimer} comes
     * back here as an {@link AccelerometerInputReply}. The reply's measurement
     * type matches the requested mode, so we cast accordingly, log it, and
     * forward a rendered string to the LCD with a {@link ShowTextRequest}. This
     * is the read-then-display half of the input/output pairing.
     */
    @SuppressWarnings("rawtypes")
    public void handleAccelerometerInputReply(AccelerometerInputReply rep,
                                              short protocolId) {
        System.err.println("Received AccelerometerInputReply Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            // The reply echoes which reading mode it answers, so unpack the
            // payload with the matching type.
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

    /**
     * Application bootstrap (the entry point {@code Main} calls for the
     * {@code Accel} demo). Standard Babel start-up sequence:
     * <ol>
     *   <li>grab the {@link Babel} singleton and load configuration;</li>
     *   <li>instantiate the two control protocols this demo depends on — the
     *       I²C output protocol (LCD) and the I²C input protocol (accelerometer);</li>
     *   <li>register all three protocols (the two controllers plus this demo) with Babel;</li>
     *   <li>call {@code init} in dependency order — controllers before the demo,
     *       so they are ready to answer the demo's registration requests;</li>
     *   <li>{@code b.start()} hands control to the Babel event loop.</li>
     * </ol>
     */
    @Override
    public void execute() throws Exception {
        Babel b = Babel.getInstance();

		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelAccelerometerLCDDemo gDemo = this;

        // The control protocols that own the hardware; the demo only talks to them.
        I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
        I2CInputControlProtocol i2cin = new I2CInputControlProtocol();

        // Make all protocols known to Babel before any of them is initialised.
        b.registerProtocol(i2cout);
        b.registerProtocol(i2cin);
        b.registerProtocol(gDemo);

        // Init controllers first so they can service the demo's register requests.
        i2cout.init(props);
        i2cin.init(props);
        gDemo.init(props);

        System.out.println("Setup is complete.");

        // Start the Babel runtime — from here everything is event-driven.
        b.start();

        System.out.println("System is running.");
    }
}
