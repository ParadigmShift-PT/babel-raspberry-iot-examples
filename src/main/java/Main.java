import pt.unl.fct.di.tardis.babel.iot.demos.BabelAccelerometerLCDDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelAnyGestureMatrixDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelArrowGestureMatrixDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelCardinalGestureMatrixDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelChainableLedsRGBDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelChainableLedsHSBDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelControlableLedChainDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelEncoderMatrixDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelLcdDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelMatrixDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelMatrixDemo2;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelSimpleChainableLedsHSBDemo;
import pt.unl.fct.di.tardis.babel.iot.demos.BabelSimpleChainableLedsRGBDemo;
import pt.paradigmshift.iot.demos.BabelLoRaDemo;
import pt.paradigmshift.iot.demos.BabelZigBeeDemo;

/**
 * Entry point and demo launcher for the whole project. This is the fat JAR's
 * main class.
 *
 * <p>The project ships many small demos, but a Raspberry Pi can only run one at
 * a time (there is a single Babel runtime per JVM, a single Pi4J {@code Context},
 * and the buses are claimed exclusively). So {@code Main} takes exactly one
 * command-line argument — the name of the demo to run — looks it up, and hands
 * off to it.
 *
 * <p>Run it with the demo name, for example:
 * <pre>{@code java -jar babel-raspberry-iot-examples.jar Lcd}</pre>
 * Invoked with no argument (or the wrong number of arguments), it prints the
 * list of available demos and exits. Each {@code case} below maps one of those
 * names to the {@link BabelDemo} that implements it.
 *
 * <p>Each demo knows how to bootstrap its own Babel runtime; {@code Main} just
 * constructs the chosen one and calls {@link BabelDemo#execute()}.
 */
public class Main {

    /**
     * Parses the single demo-name argument, constructs the matching
     * {@link BabelDemo}, and runs it via {@link BabelDemo#execute()}.
     *
     * @param args expects exactly one element: the demo name (see the usage list
     *             printed when the count is wrong)
     */
    public static void main(String args[]) {

        // No demo name (or too many args): print the catalogue of demos and exit.
        if (args.length != 1) {
            System.err.println("This is a simple demo to control different IoT "
                               + "and operations in Babel");
            System.err.println("");
            System.err.println("To start a demo, pass one of the following "
                               + "demo names as an argument.");
            System.err.println("");
            System.err.println("Demo List:");
            System.err.println("");
            System.err.println("LedMatrix -> a simple demo that shows the "
                               + "LED matrix showcasing several images.");
            System.err.println("LedMatrix2 -> a simple demo that shows the "
                    + "LED matrix showcasing several icons and features added "
                    + "by us.");
            System.err.println("Lcd -> a simple demo that shows the LCD text "
                               + "display showcasing several messages.");
            System.err.println("Accel -> a simple demo that periodically "
                               + "requests Accelerometer data"
                               + "and displays it on the LCD.");
            System.err.println("anyGesture -> a simple demo that displays "
                               + "different emojis on the LED matrix based on "
                               + "reactive input from the gesture detector");
            System.err.println("cardinalGesture -> a simple demo that displays "
                               + "different colors on the LED matrix based on "
                               + "reactive input from the gesture detector that "
                               + "matches the provided \"threshold\" "
                               + "(UP, DOWN, LEFT, RIGHT)");
            System.err.println("arrowGesture -> a simple demo that displays "
                    + "different arrows on the LED matrix based on "
                    + "reactive input from the gesture detector that "
                    + "matches the provided \"threshold\" "
                    + "(UP, DOWN, LEFT, RIGHT)");
            System.err.println("ledsRGB -> a simple demo that shows "
                    + "chainable leds changing color in sequence "
                    + "using random colors.");
            System.err.println("ledsHSB -> a simple demo that shows "
                    + "chainable leds changing color using the HSB model"
                    + " in sequence using random colors.");
            System.err.println("simpleLedsRGB -> a simple led chaing demo with RGB "
            		+ " control.");
            System.err.println("simpleLedsHSB -> a simple led chaing demo with HSB "
            		+ " control.");
            System.err.println("lightControl -> a demo that uses a chain of RGB "
            		+ "leds, the gesture movemente detector, the led matrix, and "
            		+ "lcd to allow the use to control turning on and off the lights "
            		+ "and manipulating their refresh rate.");
            System.err.println("");
            System.err.println("Radio demos (ParadigmShift) — run a sender on one "
                    + "Raspberry Pi and a receiver on another:");
            System.err.println("loraSend -> broadcast a counter packet over LoRa "
                    + "every few seconds (requires a Waveshare SX126X HAT).");
            System.err.println("loraReceive -> listen for and print LoRa packets "
                    + "(requires a Waveshare SX126X HAT).");
            System.err.println("zigbeeSend -> broadcast a counter packet over "
                    + "ZigBee every few seconds (requires an Ember EZSP dongle).");
            System.err.println("zigbeeReceive -> listen for and print ZigBee "
                    + "packets (requires an Ember EZSP dongle).");
            System.exit(1);
        }

        BabelDemo demo = null;

        // Map the demo name to its implementation. Grove I²C/digital demos and the
        // ParadigmShift radio demos all implement BabelDemo. The radio demos take a
        // boolean: true = sender, false = receiver, so loraSend/loraReceive (and the
        // ZigBee pair) reuse one class with opposite roles.
        switch (args[0]) {
        case "LedMatrix":
            demo = new BabelMatrixDemo();
            break;
        case "LedMatrix2":
            demo = new BabelMatrixDemo2();
            break;
        case "Lcd":
            demo = new BabelLcdDemo();
            break;
        case "Accel":
            demo = new BabelAccelerometerLCDDemo();
            break;
        case "anyGesture":
            demo = new BabelAnyGestureMatrixDemo();
            break;
        case "cardinalGesture":
            demo = new BabelCardinalGestureMatrixDemo();
            break;
        case "arrowGesture":
        	demo = new BabelArrowGestureMatrixDemo();
        	break;
        case "encoderMatrix":
            demo = new BabelEncoderMatrixDemo();
            break;
        case "ledsRGB":
        	demo = new BabelChainableLedsRGBDemo();
        	break;
        case "ledsHSB":
        	demo = new BabelChainableLedsHSBDemo();
        	break;
        case "simpleLedsRGB":
        	demo = new BabelSimpleChainableLedsRGBDemo();
        	break;
        case "simpleLedsHSB":
        	demo = new BabelSimpleChainableLedsHSBDemo();
        	break;
        case "lightControl":
        	demo = new BabelControlableLedChainDemo();
        	break;
        case "loraSend":
            demo = new BabelLoRaDemo(true);
            break;
        case "loraReceive":
            demo = new BabelLoRaDemo(false);
            break;
        case "zigbeeSend":
            demo = new BabelZigBeeDemo(true);
            break;
        case "zigbeeReceive":
            demo = new BabelZigBeeDemo(false);
            break;
        default:
            // Name didn't match any case above.
            System.err.println("Unknown test '" + args[0] + "'");
            System.exit(1);
            break;
        }

        try {
            // Hand off to the chosen demo: it builds and starts its own Babel
            // runtime and never returns under normal operation (the event loop runs).
            demo.execute();
        } catch (Exception e) {
            // Any startup failure (e.g. missing hardware, bus busy) lands here.
            e.printStackTrace();
            System.exit(1);
        }
    }
}
