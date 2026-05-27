# ParadigmShift Babel IoT Examples for Raspberry Pi

A collection of small, **runnable demo applications** that double as a guide to
building IoT applications on a Raspberry Pi with [Babel](https://novasys.di.fct.unl.pt/)
using the ParadigmShift libraries. Each demo is a complete, minimal program you
can run on real hardware and read end-to-end to see one piece of the puzzle.

> These are **executables for learning and hardware bring-up, not a library.**
> The project is *not* published to the ParadigmShift Maven repository — see
> [Distribution](#distribution).

This README is a **developer guide**, not just a list. It explains the
architecture, the patterns you copy, what these libraries can and can't do, and
walks one demo end-to-end so the rest become easy to read.

**Quick links**

- [Requirements](#requirements) · [Quickstart](#quickstart)
- [Architecture](#architecture-the-layers-you-build-on) · [How to write an IoT-control app](#pattern-1-driving-a-grove-device) · [How to write a radio app](#pattern-2-sending-and-receiving-over-a-radio)
- [What you can and can't do](#capabilities-and-limits)
- [IoT device demos](#iot-device-demos) · [Radio demos](#radio-demos-lora--zigbee)
- [Anatomy of a demo](#anatomy-of-a-demo-bablelcddemo) · [Adding your own demo](#adding-your-own-demo)

---

## Requirements

- **Java 21** and Maven 3.6+ to build (the ZigBee stack pulls the Java 21 floor).
- A **Raspberry Pi** (4 or 5) to run — the demos drive real GPIO/I²C/UART hardware.
- Depending on the demo: Grove devices on a Pi4J-compatible GrovePi HAT, a
  Waveshare SX126X LoRa HAT, or an Ember (EZSP) ZigBee USB dongle.
- **OS package** (Raspbian / Raspberry Pi OS): `sudo apt install i2c-tools`.
  The I²C demos depend on `babel-iot-control-protocols`, whose `I2CScanner`
  shells out to `i2cdetect` to enumerate connected devices. Without the
  package every I²C probe fails with `Cannot run program "i2cdetect":
  error=2, No such file or directory`.

All Maven dependencies resolve from the **ParadigmShift Maven repository**
(`https://maven.paradigmshift.pt/releases`, read-open — no credentials needed),
already configured in `pom.xml`.

---

## Quickstart

```bash
mvn package
java -jar target/babel-raspberry-iot-examples.jar             # prints the demo list
java -jar target/babel-raspberry-iot-examples.jar Lcd         # run a specific demo
```

Each demo takes a single string argument naming it. With no argument the
program prints every available demo and exits.

---

## Architecture: the layers you build on

These demos sit on a stack of cooperating libraries. You write at the top —
your application protocol — and the layers below take care of the rest.

```
 ┌──────────────────────────────────────────────────────────────────────────┐
 │  YOUR APPLICATION PROTOCOL (extends GenericProtocol)                     │
 │                                                                          │
 │   • Builds RegisterIoTDeviceRequest / BroadcastRadioPacketRequest …      │
 │   • Subscribes to notifications, registers reply/timer handlers          │
 └────┬───────────────────────────────────────────┬─────────────────────────┘
      │ Babel async events (requests/replies/notifications/timers)
      ▼                                            ▼
 ┌─────────────────────────────┐       ┌─────────────────────────────────┐
 │  babel-iot-control-protocols│       │  babel-lora-protocol            │
 │   • I2COutputControlProtocol│       │  babel-zigbee-protocol          │
 │     (id 2000) — LCD, matrix │       │   • Implement the shared        │
 │   • I2CInputControlProtocol │       │     babel-radio-api surface     │
 │     (id 2100) — gesture,    │       │   • LoRa id 1100, ZigBee id 1200│
 │     accel, barometer        │       └────┬────────────────────────────┘
 │   • DigitalInputControl…    │            │
 │     (id 2200) — encoder,    │            ▼
 │     ultrasonic              │       ┌─────────────────────────────────┐
 │   • DigitalOutputControl…   │       │  babel-lora-standalone (HAT)    │
 │     (id 2300) — chainable   │       │  babel-zigbee-standalone (EZSP) │
 │     RGB                     │       │   • Plain Java drivers, no      │
 │   • Speaks babel-iot-       │       │     Babel dependency            │
 │     control-api requests/   │       └────┬────────────────────────────┘
 │     replies                 │            │
 └────┬────────────────────────┘            ▼ UART / USB serial
      │
      ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │  pi4j-iot-device-library (GroveLcd, GroveChainableRGB, …)              │
 │  pi4j-components (LED matrix driver, ADCs, joysticks, …)               │
 │  Pi4J 2.x  (raspberrypi, linuxfs, gpiod, pigpio providers)             │
 │                                                                        │
 │      ↑ all consumers fetch the one allowed Pi4J Context from           │
 │        pi4j-shared-context (SharedPi4J.get())                          │
 └────────────────────────────────────────────────────────────────────────┘
```

**Why this layering matters in practice:**

- **You never touch Pi4J directly.** Your protocol sends Babel requests; the
  control protocols translate them into Pi4J / I²C / GPIO operations.
- **Devices live behind opaque handles.** You register a device once and get a
  `DeviceHandle` back; every subsequent action just references that handle.
- **Radios are interchangeable.** Both LoRa and ZigBee implement the
  `babel-radio-api` surface (`BroadcastRadioPacketRequest`,
  `RadioPacketReceivedNotification`, …), so the same application code works
  against either — only the target protocol id changes.
- **There is exactly one Pi4J Context in the process.** Pi4J's pigpio and
  libgpiod backends each claim the GPIO peripheral exclusively, so a second
  context would fail with `Device or resource busy`. `pi4j-shared-context`
  enforces the rule by exposing one lazy singleton.

### Dependency map

What's already brought in via the POM, and which library it comes from:

| Library | What it gives you | Pulled by |
|---|---|---|
| `pt.paradigmshift.iot:babel-iot-control-protocols:1.2.0` | The four control protocols + all output/input requests | direct |
| `pt.paradigmshift.iot:babel-iot-control-api:1.1.0` | `DeviceHandle`, `DeviceType`, `Threshold`, `RegisterIoTDeviceRequest/Reply`, request base classes | transitive |
| `pt.paradigmshift.iot:pi4j-iot-device-library:1.0.0` | Grove drivers (`GroveLcd`, `GroveChainableRGB`, `GroveLedMatrix`, `GroveGestureDetector`, …) + `LedMatrixUtils` | transitive |
| `pt.paradigmshift.iot:pi4j-components:0.0.7` | Generic Pi4J component catalogue (LEDs, buttons, ADCs, displays, …) | transitive |
| `pt.paradigmshift.iot:pi4j-shared-context:0.1.0` | `SharedPi4J.get()` — the one Pi4J Context | direct (also transitive) |
| `pt.paradigmshift.babel:babel-radio-api:0.2.0` | `RadioAddress`, `(Send|Broadcast)RadioPacketRequest`, `RadioPacketReceivedNotification`, `RadioSendFailedNotification` | direct |
| `pt.paradigmshift.babel:babel-lora-protocol:0.3.0` | `LoRaProtocol` (id 1100), `LoRaAddress`, `LoRaPacketReceivedNotification` (adds RSSI/prevHop) | direct |
| `pt.paradigmshift.babel:babel-zigbee-protocol:0.3.0` | `ZigBeeProtocol` (id 1200), `ZigBeeAddress`, `ZigBeePacketReceivedNotification` (adds packetId/val), `ZigBeeHeartbeatNotification` | direct |
| `pt.paradigmshift.iot:babel-lora:0.2.2` | Standalone `LoRaHAT` driver (UART + M0/M1 GPIO) | transitive |
| `pt.paradigmshift.iot:babel-zigbee:0.1.0` | Standalone `ZigBeeCoordinator` (Ember EZSP via USB serial) | transitive |
| `pt.paradigmshift.babel:babel-core:1.0.0` | Babel itself — `GenericProtocol`, `Babel`, channels, timers | transitive |

---

## Pattern 1: driving a Grove device

The contract is the same for every device, regardless of bus:

1. **Send a `RegisterIoTDeviceRequest`** to the appropriate control protocol
   (one of `I2COutputControlProtocol` 2000, `I2CInputControlProtocol` 2100,
   `DigitalInputControlProtocol` 2200, `DigitalOutputControlProtocol` 2300).
   For I²C devices the GPIO line is omitted; for digital devices it's required.
2. **Register a reply handler** for `RegisterIoTDeviceReply` and, when it
   arrives, **keep the `DeviceHandle`** — that is your reference to the device.
3. **Send typed requests** carrying the handle to act on the device
   (`ShowTextRequest`, `SetChainableLEDColorRGBRequest`, …).
4. **Subscribe to notifications** for asynchronous input events
   (`GestureNotification`, `EncoderNotification`).

A complete LCD example, abbreviated:

```java
public class MyApp extends GenericProtocol implements BabelDemo {
    public MyApp() { super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID); }

    private DeviceHandle lcd;

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
        // (1) Listen for the reply that will bring us the handle.
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, this::onRegistered);

        // (2) Ask the I²C output protocol to give us an LCD.
        //     No GPIO line for I²C devices — the alias is just a name.
        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD, "my-lcd"),
                    I2COutputControlProtocol.PROTOCOL_ID);
    }

    private void onRegistered(RegisterIoTDeviceReply r, short from) {
        if (!r.isSuccessful()) { /* log r.getErrorCode() / r.getErrorMessage() */ return; }
        this.lcd = r.getDeviceHandle();

        // (3) Drive the device. Use "\n" to write the second row of a 16x2 LCD.
        sendRequest(new ShowTextRequest(lcd, "Hello\nWorld"),
                    I2COutputControlProtocol.PROTOCOL_ID);
    }
}
```

Reactive inputs (gesture, encoder) follow the same flow plus a notification
subscription — see `BabelControlableLedChainDemo` for the canonical version.

### Which control protocol owns which device

| Protocol (id) | `DeviceType` | Notes |
|---|---|---|
| `I2COutputControlProtocol` (2000) | `GROVE_LCD`, `GROVE_LED_MATRIX` | Probes the I²C bus on register; missing devices fail fast |
| `I2CInputControlProtocol` (2100) | `GROVE_3AXIS_ACCELEROMETER`, `GROVE_BAROMETER`, `GROVE_GESTURE_DETECTOR` | Emits `GestureNotification` on reactive gesture requests |
| `DigitalInputControlProtocol` (2200) | `GROVE_ENCODER`, `GROVE_ULTRASONIC_RANGER`, `GROVE_DIGITAL_INPUT_DEVICE` | Emits `EncoderNotification` on reactive encoder requests |
| `DigitalOutputControlProtocol` (2300) | `GROVE_CHAINABLE_RGB`, `GROVE_4DIGIT_DISPLAY`, `GROVE_BUZZER`, `GROVE_LED_BAR`, `GROVE_DIGITAL_OUTPUT_DEVICE` | Reads the chainable-RGB strip length from `rgb.led.count` |

---

## Pattern 2: sending and receiving over a radio

The radio API (`babel-radio-api`) is **radio-agnostic**: the same application
code sends and receives over LoRa or ZigBee, you just point the request at a
different protocol id. The two radio protocols share one notification id space
in slot `400`, so a single subscription catches packets from both radios. Demux
on the local `sourceProto` parameter Babel passes to your handler — the wire
envelope carries the **application** protocol id of the sender, not the radio
that delivered the frame.

```java
public class MyRadioApp extends GenericProtocol implements BabelDemo {
    public MyRadioApp() { super(BabelDemo.PROTO_NAME, BabelDemo.PROTO_ID); }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
        // One subscription catches LoRa and ZigBee traffic alike.
        subscribeNotification(RadioPacketReceivedNotification.NOTIFICATION_ID, this::onPacket);
        subscribeNotification(RadioSendFailedNotification.NOTIFICATION_ID,     this::onFail);
    }

    public void broadcastSomething() {
        byte[] payload = "hello".getBytes(StandardCharsets.US_ASCII);
        // Tag the frame with OUR protocol id so the receiver can filter it in.
        sendRequest(new BroadcastRadioPacketRequest(BabelDemo.PROTO_ID, payload),
                    LoRaProtocol.PROTOCOL_ID);              // or ZigBeeProtocol.PROTOCOL_ID
    }

    private void onPacket(RadioPacketReceivedNotification n, short from) {
        if (n.getSourceProto() != BabelDemo.PROTO_ID) return;   // not our traffic
        if (n instanceof LoRaPacketReceivedNotification lora) {
            // LoRa-specific extras (optional down-cast)
            System.out.println("rssi=" + lora.getRssi() + " dBm");
        }
        // Generic fields available from the base class:
        System.out.println("from " + n.getOrigin() + " : "
                + new String(n.getPayload(), StandardCharsets.US_ASCII));
    }

    private void onFail(RadioSendFailedNotification n, short from) {
        if (n.getSourceProto() != BabelDemo.PROTO_ID) return;
        System.err.println("send failed: " + n.getReason());
    }
}
```

**Filter on `sourceProto`.** Several protocols can share one radio. The
`sourceProto` short on every request and notification lets each application
ignore traffic that wasn't addressed to it.

**Use the generic types unless you need extras.** `RadioPacketReceivedNotification`
gives you `getOrigin()` (an abstract `RadioAddress`) and `getPayload()` — that
is all most code needs. Cast to `LoRaPacketReceivedNotification` only to read
RSSI; cast to `ZigBeePacketReceivedNotification` only to read the µBabel packet
id / value. This is what keeps app code portable across radios.

---

## Capabilities and limits

### What you CAN do

- **Mix freely.** A single Babel app can drive Grove I²C devices, Grove digital
  devices, and one or both radios at the same time — that is exactly the
  StoneFlux edge gateway. `BabelControlableLedChainDemo` is the in-repo proof.
- **Add new application protocols** without touching the libraries: register
  the devices you need, subscribe to the events you care about, send
  requests/timers to drive output.
- **Replace one radio with another** (LoRa ↔ ZigBee) by changing the target
  protocol id in `sendRequest(...)`. The notification handler keeps working.

### What you CAN'T do (yet) — and why

- **You cannot build a second Pi4J `Context`.** Pi4J's pigpio init and libgpiod
  line requests each take exclusive ownership of the GPIO peripheral; a second
  context fails with `GpioDException: Device or resource busy`. Use
  `SharedPi4J.get()` everywhere, including for the LoRa HAT. Library code in
  `babel-iot-control-protocols 1.2.0` already does.
- **You cannot run two of these JVMs against the same Pi at once.** The OS-level
  exclusivity above applies process-wide, not just JVM-wide. One demo per Pi.
- **You cannot inject your own `Context` into the control protocols.** Their
  no-arg constructors fetch the singleton internally — by design. Only the LoRa
  `LoRaHAT` and ZigBee `ZigBeeCoordinator` accept injection.
- **You cannot run these demos off a Raspberry Pi.** They compile anywhere, but
  building the Pi4J context performs native GPIO initialisation; on macOS /
  Linux x86 you'll see a startup failure.
- **ZigBee broadcasts are unacknowledged.** A sleepy end device that's not
  awake at the moment of broadcast simply misses the frame. Don't use ZigBee
  broadcast for state you need every device to see — use unicast there.
- **LoRa is half-duplex, ALOHA, no MAC layer.** Two senders close in time will
  collide; expect retries at the application layer if you care about delivery.
- **I²C device addressing is fixed.** Two devices of the same model on one
  bus will collide on the same I²C address. The control protocol probes the
  bus and rejects registrations for missing devices, but it cannot disambiguate
  duplicates.
- **The chainable RGB protocol assumes one strip per process.** It caches the
  driver in a single `rgb` field; if you need two strips you'll need to extend
  the protocol.
- **`Babel` is itself a singleton.** A single JVM hosts exactly one
  `Babel.getInstance()`. All demos live in one process — that is why `Main`
  picks just one by command-line argument.

---

## IoT device demos

These drive Grove devices through the IoT control protocols. Each demo is one
class implementing `BabelDemo`; reading any of them top-to-bottom shows the
register-then-drive pattern in full.

| Demo name | What it does | Hardware |
|---|---|---|
| `LedMatrix` | Cycles through several images on the LED matrix | Grove RGB LED matrix (I²C) |
| `LedMatrix2` | LED matrix with extra icons/features | Grove RGB LED matrix (I²C) |
| `Lcd` | Shows a sequence of messages on the LCD | Grove LCD (I²C) |
| `Accel` | Periodically reads the accelerometer and shows it on the LCD | Grove 3-axis accelerometer + LCD (I²C) |
| `anyGesture` | Shows an emoji on the matrix for any detected gesture | Grove gesture detector + LED matrix (I²C) |
| `cardinalGesture` | Colours the matrix by the cardinal gesture (UP/DOWN/LEFT/RIGHT) | Grove gesture detector + LED matrix (I²C) |
| `arrowGesture` | Shows an arrow on the matrix matching the gesture | Grove gesture detector + LED matrix (I²C) |
| `encoderMatrix` | Drives the matrix from the rotary encoder | Grove rotary encoder + LED matrix |
| `ledsRGB` | Chainable RGB LEDs cycling random colours (RGB model) | Grove chainable RGB LED (D26) |
| `ledsHSB` | Chainable RGB LEDs cycling random colours (HSB model) | Grove chainable RGB LED (D26) |
| `simpleLedsRGB` | Minimal chainable-LED RGB control | Grove chainable RGB LED (D26) |
| `simpleLedsHSB` | Minimal chainable-LED HSB control | Grove chainable RGB LED (D26) |
| `lightControl` | Gesture-controlled lights: RGB chain + gesture detector + matrix + LCD | All of the above |

The chainable-RGB demos read the strip length from `rgb.led.count` and the GPIO
line from `led.line` (default `26`) in `paradigmshift.config`.

---

## Radio demos (LoRa & ZigBee)

These show the radio-agnostic pattern described above. Run a **sender on one
Pi** and a **receiver on another** to watch frames cross the air:

| Demo name | Role | Hardware |
|---|---|---|
| `loraSend` | Broadcast `ParadigmShift LoRa #<n>` every 3 s | Waveshare SX126X LoRa HAT |
| `loraReceive` | Listen and print received LoRa frames | Waveshare SX126X LoRa HAT |
| `zigbeeSend` | Broadcast `ParadigmShift ZigBee #<n>` every 3 s (NWK broadcast) | Ember EZSP ZigBee dongle |
| `zigbeeReceive` | Listen and print received ZigBee frames | Ember EZSP ZigBee dongle |

Example:

```bash
# Pi A
java -jar target/babel-raspberry-iot-examples.jar loraReceive
# Pi B
java -jar target/babel-raspberry-iot-examples.jar loraSend
```

Configuration keys (in `paradigmshift.config`, all optional — sensible defaults
apply):

| Key | Default | Used by |
|---|---|---|
| `lora.device` | `/dev/ttyAMA0` | LoRa demos — UART device of the HAT |
| `lora.own.addr` | `0x0001` | LoRa demos — 16-bit on-air address of this node |
| `zigbee.serial.port` | *(empty → auto-discover)* | ZigBee demos — EZSP dongle serial port |
| `zigbee.permit.join.seconds` | `254` | ZigBee demos — how long the network stays open for joining |

---

## Anatomy of a demo: `BabelLcdDemo`

To make the layering concrete, here is the LCD demo's structure annotated.
Read it alongside `src/main/java/pt/unl/fct/di/tardis/babel/iot/demos/BabelLcdDemo.java`.

```java
// One class. Two duties:
//   1. As GenericProtocol, runs on Babel's event loop, holds the handle,
//      reacts to replies/timers.
//   2. As BabelDemo, knows how to bootstrap its own runtime in execute().
public class BabelLcdDemo extends GenericProtocol implements BabelDemo {

    private DeviceHandle lcd;                            // populated asynchronously

    public BabelLcdDemo() { super(PROTO_NAME, PROTO_ID); }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
        // (a) Register a reply handler BEFORE sending the request, so the
        //     reply can't arrive before the handler is wired.
        registerReplyHandler(RegisterIoTDeviceReply.REPLY_ID, this::onRegistered);
        registerTimerHandler(DemoTimer.TIMER_ID, this::onTick);

        // (b) Ask the I²C output protocol for an LCD. No GPIO line for I²C.
        sendRequest(new RegisterIoTDeviceRequest(DeviceType.GROVE_LCD, "demo-lcd"),
                    I2COutputControlProtocol.PROTOCOL_ID);
    }

    private void onRegistered(RegisterIoTDeviceReply r, short from) {
        if (!r.isSuccessful()) { System.exit(1); }       // hardware missing → bail
        this.lcd = r.getDeviceHandle();
        // (c) Now that the device is ready, start the periodic timer that
        //     cycles through messages. The timer handler drives the LCD.
        setupPeriodicTimer(new DemoTimer(), 2000, 2000);
    }

    private void onTick(DemoTimer t, long id) {
        sendRequest(new ShowTextRequest(lcd, nextMessage()),
                    I2COutputControlProtocol.PROTOCOL_ID);
    }

    @Override
    public void execute() throws Exception {
        // (d) Bootstrap: build Babel, load config, instantiate the IoT control
        //     protocol THIS demo needs, register both protocols, init in the
        //     right order, and start the event loop. Notice that this demo
        //     only registers the I²C output protocol — no need to pull in the
        //     digital or input protocols if you don't use them.
        Babel babel = Babel.getInstance();
        Properties props = Babel.loadConfig(new String[0], "paradigmshift.config");

        I2COutputControlProtocol i2cout = new I2COutputControlProtocol();
        babel.registerProtocol(i2cout);
        babel.registerProtocol(this);

        i2cout.init(props);     // sets up its request handlers
        this.init(props);       // sends our first request

        babel.start();          // event loop runs from here on
    }
}
```

The same skeleton — `init` registers handlers and fires a request, the reply
populates a handle, subsequent requests use it — applies to every demo. The
radio demos look identical except they instantiate `LoRaProtocol` /
`ZigBeeProtocol` and skip the handle step (the radio protocol IS the
addressable target).

---

## Adding your own demo

To extend this project with a new demo:

1. **Create a class** anywhere under `src/main/java/`. By convention:
   - NOVA/TaRDIS-derived IoT demos live under `pt.unl.fct.di.tardis.babel.iot.demos`;
   - ParadigmShift originals live under `pt.paradigmshift.iot.demos`.
2. **Implement `BabelDemo`** and extend `GenericProtocol`. Use
   `BabelDemo.PROTO_ID` (`666`) and `BabelDemo.PROTO_NAME` for the protocol
   identity — only one demo runs at a time, so collisions are not a concern.
3. **Write `execute()`** to build Babel, instantiate the control protocols you
   actually need, register them + your demo, init each in dependency order,
   then `babel.start()`.
4. **Add a switch case to `Main.java`** mapping your demo name to
   `new YourDemo()`, plus a usage line in the help text.
5. **Add timers under `events/`** if you need one. Match the convention:
   stateless `ProtoTimer` subclasses whose `clone()` returns `this`. The
   demos all use timer id `666` (`DemoTimer`) or `667` (`RadioSendTimer`);
   pick another small constant if you need more than one timer in one demo.
6. **For new config keys**, add them with sensible defaults using
   `props.getProperty(key, default)` and document them in this README.

You do **not** need to modify the libraries themselves to add a demo. If you
find you need a new device type, a new control-protocol request, or a new
radio extra, that change belongs upstream in `babel-iot-control-protocols` /
`babel-iot-control-api` / the radio protocol — open an issue or PR there.

---

## Distribution

This repository is a set of **demo applications, not a reusable artifact**, so
it is deliberately *not* deployed to the ParadigmShift Maven repository the way
the libraries are. CI builds the executable fat JAR and attaches it as a
workflow artifact; how we publish the demos for download (e.g. the ParadigmShift
website or GitHub Releases) is still to be decided. To get a build today,
either run `mvn package` locally or grab the JAR from the latest CI run.

---

## Credits

This repository contains two bodies of work:

- The **IoT device-control demos** (package `pt.unl.fct.di.tardis.babel.iot.demos`)
  are based on work originally developed at **NOVA School of Science and
  Technology (NOVA FCT)** as part of the **TaRDIS** European project
  ([project page](https://codelab.fct.unl.pt/di/research/tardis/wp6/iot/applications/simple-iot-examples))
  and are now maintained by ParadigmShift.
- The **LoRa and ZigBee radio demos** (package `pt.paradigmshift.iot.demos`) were
  developed by **ParadigmShift, Lda.**

## License

Copyright (c) 2026 ParadigmShift, Lda. See [LICENSE](LICENSE) for full terms,
including the NOVA FCT / TaRDIS attribution that must be retained.
