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

/**
 * Reactive Babel demo: draws an <em>arrow</em> on the Grove RGB LED matrix that
 * matches the direction of the detected gesture (UP/DOWN/LEFT/RIGHT), then clears the
 * matrix shortly afterwards if no new gesture arrives.
 *
 * <p>Like its sibling demos it illustrates <strong>reactive input driving output</strong>,
 * wiring two IoT control protocols together:</p>
 * <ul>
 *   <li>{@link I2CInputControlProtocol} (id {@code 2100}) reads the Grove gesture
 *       detector and emits a {@link GestureNotification} for each recognised gesture
 *       (here filtered to the four cardinal directions);</li>
 *   <li>{@link I2COutputControlProtocol} (id {@code 2000}) drives the Grove RGB LED
 *       matrix; the demo sends it a {@link ShowDisplayRequest} with a bitmap encoding
 *       the matching arrow, and a {@link ClearDisplayRequest} to blank it.</li>
 * </ul>
 *
 * <p>The added wrinkle versus {@link BabelAnyGestureMatrixDemo} is a self-cancelling
 * "auto-clear" timer: each gesture stamps {@link #lastActionTimestamp} and schedules a
 * {@link DemoTimer} carrying that stamp; the timer only clears the display if it is
 * still the latest action, so a fresh gesture before the timeout cancels the pending
 * clear. The demo never touches Pi4J/GPIO directly — it only exchanges Babel
 * requests/replies/notifications with the control protocols, following "Pattern 1:
 * driving a Grove device" from the module {@code README.md}.</p>
 *
 * <p>Run it with the command-line name {@code arrowGesture} (see {@code Main.java}):
 * <pre>java -jar babel-raspberry-iot-examples.jar arrowGesture</pre></p>
 *
 * @see BabelLcdDemo the fully-commented output-only exemplar in this package
 */
public class BabelArrowGestureMatrixDemo
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

    // Timestamp of the most recent gesture. Used as a token to make the auto-clear
    // timer self-cancelling: a clear only fires if its stamp is still the latest one.
    private long lastActionTimestamp;

    /**
     * Builds the demo protocol with the shared {@code BabelDemo} name/id (Babel routes
     * events by that id) and initialises the auto-clear timestamp to zero.
     */
    public BabelArrowGestureMatrixDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
        this.lastActionTimestamp = 0;
    }

    /**
     * Babel lifecycle hook: register all handlers, then send the requests that will
     * trigger them. As always, handlers are registered <strong>before</strong> the
     * requests so no reply or notification can arrive unhandled.
     */
    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        // Timer handler for the auto-clear timer (see handleDemoTimer).
        registerTimerHandler(DemoTimer.TIMER_ID, this::handleDemoTimer);
        // Reply handler that delivers each device's DeviceHandle on registration.
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);

        // Reply handler acknowledging the reactive-gesture subscription request.
        registerReplyHandler(GestureInputReply.REPLY_ID,
                             this::handleGestureInputReply);

        // The reactive twist: subscribe to GestureNotification so the input protocol
        // PUSHES us an event asynchronously whenever it recognises a matching gesture.
        subscribeNotification(GestureNotification.NOTIFICATION_ID,
                              this::handleGestureNotification);

        // Handlers are in place — now request the two devices.
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
     * Reply handler for {@link RegisterIoTDeviceReply}, invoked once per registration.
     * The canonical pattern: check {@link RegisterIoTDeviceReply#isSuccessful()} and on
     * success keep the {@link DeviceHandle}. Because both devices share this handler, it
     * branches on the reply's device type to store the right handle and act accordingly.
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
                // Start from a blank matrix: send a ClearDisplayRequest carrying the
                // matrix handle to the OUTPUT protocol.
                sendRequest(new ClearDisplayRequest(matrixDevice),
                		I2COutputControlProtocol.PROTOCOL_ID);
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

                // Arm the reactive input, but only for the four cardinal directions.
                Set<PAJ7620GestureType> gestures =
                    Set.of(PAJ7620GestureType.UP, PAJ7620GestureType.DOWN,
                           PAJ7620GestureType.LEFT, PAJ7620GestureType.RIGHT);

                // Threshold.any(gestures) tells the input protocol to PUSH a
                // GestureNotification only when the detected gesture is one of those
                // directions — other gestures are filtered out at the source.
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

    /**
     * Reply handler for the {@link GetReactiveGestureRequest}. It confirms the input
     * protocol accepted the reactive subscription; on success the demo paints the
     * matrix blue as a "ready" acknowledgement and arms a one-shot auto-clear timer
     * stamped with the current time so the display blanks if nothing follows.
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

            // Record this as the latest action and schedule a 2 s one-shot timer that
            // carries the same stamp. handleDemoTimer clears the display only if this
            // is still the latest action, so a newer gesture cancels the clear.
            this.lastActionTimestamp = System.currentTimeMillis();
            setupTimer(new DemoTimer(this.lastActionTimestamp), 2000);

        } else {
            System.err.println("Failed to receive gesture info: " +
                               rep.getErrorMessage());
        }
    }

    /**
     * Timer handler for the auto-clear {@link DemoTimer}. It clears the matrix only if
     * the timer's stamp still equals {@link #lastActionTimestamp} — i.e. no newer
     * gesture happened since this timer was armed. This "timestamp token" trick lets a
     * fresh gesture implicitly cancel a stale pending clear without tracking timer ids.
     */
    public void handleDemoTimer(DemoTimer t, long time) {
        if(this.lastActionTimestamp == t.getTimestamp()) {
        	sendRequest(new ClearDisplayRequest(matrixDevice),
                          I2COutputControlProtocol.PROTOCOL_ID);
        }
    }

    /**
     * Notification handler — the reactive core of the demo. Babel invokes it
     * <strong>asynchronously</strong> whenever the detector reports one of the
     * subscribed cardinal gestures. The demo reacts by encoding the matching arrow
     * bitmap and driving the LED matrix with a {@link ShowDisplayRequest} (carrying the
     * matrix handle), then re-arms the auto-clear timer. The app issues only Babel
     * requests — the control protocol does the actual I²C work.
     */
    private void handleGestureNotification(GestureNotification not,
                                           short protocolId) {
        // Map each cardinal gesture to the matching arrow bitmap and show it.
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
            // Any non-cardinal gesture is ignored (it never gets here when the
            // reactive Threshold is doing its job, but guard anyway).
            return;
        }
        // Mark this gesture as the latest action and arm a fresh auto-clear timer
        // stamped with it, so the arrow is wiped after the timeout unless superseded.
        this.lastActionTimestamp = System.currentTimeMillis();
        setupTimer(new DemoTimer(this.lastActionTimestamp),
        		I2COutputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Application entry point (invoked by {@code Main} for the {@code arrowGesture}
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
     * The demo accesses hardware only through these protocols — it never builds a Pi4J
     * context or opens an I²C bus itself.
     */
    @Override
    public void execute() throws Exception {
        // (1) Babel is a process-wide singleton.
        Babel b = Babel.getInstance();

        // (2) Load runtime configuration.
		Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelArrowGestureMatrixDemo gDemo = this;

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
