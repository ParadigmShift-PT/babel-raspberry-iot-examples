package pt.paradigmshift.iot.demos;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import pt.paradigmshift.babel.radio.notifications.RadioPacketReceivedNotification;
import pt.paradigmshift.babel.radio.notifications.RadioSendFailedNotification;
import pt.paradigmshift.babel.radio.requests.BroadcastRadioPacketRequest;
import pt.paradigmshift.babel.zigbee.ZigBeeProtocol;
import pt.paradigmshift.babel.zigbee.notifications.ZigBeePacketReceivedNotification;
import pt.paradigmshift.iot.demos.events.RadioSendTimer;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelDemo;
import zigbee.ZigBeeCoordinator;
import zigbee.ZigBeeCoordinator.ZigBeeConfig;

/**
 * Minimal ZigBee send/receive demo built on the ParadigmShift radio stack.
 *
 * <p>Like {@link BabelLoRaDemo}, the application code is radio-agnostic: it
 * sends with a {@link BroadcastRadioPacketRequest} (an NWK-layer broadcast to
 * all joined devices) addressed to {@link ZigBeeProtocol#PROTOCOL_ID}, and
 * receives via the generic {@link RadioPacketReceivedNotification}. The only
 * ZigBee-specific touch is the optional {@link ZigBeePacketReceivedNotification}
 * down-cast used to print the packet id / value.
 *
 * <p>Two roles, selected by the constructor flag:
 * <ul>
 *   <li><b>sender</b> — broadcasts {@code "ParadigmShift ZigBee #<n>"} every few
 *       seconds. Note ZigBee broadcasts are unacknowledged: sleepy end devices
 *       that are not awake at that moment miss the frame;</li>
 *   <li><b>receiver</b> — only listens and prints frames arriving from joined
 *       end devices.</li>
 * </ul>
 *
 * <p>Runs only with an Ember (EZSP) ZigBee coordinator dongle attached over USB
 * serial. The serial port is auto-discovered unless {@code zigbee.serial.port}
 * is set. Developed by ParadigmShift, Lda.
 *
 * @author ParadigmShift, Lda (info@paradigmshift.pt)
 */
public class BabelZigBeeDemo extends GenericProtocol implements BabelDemo {

    /** Config key: serial port of the EZSP dongle; empty ⇒ auto-discover. */
    public static final String ZIGBEE_PORT = "zigbee.serial.port";
    /** Config key: seconds the network is left open for joining at startup. */
    public static final String ZIGBEE_PERMIT_JOIN = "zigbee.permit.join.seconds";
    public static final String ZIGBEE_PERMIT_JOIN_DEFAULT = "254";

    private static final long SEND_PERIOD_MS = 3000;

    private final boolean sender;
    private int counter;

    /**
     * @param sender {@code true} to broadcast periodically, {@code false} to
     *               run as a pure receiver
     */
    public BabelZigBeeDemo(boolean sender) {
        super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID);
        this.sender = sender;
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
        subscribeNotification(RadioPacketReceivedNotification.NOTIFICATION_ID, this::onPacket);
        subscribeNotification(RadioSendFailedNotification.NOTIFICATION_ID, this::onSendFailed);

        if (sender) {
            registerTimerHandler(RadioSendTimer.TIMER_ID, this::onSendTick);
            setupPeriodicTimer(new RadioSendTimer(), SEND_PERIOD_MS, SEND_PERIOD_MS);
            System.out.println("[ZigBee demo] sender — broadcasting every " + SEND_PERIOD_MS + " ms");
        } else {
            System.out.println("[ZigBee demo] receiver — listening for ZigBee frames");
        }
    }

    private void onSendTick(RadioSendTimer t, long timerId) {
        byte[] payload = ("ParadigmShift ZigBee #" + (counter++))
                .getBytes(StandardCharsets.US_ASCII);
        sendRequest(new BroadcastRadioPacketRequest(BabelDemo.PROTO_ID, payload),
                    ZigBeeProtocol.PROTOCOL_ID);
        System.out.println("[ZigBee ->] broadcast " + payload.length + " bytes");
    }

    private void onPacket(RadioPacketReceivedNotification n, short from) {
        if (n.getSourceProto() != BabelDemo.PROTO_ID) {
            return;
        }
        String text = new String(n.getPayload(), StandardCharsets.US_ASCII);
        if (n instanceof ZigBeePacketReceivedNotification zb) {
            System.out.println("[ZigBee <-] origin=" + zb.getZigBeeOrigin()
                    + " id=" + zb.getPacketId() + " val=" + zb.getVal() + "  \"" + text + "\"");
        } else {
            System.out.println("[ZigBee <-] origin=" + n.getOrigin() + "  \"" + text + "\"");
        }
    }

    private void onSendFailed(RadioSendFailedNotification n, short from) {
        if (n.getSourceProto() != BabelDemo.PROTO_ID) {
            return;
        }
        System.err.println("[ZigBee x] send failed: " + n.getReason());
    }

    @Override
    public void execute() throws Exception {
        Babel babel = Babel.getInstance();
        Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        // Pick an explicit serial port from config, else auto-discover (the
        // driver fails fast and clearly when the choice is ambiguous).
        String port = props.getProperty(ZIGBEE_PORT, "").trim();
        if (port.isEmpty()) {
            port = ZigBeeCoordinator.autoDiscoverSerialPort();
            System.out.println("[ZigBee demo] serial port auto-discovered: " + port);
        }

        ZigBeeCoordinator coordinator = new ZigBeeCoordinator(
                new ZigBeeConfig.Builder().serialPort(port).build());
        coordinator.init();
        int permit = Integer.parseInt(
                props.getProperty(ZIGBEE_PERMIT_JOIN, ZIGBEE_PERMIT_JOIN_DEFAULT));
        coordinator.permitJoin(permit);
        System.out.println("[ZigBee demo] coordinator up; network open for joining (" + permit + "s)");

        ZigBeeProtocol zigbee = new ZigBeeProtocol(coordinator);

        babel.registerProtocol(zigbee);
        babel.registerProtocol(this);

        zigbee.init(props);
        this.init(props);

        babel.start();
        System.out.println("ZigBee demo running (" + (sender ? "sender" : "receiver") + ").");
    }
}
