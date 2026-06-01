package pt.unl.fct.di.tardis.babel.iot.demos;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
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
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Reactive Babel demo: colours the whole Grove RGB LED matrix according to the
 * <em>cardinal</em> gesture detected (UP/DOWN/LEFT/RIGHT each map to a colour).
 *
 * <p>It is the simplest of the three gesture demos and a clean example of
 * <strong>reactive input driving output</strong>, wiring two IoT control protocols:</p>
 * <ul>
 *   <li>{@link I2CInputControlProtocol} (id {@code 2100}) reads the Grove gesture
 *       detector and emits a {@link GestureNotification} for each recognised gesture
 *       (filtered here to the four cardinal directions);</li>
 *   <li>{@link I2COutputControlProtocol} (id {@code 2000}) drives the Grove RGB LED
 *       matrix; the demo sends it a {@link SetDisplayColorRequest} carrying the
 *       matrix's {@link DeviceHandle} and the colour for the detected direction.</li>
 * </ul>
 *
 * <p>The demo is a {@link GenericProtocol} that never touches Pi4J, I²C or any GPIO
 * line directly — it only exchanges Babel requests/replies/notifications with the
 * control protocols, which own the hardware. This is "Pattern 1: driving a Grove
 * device" from the module {@code README.md}, with a notification subscription added for
 * the reactive input.</p>
 *
 * <p>Run it with the command-line name {@code cardinalGesture} (see {@code Main.java}):
 * <pre>java -jar babel-raspberry-iot-examples.jar cardinalGesture</pre></p>
 *
 * @see BabelLcdDemo the fully-commented output-only exemplar in this package
 */
public class BabelCardinalGestureMatrixDemo
    extends GenericProtocol implements BabelDemo {

    // Handles returned by the control protocols on registration — the demo's only
    // reference to the hardware. Every device-acting request carries the matching
    // handle so the control protocol knows which physical device to drive.
    private DeviceHandle matrixDevice;
    private DeviceHandle gestureDevice;

    // Aliases this demo assigns to its two devices; echoed back in the registration
    // reply so we can match each handle to the right device and sanity-check it.
    private String MATRIX_ALIAS = "Matrix";
    private String GESTURE_ALIAS = "Gesture";

    /**
     * Builds the demo protocol with the shared {@code BabelDemo} name and id; Babel
     * uses the id to route events to this instance.
     */
    public BabelCardinalGestureMatrixDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    /**
     * Babel lifecycle hook: register all handlers, then send the requests that trigger
     * them. Handlers are registered <strong>before</strong> the requests so no reply or
     * notification can arrive before there is a handler to receive it.
     */
    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        // Timer handler: the periodic "idle" repaint (see handleDemoTimer).
        registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
        // Reply handler that delivers each device's DeviceHandle on registration.
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);

        // Reply handler acknowledging the reactive-gesture subscription request.
        registerReplyHandler(GestureInputReply.REPLY_ID,
                             this::handleGestureInputReply);

        // The reactive twist: subscribe to GestureNotification so the input protocol
        // PUSHES us an event asynchronously each time it recognises a matching gesture.
        subscribeNotification(GestureNotification.NOTIFICATION_ID,
                              this::handleGestureNotification);

        // Handlers ready — now request the two devices.
        // (1) The LED matrix from the I²C OUTPUT protocol (2000)...
        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX,
                                                 MATRIX_ALIAS),
                    I2COutputControlProtocol.PROTOCOL_ID);

        // (2) ...and the gesture detector from the I²C INPUT protocol (2100). Each
        // RegisterIoTDeviceReply arrives in handleRegisterIoTDeviceReply.
        sendRequest(new RegisterIoTDeviceRequest(
                        DeviceType.GROVE_GESTURE_DETECTOR, GESTURE_ALIAS),
                    I2CInputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Reply handler for {@link RegisterIoTDeviceReply}, invoked once per registration
     * (here twice). The canonical pattern: check
     * {@link RegisterIoTDeviceReply#isSuccessful()} and on success keep the
     * {@link DeviceHandle}; branch on the reply's device type since both registrations
     * share this one handler.
     */
    public void handleRegisterIoTDeviceReply(RegisterIoTDeviceReply rep,
                                             short protocolId) {
        System.err.println("Received RegisterIoTDeviceReply. Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            if (rep.getDeviceType().equals(DeviceType.GROVE_LED_MATRIX)) {
                // The matrix (output device): keep its handle for later requests.
                this.matrixDevice = rep.getDeviceHandle();
                // Safety verification: ensure we got the alias we asked for.
                if (!this.matrixDevice.getDeviceAlias().equals(MATRIX_ALIAS)) {
                    System.err.println(
                        "Incorrect answer received, expected "
                        + "alias " + MATRIX_ALIAS + ", received '" +
                        this.matrixDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

            } else if (rep.getDeviceType().equals(
                           DeviceType.GROVE_GESTURE_DETECTOR)) {
                // The gesture detector (input device): keep its handle too.
                this.gestureDevice = rep.getDeviceHandle();

                if (!this.gestureDevice.getDeviceAlias().equals(
                        GESTURE_ALIAS)) {
                    System.err.println(
                        "Incorrect answer received, expected "
                        + "alias " + GESTURE_ALIAS + ", received '" +
                        this.gestureDevice.getDeviceAlias() + "'");
                    System.exit(1);
                }

                // Arm the reactive input, restricted to the four cardinal directions.
                Set<PAJ7620GestureType> gestures =
                    Set.of(PAJ7620GestureType.UP, PAJ7620GestureType.DOWN,
                           PAJ7620GestureType.LEFT, PAJ7620GestureType.RIGHT);

                // Threshold.any(gestures) asks the input protocol to PUSH a
                // GestureNotification only when a detected gesture is one of those
                // directions; other gestures are filtered out at the source.
                Threshold<PAJ7620GestureType> t = Threshold.any(gestures);
                sendRequest(new GetReactiveGestureRequest(gestureDevice, t),
                            I2CInputControlProtocol.PROTOCOL_ID);

                // A periodic timer that repaints the matrix green between gestures
                // (every 5 s after a 10 s initial delay); see handleDemoTimer.
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
     * Reply handler for the {@link GetReactiveGestureRequest}. It confirms the input
     * protocol accepted the reactive subscription; on success the demo paints the
     * matrix blue as a visible "ready" acknowledgement. The per-gesture colouring
     * happens later in {@link #handleGestureNotification}.
     */
    public void handleGestureInputReply(GestureInputReply rep,
                                        short protocolId) {
        System.err.println("Received GestureInputReply Success: " +
                           rep.isSuccessful());
        if (rep.isSuccessful()) {
            System.out.println("Got gesture");
            // Drive the matrix blue (0,0,255) via the OUTPUT protocol; the handle
            // routes the request to the matrix.
            sendRequest(new SetDisplayColorRequest(matrixDevice, 0, 0, 255),
                        I2COutputControlProtocol.PROTOCOL_ID);

        } else {
            System.err.println("Failed to receive gesture info: " +
                               rep.getErrorMessage());
        }
    }

    /**
     * Timer handler driven by the periodic {@link DemoTimer}. Each tick repaints the
     * matrix green, giving a steady "idle" colour in between gesture-triggered colours.
     * Illustrates a Babel protocol mixing timer-driven and event-driven work in one
     * single-threaded handler model.
     */
    public void handleDemoTimer(DemoTimer t, long time) {
        sendRequest(new SetDisplayColorRequest(matrixDevice, 0, 255, 0),
                    I2COutputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Notification handler — the reactive core of the demo. Babel invokes it
     * <strong>asynchronously</strong> whenever the detector reports one of the
     * subscribed cardinal gestures. The demo reacts by choosing an RGB colour for the
     * direction and driving the whole matrix with a {@link SetDisplayColorRequest}
     * carrying the matrix handle — input event in, output command out, with the demo
     * touching no hardware itself.
     */
    private void handleGestureNotification(GestureNotification not,
                                           short protocolId) {
        // Map each cardinal direction to an RGB colour (default: stay black/off).
        int red = 0, green = 0, blue = 0;
        switch (not.getValue()) {
        case UP:
            red = 255;
            break;
        case DOWN:
            green = 255;
            break;
        case LEFT:
            blue = 255;
            break;
        case RIGHT:
            red = 255;
            green = 255;
            blue = 255;
            break;
        default:
            // Any non-cardinal gesture leaves the colour at (0,0,0) = matrix off.
            break;
        }
        // Apply the chosen colour to the whole matrix via the OUTPUT protocol; the
        // handle routes the request to the right device.
        sendRequest(new SetDisplayColorRequest(matrixDevice, red, green, blue),
                    I2COutputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Application entry point (invoked by {@code Main} for the {@code cardinalGesture}
     * demo). Standard Babel bootstrap:
     * <ol>
     *   <li>get the singleton {@link Babel} instance;</li>
     *   <li>load configuration from {@code paradigmshift.config};</li>
     *   <li>instantiate the control protocols (I²C output 2000 + I²C input 2100) and
     *       the demo protocol;</li>
     *   <li>{@code registerProtocol} each one;</li>
     *   <li>{@code init} them in dependency order — control protocols first, then the
     *       demo, so the demo's first requests have a target;</li>
     *   <li>{@code start} Babel to run the event loops and begin delivering events.</li>
     * </ol>
     * The demo reaches hardware only through these protocols — never via Pi4J/GPIO
     * directly.
     */
    @Override
    public void execute() throws Exception {
        // (1) Babel is a process-wide singleton.
        Babel b = Babel.getInstance();

        // (2) Load runtime configuration.
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelCardinalGestureMatrixDemo gDemo = this;

        // (3) Create the hardware-owning control protocols plus this demo.
        I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
        I2CInputControlProtocol i2cin = new I2CInputControlProtocol();

        // (4) Register every protocol so Babel can route events between them.
        b.registerProtocol(i2cout);
        b.registerProtocol(i2cin);
        b.registerProtocol(gDemo);

        // (5) Initialise in dependency order: control protocols before the demo.
        i2cout.init(props);
        i2cin.init(props);
        gDemo.init(props);

        System.out.println("Setup is complete.");

        // (6) Start Babel: event loops run and handlers begin firing.
        b.start();

        System.out.println("System is running.");
    }
}
