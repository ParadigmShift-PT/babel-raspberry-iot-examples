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

public class Main {

    public static void main(String args[]) {

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
            System.err.println("Unknown test '" + args[0] + "'");
            System.exit(1);
            break;
        }

        try {
            demo.execute();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
