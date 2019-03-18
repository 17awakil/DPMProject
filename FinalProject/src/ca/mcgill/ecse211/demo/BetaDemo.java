package ca.mcgill.ecse211.demo;

import ca.mcgill.ecse211.canhandling.CanColor;
import ca.mcgill.ecse211.canhandling.Claw;
import ca.mcgill.ecse211.localization.LightLocalizer;
import ca.mcgill.ecse211.localization.UltrasonicLocalizer;
import ca.mcgill.ecse211.navigation.Navigation;
import ca.mcgill.ecse211.odometer.Odometer;
import ca.mcgill.ecse211.odometer.OdometerExceptions;
import ca.mcgill.ecse211.odometer.OdometryCorrection;
import ca.mcgill.ecse211.wifi.GameSettings;
import lejos.hardware.Button;
import lejos.hardware.Sound;
import lejos.hardware.ev3.LocalEV3;
import lejos.hardware.lcd.TextLCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.EV3TouchSensor;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.hardware.sensor.SensorModes;
import lejos.robotics.SampleProvider;

public class BetaDemo {
  /**
   * The robot's left motor
   */
  public static final EV3LargeRegulatedMotor LEFT_MOTOR = 
      new EV3LargeRegulatedMotor(LocalEV3.get().getPort("D"));
  /**
   * The robot's right motor
   */
  public static final EV3LargeRegulatedMotor RIGHT_MOTOR =
      new EV3LargeRegulatedMotor(LocalEV3.get().getPort("A"));
  /**
   * The light sensor motor
   */
  public static final EV3LargeRegulatedMotor SENSOR_MOTOR =
      new EV3LargeRegulatedMotor(LocalEV3.get().getPort("C"));
  /**
   * The robot's color-detecting light sensor
   */
  public static final SampleProvider COLOR_SENSOR;
  /**
   * The robot's line-detecting light sensor
   */
  public static final SampleProvider LINE_SENSOR;
  /**
   * The robot's touch sensor to detect heavy cans
   */
  public static final SampleProvider TOUCH_SENSOR;
  /**
   * The robot's front-facing ultrasonic sensor
   */
  public static final SampleProvider US_FRONT;
  /**
   * Represents the radius of each wheel, in cm
   */
  public static final double WHEEL_RAD = 2.18;
  /**
   * Represents half the distance between the wheels, in cm Will need updating
   */
  public static final double TRACK = 13.6;
  /**
   * The offset between the robot turning center and the line sensor in
   * the Y direction, in cm. Note: magnitude only.
   */
  public static final double LINE_OFFSET_Y = 10.5;
  /**
   * The offset between the robot turning center and the line sensor in
   * the X direction, in cm. Note: magnitude only.
   */
  public static final double LINE_OFFSET_X = TRACK/2;
  /**
   * The offset between the robot turning center and the
   * center of where the can should be for measurment
   */
  public static final double CAN_DIST = 7;

  /**
   * The can classifier used by the program
   */
  public static final Claw CLAW = new Claw();

  /**
   * The Odometry correction system for the robot
   */
  public static final OdometryCorrection OC = getOC();

  /**
   * The navigation thread used by the robot
   */
  public static final Navigation NAV = getNav();
  private static Navigation getNav() {
    try {
      return new Navigation(OC);
    } catch (OdometerExceptions e) {
      return null;
    }
  }
  private static OdometryCorrection getOC() {
    try {
      return new OdometryCorrection();
    } catch (OdometerExceptions e) {
      return null;
    }
  }

  static {
    @SuppressWarnings("resource")
    SensorModes lightSensorMode = new EV3ColorSensor(LocalEV3.get().getPort("S1"));
    COLOR_SENSOR = lightSensorMode.getMode("RGB");

    @SuppressWarnings("resource")
    SensorModes lightSensorMode2 = new EV3ColorSensor(LocalEV3.get().getPort("S2"));
    LINE_SENSOR = lightSensorMode2.getMode("Red");

    @SuppressWarnings("resource")
    SensorModes usSensor = new EV3UltrasonicSensor(LocalEV3.get().getPort("S4"));
    US_FRONT = usSensor.getMode("Distance");

    @SuppressWarnings("resource")
    SensorModes touchSensorMode = new EV3TouchSensor(LocalEV3.get().getPort("S3"));
    TOUCH_SENSOR = touchSensorMode.getMode("Touch");
  }
  /**
   * The LCD used to output during the robot's journey
   */
  public static final TextLCD LCD = LocalEV3.get().getTextLCD();


  /**
   * Localizes the robot using US and light,
   * then moves to a specified search area and searches for cans
   * @param args not used
   * @throws OdometerExceptions 
   * @throws InterruptedException
   */
  public static void main(String[] args) throws OdometerExceptions, InterruptedException {
    init();
    localize();
    squareDrive(false);
  }
  
  /**
   * Initializes the robot by getting game settings,
   * starting the odometry thread & the nav thread
   * @throws OdometerExceptions
   */
  private static void init() throws OdometerExceptions {
    GameSettings.init();
    (new Thread(Odometer.getOdometer())).start();
    NAV.start();
    OC.start();
    OC.setOn(false);
  }
  
  /**
   * Localizes the robot, and starts the correction when
   * done
   * @throws OdometerExceptions
   */
  private static void localize() throws OdometerExceptions {
    (new UltrasonicLocalizer()).run();
    (new LightLocalizer(0,0)).run();
    OC.setOn(true);
  }

  /**
   * Drives in a square
   * @param ocOn Whether or not to use correction
   */
  private static void squareDrive(boolean ocOn) {
    OC.setOn(ocOn);
    NAV.travelTo(0, 2);
    NAV.waitUntilDone();
    NAV.travelTo(2,2);
    NAV.waitUntilDone();
    NAV.travelTo(0, 0);
    NAV.waitUntilDone();
    OC.setOn(true);
  }

  /**
   * Calculates the center of the sensor from the position of the
   * robot, denoted as an array
   * @param robot An array of the form {x,y,t} representing the
   * position of the sensor
   * @return
   */
  public static double[] toSensor(double[] robot) {
    double[] result = new double[3];
    if (robot.length == 3) {
      double t = robot[2];
      result[0] = robot[0] 
          + Demo.LINE_OFFSET_X * Math.cos(Math.toRadians(t))
          - Demo.LINE_OFFSET_Y * Math.sin(Math.toRadians(t));
      result[1] = robot[1] 
          - Demo.LINE_OFFSET_X * Math.sin(Math.toRadians(t))
          - Demo.LINE_OFFSET_Y * Math.cos(Math.toRadians(t));
      result[2] = t;
    }
    return result;
  }
}
