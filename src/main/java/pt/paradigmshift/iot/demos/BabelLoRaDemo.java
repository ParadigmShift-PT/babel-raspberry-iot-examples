package pt.paradigmshift.iot.demos;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.pi4j.context.Context;

import lora.LoRaHAT;
import pt.paradigmshift.babel.lora.LoRaProtocol;
import pt.paradigmshift.babel.lora.notifications.LoRaPacketReceivedNotification;
import pt.paradigmshift.babel.radio.notifications.RadioPacketReceivedNotification;
import pt.paradigmshift.babel.radio.notifications.RadioSendFailedNotification;
import pt.paradigmshift.babel.radio.requests.BroadcastRadioPacketRequest;
import pt.paradigmshift.iot.demos.events.RadioSendTimer;
import pt.paradigmshift.iot.pi4j.SharedPi4J;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelDemo;

/**
 * Minimal LoRa send/receive demo built on the ParadigmShift radio stack.
 *
 * <p>It shows the radio-agnostic pattern that the gateway uses in production:
 * the application talks only to the shared {@code babel-radio-api} surface —
 * it sends with a {@link BroadcastRadioPacketRequest} addressed to
 * {@link LoRaProtocol#PROTOCOL_ID} and receives via the generic
 * {@link RadioPacketReceivedNotification}. The same code would work against any
 * radio that implements {@code babel-radio-api}; nothing here is LoRa-specific
 * except the target protocol id and the optional {@link LoRaPacketReceivedNotification}
 * down-cast used to print the RSSI.
 *
 * <p>Two roles, selected by the constructor flag:
 * <ul>
 *   <li><b>sender</b> — broadcasts {@code "ParadigmShift LoRa #<n>"} every few
 *       seconds (and also prints anything it hears);</li>
 *   <li><b>receiver</b> — only listens and prints received frames.</li>
 * </ul>
 * Run two Raspberry Pis, each with a Waveshare SX126X HAT — one as sender, one
 * as receiver — to see frames cross the air.
 *
 * <p>Runs only on a Pi with the LoRa HAT attached. Developed by ParadigmShift, Lda.
 *
 * @author ParadigmShift, Lda (info@paradigmshift.pt)
 */
public class BabelLoRaDemo extends GenericProtocol implements BabelDemo {

    /** Config key: UART device the LoRa HAT is on. */
    public static final String LORA_DEVICE = "lora.device";
    public static final String LORA_DEVICE_DEFAULT = "/dev/ttyAMA0";
    /** Config key: 16-bit on-air address of this node (hex, e.g. {@code 0x0001}). */
    public static final String LORA_OWN_ADDR = "lora.own.addr";
    public static final String LORA_OWN_ADDR_DEFAULT = "0x0001";

    private static final long SEND_PERIOD_MS = 3000;

    private final boolean sender;
    private int counter;

    /**
     * @param sender {@code true} to broadcast periodically, {@code false} to
     *               run as a pure receiver
     */
    public BabelLoRaDemo(boolean sender) {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
        this.sender = sender;
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
        // Both roles subscribe to the shared inbound notifications. The local
        // `sourceProto` carried by each event lets us ignore traffic that did
        // not originate from this demo (see onPacket).
        subscribeNotification(RadioPacketReceivedNotification.NOTIFICATION_ID, this::onPacket);
        subscribeNotification(RadioSendFailedNotification.NOTIFICATION_ID, this::onSendFailed);

        if (sender) {
            registerTimerHandler(RadioSendTimer.TIMER_ID, this::onSendTick);
            setupPeriodicTimer(new RadioSendTimer(), SEND_PERIOD_MS, SEND_PERIOD_MS);
            System.out.println("[LoRa demo] sender — broadcasting every " + SEND_PERIOD_MS + " ms");
        } else {
            System.out.println("[LoRa demo] receiver — listening for LoRa frames");
        }
    }

    private void onSendTick(RadioSendTimer t, long timerId) {
        byte[] payload = ("ParadigmShift LoRa #" + (counter++))
                .getBytes(StandardCharsets.US_ASCII);
        // Tag the frame with our own protocol id so the receiver can filter it in.
        sendRequest(new BroadcastRadioPacketRequest(BabelDemo.PROTO_ID, payload),
                    LoRaProtocol.PROTOCOL_ID);
        System.out.println("[LoRa ->] broadcast " + payload.length + " bytes");
    }

    private void onPacket(RadioPacketReceivedNotification n, short from) {
        if (n.getSourceProto() != BabelDemo.PROTO_ID) {
            return; // traffic from some other protocol — not ours
        }
        String text = new String(n.getPayload(), StandardCharsets.US_ASCII);
        if (n instanceof LoRaPacketReceivedNotification lora) {
            System.out.println("[LoRa <-] origin=" + lora.getLoRaOrigin()
                    + " rssi=" + lora.getRssi() + " dBm  \"" + text + "\"");
        } else {
            System.out.println("[LoRa <-] origin=" + n.getOrigin() + "  \"" + text + "\"");
        }
    }

    private void onSendFailed(RadioSendFailedNotification n, short from) {
        if (n.getSourceProto() != BabelDemo.PROTO_ID) {
            return;
        }
        System.err.println("[LoRa x] send failed: " + n.getReason());
    }

    @Override
    public void execute() throws Exception {
        Babel babel = Babel.getInstance();
        Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        int ownAddr = Integer.decode(props.getProperty(LORA_OWN_ADDR, LORA_OWN_ADDR_DEFAULT));
        String device = props.getProperty(LORA_DEVICE, LORA_DEVICE_DEFAULT);

        // One shared Pi4J context for the whole process (see pi4j-shared-context).
        Context pi4j = SharedPi4J.get();
        LoRaHAT hat = new LoRaHAT(pi4j, ownAddr, device);
        hat.init();

        LoRaProtocol lora = new LoRaProtocol(hat, ownAddr);

        babel.registerProtocol(lora);
        babel.registerProtocol(this);

        lora.init(props);
        this.init(props);

        babel.start();
        System.out.println("LoRa demo running (" + (sender ? "sender" : "receiver")
                + ", own address 0x" + Integer.toHexString(ownAddr) + ").");
    }
}
