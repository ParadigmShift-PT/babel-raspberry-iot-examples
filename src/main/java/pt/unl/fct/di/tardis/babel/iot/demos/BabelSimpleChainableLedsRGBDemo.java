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
import pt.unl.fct.di.tardis.babel.iot.controlprotocols.requests.output.SetChainableLEDColorRGBRequest;
import pt.unl.fct.di.tardis.babel.iot.demos.events.DemoTimer;

/**
 * Demo: the <em>minimal</em> way to drive a Grove chainable RGB LED strip in the
 * <strong>RGB</strong> colour model. Every LED in the chain is set to the
 * <em>same</em> colour, which sweeps smoothly around the rainbow over time.
 *
 * <p>This is the stripped-down counterpart of {@link BabelChainableLedsRGBDemo}.
 * Where the fuller demo keeps a per-LED colour buffer and pushes the whole strip
 * in one batched request, this one carries no per-LED state: on each tick it
 * computes one colour and sends a separate {@link SetChainableLEDColorRGBRequest}
 * for every LED position. Read this first to see the bare register-then-drive
 * loop, then read the fuller demo to see the batched variant.
 *
 * <p><strong>Devices &amp; control protocols used.</strong> One Grove chainable
 * RGB LED strip ({@link DeviceType#GROVE_CHAINABLE_RGB}), driven through the
 * {@link DigitalOutputControlProtocol} (protocol id 2300).
 *
 * <p><strong>The teaching point.</strong> The app never touches Pi4J or GPIO. It
 * registers the device, keeps the returned {@link DeviceHandle}, and drives the
 * strip purely by sending Babel requests; the control protocol does the GPIO
 * bit-banging.
 *
 * <p><strong>To run:</strong> {@code java -jar <jar> simpleLedsRGB} (see
 * {@code Main.java}).
 *
 * <p><strong>Configuration.</strong> The strip length is read from the
 * {@code rgb.led.count} property (via
 * {@link DigitalOutputControlProtocol#RGB_LED_COUNT}, default 1) and the GPIO
 * data line from {@code led.line} (default {@code 24}) — both in
 * {@code paradigmshift.config}.
 *
 * <p>Based on IoT-control demos originally developed at NOVA FCT for the TaRDIS
 * project; provided and evolved independently by ParadigmShift.
 */
public class BabelSimpleChainableLedsRGBDemo
    extends GenericProtocol implements BabelDemo {

    /** Config key naming the GPIO data line the LED strip is wired to. */
    public static final String LED_PORT = "led.line";
    /** Default GPIO line (BCM 24) — coexists with a seated LoRa HAT. */
    public static final String LED_PORT_DEFAULT = "24";

    /** Opaque reference to the registered strip; populated asynchronously by the reply handler. */
    private DeviceHandle chainableLeds;

    /** Human-readable name we register the strip under, then verify in the reply. */
    public final static String ledAlias = "leds";
    private int deviceLine;
    float lastColor;

    private final int steps = 100;
    private int i = 0;

    /** Number of LEDs in the chain; read from {@code rgb.led.count}. */
    private int numberOfLeds;

    /**
     * Sets the protocol identity shared by all demos
     * ({@link BabelDemo#PROTO_NAME} / {@link BabelDemo#PROTO_ID}). Babel handlers
     * are wired later in {@link #init(Properties)}.
     */
    public BabelSimpleChainableLedsRGBDemo() {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
    }

    /**
     * Wires this protocol's event handlers and starts device registration.
     *
     * <p>The reply and timer handlers are registered <em>before</em> the request
     * is sent, so the {@link RegisterIoTDeviceReply} cannot arrive before its
     * handler is in place. We then ask the {@link DigitalOutputControlProtocol}
     * to register a chainable RGB strip on {@code deviceLine}; the handle comes
     * back asynchronously in {@link #handleRegisterIoTDeviceReply}.
     */
    @Override
    public void init(Properties props)
        throws HandlerRegistrationException, IOException {
        // Register handlers BEFORE issuing the request so the reply can't race us.
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

        // Digital devices need the GPIO line; the control protocol owns the wiring.
        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_CHAINABLE_RGB,
                                                 ledAlias, this.deviceLine),
                    DigitalOutputControlProtocol.PROTOCOL_ID);
    }

    /**
     * Periodic-timer handler: each tick simply advances the colour by repainting
     * the strip. Babel calls this on its event loop once the timer is armed in
     * {@link #handleRegisterIoTDeviceReply}.
     */
    public void handleDemoTimer(DemoTimer t, long time) { updateLedsColors(); }

    /**
     * Reply handler for the device registration. Babel routes the
     * {@link RegisterIoTDeviceReply} here once the control protocol has claimed
     * the hardware.
     *
     * <p>The pattern: check {@link RegisterIoTDeviceReply#isSuccessful()}; on
     * failure, bail out. On success, keep the {@link DeviceHandle} (our only
     * reference to the strip), paint an initial frame, and arm the periodic timer
     * that drives the colour sweep. The alias check guards against a mismatched
     * reply.
     */
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

            // Drive repeated colour changes off a periodic Babel timer.
            setupPeriodicTimer(new DemoTimer(), 50, 50); // 50 Milliseconds wait

        } else {
            System.err.println("Failed to register ChainableLed Device: " +
                               rep.getErrorMessage());
            System.exit(1);
        }
    }

    /**
     * Computes one RGB colour for the current animation step (a sine sweep at
     * three 120° phase offsets — the classic rainbow trick) and sets every LED in
     * the chain to it, one {@link SetChainableLEDColorRGBRequest} per position.
     * Each request carries the device handle and is sent to the
     * {@link DigitalOutputControlProtocol}, which performs the GPIO write.
     */
    private void updateLedsColors() {
        if (i == steps) {
            i = 0; // so it never overflows
        }

        float ratio = (float)(i % steps) / steps;
        double angle = 2 * Math.PI * ratio;

        byte red =
            (byte)(Math.sin(angle) * 127 + 128); // stays between 0 and 255
        byte green = (byte)(Math.sin(angle + (2.0 / 3.0 * Math.PI)) * 127 +
                            128); // 120° phase shift
        byte blue = (byte)(Math.sin(angle + (4.0 / 3.0 * Math.PI)) * 127 +
                           128); // 240° phase shift

        for (byte j = 0; j < numberOfLeds; j++) {

            sendRequest(new SetChainableLEDColorRGBRequest(this.chainableLeds,
                                                           j, red, green, blue),
                        DigitalOutputControlProtocol.PROTOCOL_ID);
        }
        i++;
    }

    /**
     * Entry point for this demo (called from {@code Main}). Bootstraps Babel: grab
     * the {@link Babel} singleton, load {@code paradigmshift.config}, instantiate
     * the one control protocol this demo needs
     * ({@link DigitalOutputControlProtocol}), register it plus this demo,
     * {@code init} them in dependency order (control protocol first so its
     * handlers exist before we send to it), then start the event loop.
     */
    @Override
    public void execute() throws Exception {
        Babel b = Babel.getInstance();

        Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        BabelSimpleChainableLedsRGBDemo demo = this;

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
