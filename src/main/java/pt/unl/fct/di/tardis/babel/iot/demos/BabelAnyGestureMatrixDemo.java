package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.util.Properties;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.iot.device.i2c.GroveLedMatrix;
import pt.unl.fct.di.novasys.iot.device.i2c.GroveGestureDetector.PAJ7620GestureType;
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
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetDisplayColorRequest;
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.ShowEmojiRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Reactive Babel demo: shows an emoji on the Grove RGB LED matrix for <em>any</em>
 * gesture the detector reports.
 *
 * <p>This is a textbook example of <strong>reactive input driving output</strong>:
 * one input device asynchronously notifies the application, which then commands an
 * output device. It wires together two of the IoT control protocols:</p>
 * <ul>
 *   <li>{@link I2CInputControlProtocol} (id {@code 2100}) reads the Grove gesture
 *       detector and, once asked for "reactive" gestures, emits a
 *       {@link GestureNotification} every time a hand movement is recognised.</li>
 *   <li>{@link I2COutputControlProtocol} (id {@code 2000}) drives the Grove RGB LED
 *       matrix; the demo sends it a {@link ShowEmojiRequest} carrying the matrix's
 *       {@link DeviceHandle} whenever a gesture arrives.</li>
 * </ul>
 *
 * <p>The demo itself is a {@link GenericProtocol}: it never touches Pi4J, I²C or any
 * GPIO line directly. It only exchanges Babel requests/replies/notifications with the
 * control protocols, which own the hardware. This is the same "Pattern 1: driving a
 * Grove device" flow documented in the module {@code README.md}, plus a notification
 * subscription for the reactive input.</p>
 *
 * <p>Run it with the command-line name {@code anyGesture} (see {@code Main.java}):
 * <pre>java -jar babel-raspberry-iot-examples.jar anyGesture</pre></p>
 *
 * @see BabelLcdDemo the fully-commented output-only exemplar in this package
 */
public class BabelAnyGestureMatrixDemo
        extends GenericProtocol implements BabelDemo {

    // Handles returned by the control protocols when each device is registered.
    // They are the demo's only reference to the hardware: every request that acts
    // on a device carries the matching handle so the control protocol knows which
    // physical device to drive.
    private DeviceHandle matrixDevice;
    private DeviceHandle gestureDevice;

    // Human-readable aliases chosen by this demo. They are echoed back in the
    // registration reply, which lets us tell the two devices apart and sanity-check
    // that we received the handle we expected.
    private String MATRIX_ALIAS = "Matrix";
    private String GESTURE_ALIAS = "Gesture";

    /**
     * Builds the demo protocol. Like every Babel {@link GenericProtocol}, it must
     * declare a protocol name and a globally unique protocol id (here the shared
     * {@code BabelDemo} constants); Babel uses the id to route events to this
     * instance.
     */
    public BabelAnyGestureMatrixDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    /**
     * Babel lifecycle hook: register every handler this protocol needs, then kick off
     * the work. The golden rule is to <strong>register handlers BEFORE sending the
     * request that triggers them</strong>, otherwise the reply/notification could
     * arrive before there is anyone to receive it.
     */
    @Override
    public void init(Properties props)
            throws HandlerRegistrationException, IOException {
        // Timer handler: a periodic "heartbeat" the demo uses to repaint the matrix
        // green (see handleDemoTimer). Registered up front like any other handler.
        registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
        // Reply handler for device registration: this is how each DeviceHandle comes
        // back to us after a RegisterIoTDeviceRequest.
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                this::handleRegisterIoTDeviceReply);

        // Reply handler for the reactive-gesture subscription request.
        registerReplyHandler(GestureInputReply.REPLY_ID,
                this::handleGestureInputReply);

        // The reactive twist: subscribe to GestureNotification so the input protocol
        // can PUSH us a notification asynchronously every time it recognises a gesture.
        // Without this subscription the detector's events would have nowhere to go.
        subscribeNotification(GestureNotification.NOTIFICATION_ID,
                this::handleGestureNotification);

        // Now that the handlers exist, ask each control protocol for a device.
        // (1) Ask the I²C OUTPUT protocol (2000) for the LED matrix...
        sendRequest(
                new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX, MATRIX_ALIAS),
                I2COutputControlProtocol.PROTOCOL_ID);

        // (2) ...and ask the I²C INPUT protocol (2100) for the gesture detector.
        // The matching RegisterIoTDeviceReply for each will arrive in
        // handleRegisterIoTDeviceReply, where we keep the handles.
        sendRequest(new RegisterIoTDeviceRequest(
                DeviceType.GROVE_GESTURE_DETECTOR, GESTURE_ALIAS),
                I2CInputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Reply handler for {@link RegisterIoTDeviceReply}. Babel calls it once for each
     * {@code RegisterIoTDeviceRequest} we sent — here twice, one per device. The
     * canonical pattern is: check {@link RegisterIoTDeviceReply#isSuccessful()}, and on
     * success keep the {@link DeviceHandle}. Because both registrations share this one
     * handler, we branch on the reply's device type to store the right handle.
     */
    public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep,
            short protocolId) {
        System.err.println("Received RegisterIoTDeviceReply. Success: " +
                rep.isSuccessful());
        if (rep.isSuccessful()) {
            if (rep.getDeviceType().equals(DeviceType.GROVE_LED_MATRIX)) {
                // The matrix (our output device): keep its handle for later requests.
                this.matrixDevice = rep.getDeviceHandle();
                // Safety verification: confirm the protocol handed back the alias we
                // asked for, so we never drive the wrong device.
                if (!this.matrixDevice.getDeviceAlias().equals(MATRIX_ALIAS)) {
                    System.err.println("Incorrect answer received, expected "
                            + "alias " + MATRIX_ALIAS + ", received '" +
                            this.matrixDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

            } else if (rep.getDeviceType().equals(
                    DeviceType.GROVE_GESTURE_DETECTOR)) {
                // The gesture detector (our input device): keep its handle too.
                this.gestureDevice = rep.getDeviceHandle();

                if (!this.gestureDevice.getDeviceAlias().equals(GESTURE_ALIAS)) {
                    System.err.println("Incorrect answer received, expected "
                            + "alias " + GESTURE_ALIAS +
                            ", received '" +
                            this.gestureDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                // Now that we hold the gesture handle, arm the reactive input. A
                // GetReactiveGestureRequest asks the input protocol to PUSH a
                // GestureNotification whenever a detected gesture matches the given
                // Threshold. Threshold.none() means "no filter": every gesture the
                // detector reports is pushed to us — hence "any gesture".
				Threshold<PAJ7620GestureType> t = Threshold.none();
                sendRequest(new GetReactiveGestureRequest(gestureDevice, t),
                        I2CInputControlProtocol.PROTOCOL_ID);

                // A periodic timer that resets the matrix to green between gestures
                // (fires every 5 s after a 10 s initial delay); see handleDemoTimer.
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
     * Reply handler for the {@link GetReactiveGestureRequest} we sent to arm the
     * detector. It confirms the input protocol accepted the reactive subscription; on
     * success the demo paints the matrix blue as a visible "ready" acknowledgement.
     * The actual per-gesture reaction happens later in {@link #handleGestureNotification}.
     */
    public void handleGestureInputReply(GestureInputReply rep,
            short protocolId) {
        System.err.println("Received GestureInputReply Success: " +
                rep.isSuccessful());
        if (rep.isSuccessful()) {
            System.out.println("Got gesture");
            // Drive the matrix: SetDisplayColorRequest carries the matrix handle, so
            // the OUTPUT protocol (2000) knows which device to colour. (0,0,255)=blue.
            sendRequest(new SetDisplayColorRequest(matrixDevice, 0, 0, 255),
                    I2COutputControlProtocol.PROTOCOL_ID);

        } else {
            System.err.println("Failed to receive gesture info: " +
                    rep.getErrorMessage());
        }
    }

    /**
     * Timer handler driven by the periodic {@link DemoTimer} armed above. Each tick
     * repaints the matrix green, giving the display a steady "idle" colour in between
     * gesture-triggered emojis. Shows how a Babel protocol mixes timer-driven and
     * event-driven work in the same single-threaded handler model.
     */
    public void handleDemoTimer(DemoTimer t, long time) {
        sendRequest(new SetDisplayColorRequest(matrixDevice, 0, 255, 0),
                I2COutputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Notification handler — the reactive heart of the demo. Babel calls it
     * <strong>asynchronously</strong> every time the gesture detector reports a
     * movement (because we subscribed to {@link GestureNotification} in {@code init}).
     * The demo reacts by mapping the detected gesture to an emoji and driving the LED
     * matrix with a {@link ShowEmojiRequest} carrying the matrix handle — input event
     * in, output command out, with the demo touching no hardware itself.
     */
    private void handleGestureNotification(GestureNotification not, short protocolId) {
        // Pick an emoji for whatever gesture was reported (any gesture maps to one).
        GroveLedMatrix.Emoji emj = null;
        switch (not.getValue()) {
            case CLOCKWISE:
                emj = GroveLedMatrix.Emoji.Cat;
                break;
            case UP:
                emj = GroveLedMatrix.Emoji.Duck;
                break;
            case DOWN:
                emj = GroveLedMatrix.Emoji.Mad;
                break;
            case RIGHT:
                emj = GroveLedMatrix.Emoji.Sad;
                break;
            case LEFT:
                emj = GroveLedMatrix.Emoji.Flame;
                break;
            case PUSH:
                emj = GroveLedMatrix.Emoji.Crab;
                break;
            case PULL:
                emj = GroveLedMatrix.Emoji.CrystalSword;
                break;
            case COUNTER_CLOCKWISE:
                emj = GroveLedMatrix.Emoji.Creeper;
                break;
            default:
                emj = GroveLedMatrix.Emoji.Umbrella;
                break;
        }
        // Send the chosen emoji to the OUTPUT protocol; the handle routes it to the
        // matrix. This is the same send-a-typed-request-carrying-the-handle step as
        // every other output action in the demo.
        sendRequest(new ShowEmojiRequest(matrixDevice, emj),
                I2COutputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Application entry point (invoked by {@code Main} for the {@code anyGesture} demo).
     * It performs the standard Babel bootstrap:
     * <ol>
     *   <li>get the singleton {@link Babel} instance;</li>
     *   <li>load configuration from {@code paradigmshift.config};</li>
     *   <li>instantiate the control protocols this demo depends on (I²C output 2000 +
     *       I²C input 2100) and the demo protocol itself;</li>
     *   <li>{@code registerProtocol} each one with Babel;</li>
     *   <li>{@code init} them in dependency order — the control protocols first, then
     *       the demo, so the targets of the demo's first requests already exist;</li>
     *   <li>{@code start} Babel, which spins up the event loops and begins delivering
     *       events to the registered handlers.</li>
     * </ol>
     * Note the demo wires hardware access entirely through these protocols: it never
     * constructs a Pi4J context or opens an I²C bus itself.
     */
    @Override
    public void execute() throws Exception {
        // (1) Babel is a process-wide singleton.
        Babel b = Babel.getInstance();

        // (2) Load runtime configuration (channels, GPIO/I²C settings, etc.).
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelAnyGestureMatrixDemo gDemo = this;

        // (3) Create the control protocols that own the hardware, plus this demo.
        I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
        I2CInputControlProtocol i2cin = new I2CInputControlProtocol();

        // (4) Register every protocol with Babel so it can route events between them.
        b.registerProtocol(i2cout);
        b.registerProtocol(i2cin);
        b.registerProtocol(gDemo);

        // (5) Initialise in dependency order: the device-owning control protocols
        // before the demo, so the demo's registration requests have someone to answer.
        i2cout.init(props);
        i2cin.init(props);
        gDemo.init(props);

        System.out.println("Setup is complete.");

        // (6) Start Babel: event loops run and handlers begin firing.
        b.start();

        System.out.println("System is running.");
    }
}
