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

/**
 * Demo: drive a Grove LED matrix from a Grove rotary encoder. Turning the knob
 * clockwise grows a "bar" on the matrix; turning it counter-clockwise shrinks
 * it. It is a small but complete <em>input → output</em> loop: a reactive input
 * device feeds an output device, both mediated entirely by Babel events.
 *
 * <p>This is the demo to read to learn the <strong>asynchronous notification</strong>
 * pattern. Unlike the LED and LCD demos (which only register an output device
 * and drive it on a timer), this one subscribes to an
 * {@link EncoderNotification}: the input control protocol pushes one to us every
 * time the knob turns, and we react by updating the matrix. There is no polling.
 *
 * <p><strong>Devices &amp; control protocols used.</strong>
 * <ul>
 *   <li>Grove LED matrix ({@link DeviceType#GROVE_LED_MATRIX}) — output, driven
 *       through {@link I2COutputControlProtocol} (protocol id 2000).</li>
 *   <li>Grove rotary encoder ({@link DeviceType#GROVE_ENCODER}) — reactive input,
 *       read through {@link DigitalInputControlProtocol} (protocol id 2200),
 *       which emits {@link EncoderNotification}s.</li>
 * </ul>
 * Two control protocols, two registered devices, one application protocol — a
 * single Babel app routinely drives several devices across several buses at once.
 *
 * <p><strong>The teaching point.</strong> The app never touches Pi4J, GPIO or
 * I²C directly. It registers two devices, subscribes to encoder turns, and reacts
 * by sending a {@link DisplayBarRequest}; the control protocols do all the
 * hardware work.
 *
 * <p><strong>To run:</strong> {@code java -jar <jar> encoderMatrix} (see
 * {@code Main.java}).
 *
 * <p><strong>Configuration.</strong> The encoder's GPIO line is read from the
 * {@code encoder.line} property (default {@code 5}) in
 * {@code paradigmshift.config}; the matrix is an I²C device and needs no line.
 *
 * <p>Based on IoT-control demos originally developed at NOVA FCT for the TaRDIS
 * project; provided and evolved independently by ParadigmShift.
 */
public class BabelEncoderMatrixDemo
    extends GenericProtocol implements BabelDemo {

    /** Config key naming the GPIO line the rotary encoder is wired to. */
    public static final String ENCODER_PORT = "encoder.line";
    /** Default GPIO line for the encoder. */
    public static final String ENCODER_PORT_DEFAULT = "5";

    /** Handle to the LED matrix; populated asynchronously by the reply handler. */
    private DeviceHandle matrixDevice;
    /** Handle to the rotary encoder; populated asynchronously by the reply handler. */
    private DeviceHandle encoderDevice;

    /** Aliases we register each device under, then verify in the shared reply handler. */
    private String MATRIX_ALIAS = "Matrix";
    private String ENCODER_ALIAS = "Encoder";

    /** Current bar level shown on the matrix; bumped up/down as the knob turns. */
    private AtomicInteger level;
    private int deviceLine;

    /**
     * Sets the protocol identity shared by all demos
     * ({@link BabelDemo#PROTO_NAME} / {@link BabelDemo#PROTO_ID}). Babel handlers
     * are wired later in {@link #init(Properties)}.
     */
    public BabelEncoderMatrixDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    /**
     * Wires this protocol's event handlers and registers both devices.
     *
     * <p>The ordering is the key lesson:
     * <ol>
     *   <li>Register the {@link RegisterIoTDeviceReply} handler <em>before</em>
     *       sending any registration request, so the reply cannot arrive first.
     *       One reply handler serves both devices; it tells them apart by
     *       {@link RegisterIoTDeviceReply#getDeviceType()}.</li>
     *   <li>{@code subscribeNotification(EncoderNotification...)} — this is how
     *       the demo receives asynchronous encoder turns. Once the encoder is set
     *       up for reactive reporting, the input control protocol pushes an
     *       {@link EncoderNotification} on every detent.</li>
     *   <li>Send a {@link RegisterIoTDeviceRequest} for each device, each to its
     *       owning control protocol — the matrix to {@link I2COutputControlProtocol}
     *       (no GPIO line; it is I²C) and the encoder to
     *       {@link DigitalInputControlProtocol} (with its GPIO line).</li>
     * </ol>
     * The handles arrive asynchronously in {@link #handleRegisterIoTDeviceReply}.
     */
    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        this.level = new AtomicInteger(0);

        // Register the (shared) reply handler BEFORE sending any request.
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID,
                             this::handleRegisterIoTDeviceReply);

        // Subscribe to async encoder turns: the input protocol notifies us on each detent.
        subscribeNotification(EncoderNotification.NOTIFICATION_ID,
                              this::handleEncoderNotification);

        // I²C device: no GPIO line, just an alias.
        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LED_MATRIX,
                                                 MATRIX_ALIAS),
                    I2COutputControlProtocol.PROTOCOL_ID);

        this.deviceLine = Integer.parseInt(
            props.getProperty(ENCODER_PORT, ENCODER_PORT_DEFAULT));

        // Digital input device: needs the GPIO line it is wired to.
        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_ENCODER,
                                                 ENCODER_ALIAS,
                                                 this.deviceLine),
                    DigitalInputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Shared reply handler for both device registrations. Babel routes every
     * {@link RegisterIoTDeviceReply} here; we demux on
     * {@link RegisterIoTDeviceReply#getDeviceType()} to learn which device it is
     * for and stash the matching {@link DeviceHandle}.
     *
     * <p>When the <em>encoder</em> reply arrives we issue a
     * {@link GetReactiveEncoderRequest} with a {@link Threshold#none()} threshold:
     * that arms the encoder for reactive reporting, so every subsequent turn comes
     * back as an {@link EncoderNotification} (handled in
     * {@link #handleEncoderNotification}). On any failure the demo bails out, and
     * each branch sanity-checks the alias against what we asked for.
     */
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

    /**
     * Notification handler invoked asynchronously every time the knob turns.
     * Babel delivers an {@link EncoderNotification} here (because we subscribed to
     * its id in {@link #init}); the demo reacts by adjusting the bar level —
     * clockwise increments it, counter-clockwise decrements it — and pushing the
     * new level to the matrix via a {@link DisplayBarRequest} to the
     * {@link I2COutputControlProtocol}. This is the input-event-drives-output
     * loop, with no polling anywhere.
     */
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

    /**
     * Entry point for this demo (called from {@code Main}). Bootstraps Babel: grab
     * the {@link Babel} singleton, load {@code paradigmshift.config}, instantiate
     * the two control protocols this demo needs ({@link I2COutputControlProtocol}
     * for the matrix, {@link DigitalInputControlProtocol} for the encoder),
     * register them plus this demo, then {@code init} all three in dependency
     * order (control protocols first so their handlers exist before the demo sends
     * to them) and start the event loop.
     */
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
