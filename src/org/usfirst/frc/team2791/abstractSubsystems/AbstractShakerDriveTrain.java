package org.usfirst.frc.team2791.abstractSubsystems;

import org.usfirst.frc.team2791.util.BasicPID;
import org.usfirst.frc.team2791.util.Constants;
import org.usfirst.frc.team2791.util.ShakerGyro;
import org.usfirst.frc.team2791.util.Util;
import org.usfirst.frc.team2791.util.ShiftingMath;

import edu.wpi.first.wpilibj.AnalogGyro;
import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.ControllerPower;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.RobotDrive;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.Talon;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.interfaces.Gyro;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public abstract class AbstractShakerDriveTrain extends ShakerSubsystem {
	
	protected double shiftPoint = ShiftingMath.getOptimalShiftPoint();
	protected double lowToHighShiftPoint = ShiftingMath.getLowToHighShiftPoint();
	protected double highToLowShiftPoint = ShiftingMath.getHighToLowShiftPoint();
	
	// use this varaible to keep track of if we're using PID or not
	// if we are using PID we ignore driver input
	protected static boolean usingPID = false;
	// This class is a singleton
	protected static AbstractShakerDriveTrain driveTrainInstance = null;
	
	// We use these PIDs to drive and turn in auton
	protected static BasicPID movingAnglePID;
	protected static BasicPID distancePID;
	protected static BasicPID stationaryAnglePID;
	
	protected static AnalogGyro gyro;
	// protected ShakerGyro gyro;
	// protected ADXRS450_Gyro gyro;
	// protected Shakey_ADXRS450_Gyro gyro;
	
	// These are sensors for the drive train
	protected Encoder leftDriveEncoder;
	protected Encoder rightDriveEncoder;
	
	// Sensor information and information about the robot
	protected static final float WHEEL_DIAMETER = (float) (8.0 / 12.0);
	protected double driveEncoderTicks = 128;
	
	protected double driveTimePIDGoodTime = 0;
	protected double angleTimePIDGoodTime = 0;
	protected double timeSinceLastShift = -10;
	
	protected double previousRate = 0;
	protected double previousRateTime = 0;
	protected double currentRate = 0;
	protected double currentTime = 0;
	
	// these vars are for setting angle PID targets in teleop
	// for the drive train to use in it's run method
	protected double angleTarget = 0.0;
	protected double turnPIDMaxOutput = 0.5;
	protected boolean PIDAtTarget = false;
	protected boolean anglePIDQuickExit = false;
	
	// this should be instantiated by the extending class
	protected RobotDrive robotDrive = null;
	
	protected Timer shiftTimer = new Timer();

	/**
	 * This will be called after the extending class has instantiated all the
	 * sensors. Here we will configure them.
	 */
	public AbstractShakerDriveTrain() {
		// TODO check if any of the sensors we rely on are null
		
		if(robotDrive == null) {
			System.out.println("robotDrive needs to be instantiated by the subclass!");
			// The only reason this is commented out is because is so we don't have to introduce 
//			throw new Exception("robotDrive needs to be instantiated by the subclass!");
		}
		
		leftDriveEncoder.reset();
		rightDriveEncoder.reset();
		leftDriveEncoder.setDistancePerPulse(Util.tickToFeet(driveEncoderTicks, WHEEL_DIAMETER));
		rightDriveEncoder.setDistancePerPulse(-Util.tickToFeet(driveEncoderTicks, WHEEL_DIAMETER));

		// gyro = new Shakey_ADXRS450_Gyro(SPI.Port.kOnboardCS1);
		// gyro = new ADXRS450_Gyro(SPI.Port.kOnboardCS1);
		// gyro = new ShakerGyro(SPI.Port.kOnboardCS1);
		// (new Thread(gyro)).start();
		// this.gyro = new AnalogGyro(new AnalogInput(1));
		// gyro.setSensitivity(0.0007);
		
		movingAnglePID = new BasicPID(Constants.DRIVE_ANGLE_P, Constants.DRIVE_ANGLE_I, Constants.DRIVE_ANGLE_D);
		distancePID = new BasicPID(Constants.DRIVE_DISTANCE_P, Constants.DRIVE_DISTANCE_I, Constants.DRIVE_DISTANCE_D);
		stationaryAnglePID = new BasicPID(Constants.STATIONARY_ANGLE_P, Constants.STATIONARY_ANGLE_I, Constants.STATIONARY_ANGLE_D);
		movingAnglePID.setInvertOutput(true);
		movingAnglePID.setMaxOutput(0.5);
		movingAnglePID.setMinOutput(-0.5);
		
		stationaryAnglePID.setIZone(4);
		distancePID.setIZone(0.25);
		movingAnglePID.setIZone(4);
		shiftTimer.reset();
		shiftTimer.start();
	}

	public abstract GearState getCurrentGear();
	public abstract void setHighGear();
	public abstract void setLowGear();

	public void run() {
		try {
			while (true) {
				// so here if we are using PID update the drive values otherwise
				// do nothing
				if (usingPID) {
					PIDAtTarget = setAngleInternal(angleTarget, turnPIDMaxOutput, anglePIDQuickExit);
				}
				Thread.sleep(updateDelayMs);

			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void updatePIDGains() {
		movingAnglePID.changeGains(Constants.DRIVE_ANGLE_P, Constants.DRIVE_ANGLE_I, Constants.DRIVE_ANGLE_D);
		distancePID.changeGains(Constants.DRIVE_DISTANCE_P, Constants.DRIVE_DISTANCE_I, Constants.DRIVE_DISTANCE_D);
		stationaryAnglePID.changeGains(Constants.STATIONARY_ANGLE_P, Constants.STATIONARY_ANGLE_I, Constants.STATIONARY_ANGLE_D);
	}
	
	public boolean driveInFeet(double distance, double angle, double maxOutput) {
		// uncomment this line if we are debugging
//		updatePIDGains();
		setLowGear();
		distancePID.setSetPoint(distance);
		movingAnglePID.setSetPoint(angle);
		
		distancePID.setMaxOutput(maxOutput);
		distancePID.setMinOutput(-maxOutput);
		movingAnglePID.setMaxOutput(maxOutput / 2);
		movingAnglePID.setMinOutput(-maxOutput / 2);

		double drivePIDOutput = -distancePID.updateAndGetOutput(getAverageDist());
		double anglePIDOutput = movingAnglePID.updateAndGetOutput(getAngle());

		setLeftRightVoltage(drivePIDOutput + anglePIDOutput, drivePIDOutput - anglePIDOutput);
		System.out.println("distError: " + distancePID.getError() + " output: " + drivePIDOutput);
		System.out.println("angleError: " + movingAnglePID.getError() + " output: " + anglePIDOutput);

		if (!(Math.abs(distancePID.getError()) < 0.05) && (Math.abs(movingAnglePID.getError()) < 1.5))
			// Makes sure pid is good error is minimal
			driveTimePIDGoodTime = Timer.getFPGATimestamp();
		else if (Timer.getFPGATimestamp() - driveTimePIDGoodTime > 0.5)
			// then makes sure that certain time has passed to be absolutely
			// positive
			return true;
		return false;

	}

	/**
	 * This method is called in teleop and sets varaibles that are used in the run method
	 * to run our angle PID.
	 * @param angle	The angle you want to robot to turn to 
	 * @param maxOutput The maximum output to use for this turn
	 * @param fastExit If this is true this method returns true when the drive train stop
	 * moving quickly. If this is false the default behavior of returning true if the error is
	 * less than 0.5 for longer than 0.5s.
	 * @return
	 */
	public boolean setAngle(double angle, double maxOutput,  boolean fastExit) {
		usingPID = true;
		turnPIDMaxOutput = maxOutput;
		angleTarget = angle;
		anglePIDQuickExit = fastExit;
		return PIDAtTarget;
	}
	
	public boolean setAngle(double angle, double maxOutput) {
		return setAngle(angle, maxOutput,  false);
	}

	/**
	 * This is the internal 
	 * @param angle
	 * @param maxOutput
	 * @param fastExit
	 * @return
	 */
	public boolean setAngleInternal(double angle, double maxOutput, boolean fastExit) {
		setLowGear();
		stationaryAnglePID.setSetPoint(angle);
		stationaryAnglePID.setMaxOutput(maxOutput);
		stationaryAnglePID.setMinOutput(-maxOutput);
		double anglePIDOutput = stationaryAnglePID.updateAndGetOutput(getAngleEncoder());
		setLeftRightVoltage(anglePIDOutput, -anglePIDOutput);
		
		if(fastExit) {
			return (Math.abs(stationaryAnglePID.getError()) < 0.5) && getEncoderAngleRate() < 1.5;
		}

		if (!(Math.abs(stationaryAnglePID.getError()) < 0.5)) {
			// Makes sure pid is good error is minimal
			angleTimePIDGoodTime = Timer.getFPGATimestamp();
		} else if (Timer.getFPGATimestamp() - angleTimePIDGoodTime > 0.5) {
			// then makes sure that certain time has passed to be absolutely
			// positive
			return true;
		}
		return false;

	}

//	/**
//	 * THIS METHOD IS NOT TESTED!! It's intent is to allow the driver to lock the angle of the drive
//	 * train while driving forwards and backwards.
//	 * @param angle
//	 * @param maxOutput
//	 * @param driving_value
//	 * @return
//	 */
//	public boolean setAngleWithDriving(double angle, double maxOutput, double drivingValue) {
//		usingPID = true;
//		setLowGear();
//		stationaryAnglePID.setInvertOutput(true);
//		stationaryAnglePID.setSetPoint(angle);
//		stationaryAnglePID.setMaxOutput(maxOutput);
//		stationaryAnglePID.setMinOutput(-maxOutput);
//		stationaryAnglePID.setIZone(4);
//		double anglePIDOutput = stationaryAnglePID.updateAndGetOutput(getAngleEncoder());
//		setLeftRightVoltage(anglePIDOutput + drivingValue, -anglePIDOutput + drivingValue);
//
//		// if (!(Math.abs(anglePID.getError()) < 0.5))
//		// // Makes sure pid is good error is minimal
//		// angleTimePIDGoodTime = Timer.getFPGATimestamp();
//		// else if (Timer.getFPGATimestamp() - angleTimePIDGoodTime > 0.5)
//		// // then makes sure that certain time has passed to be absolutely
//		// // positive
//		// return true;
//		// return false;
//		return (Math.abs(stationaryAnglePID.getError()) < 0.5) && getEncoderAngleRate() < 1.5;
//
//	}

	public double getAngleEncoder() {
		return 45.0 * (getLeftDistance() - getRightDistance()) / 2.0;

	}

	public double getEncoderAngleRate() {
		return 45.0 * (leftDriveEncoder.getRate() - rightDriveEncoder.getRate()) / 2.0;

	}

	public void autoShift(boolean isShooterLow) {
		// set our auto shift point a bit higher if the shooter is up this 
		// isn't the ideal way to slow the robot down when the CG is
		// higher but it's okay for now
		double shiftOffset = 0;
		if (!isShooterLow)
			shiftOffset = 1;
		
		if(isHighGear() && Math.abs(getAverageVelocity()) < highToLowShiftPoint + shiftOffset) {
			setLowGear();
		}
		if(!isHighGear() && Math.abs(getAverageVelocity()) > lowToHighShiftPoint + shiftOffset) {
			setHighGear();
		}
	}

	public double getAverageAcceleration() {
		double acceleration = currentRate - previousRate;
		acceleration /= (currentTime - previousRateTime);
		previousRate = currentRate;
		previousRateTime = currentTime;
		currentRate = getAverageVelocity();
		currentTime = Timer.getFPGATimestamp();
		return acceleration;
	}

	public void reset() {
		this.disable();
		this.setLowGear();
		this.rightDriveEncoder.reset();
		this.leftDriveEncoder.reset();
	}

	public void updateSmartDash() {
		// put values on the smart dashboard
		SmartDashboard.putNumber("Gear : ", isHighGear() ? 2 : 1);
		Constants.STATIONARY_ANGLE_P = SmartDashboard.getNumber("Stat Angle P");
		Constants.STATIONARY_ANGLE_I = SmartDashboard.getNumber("Stat Angle I");
		Constants.STATIONARY_ANGLE_D = SmartDashboard.getNumber("Stat Angle D");

		Constants.DRIVE_ANGLE_P = SmartDashboard.getNumber("Angle P");
		Constants.DRIVE_ANGLE_I = SmartDashboard.getNumber("Angle I");
		Constants.DRIVE_ANGLE_D = SmartDashboard.getNumber("Angle D");

		Constants.DRIVE_DISTANCE_P = SmartDashboard.getNumber("DISTANCE P");
		Constants.DRIVE_DISTANCE_I = SmartDashboard.getNumber("DISTANCE I");
		Constants.DRIVE_DISTANCE_D = SmartDashboard.getNumber("Distance D");
		
		updatePIDGains();
	}

	public void debug() {
		SmartDashboard.putNumber("Left Drive Encoders Rate", leftDriveEncoder.getRate());
		SmartDashboard.putNumber("Right Drive Encoders Rate", rightDriveEncoder.getRate());
		SmartDashboard.putNumber("Encoder Angle", getAngleEncoder());
		SmartDashboard.putNumber("Encoder Angle Rate Change", getEncoderAngleRate());
		SmartDashboard.putNumber("Angle PID Error", stationaryAnglePID.getError());
		SmartDashboard.putNumber("Angle PID Output", stationaryAnglePID.getOutput());
		SmartDashboard.putNumber("Average Encoder Distance", getAverageDist());
		SmartDashboard.putNumber("Left Encoder Distance", getLeftDistance());
		SmartDashboard.putNumber("Right Encoder Distance", getRightDistance());
		SmartDashboard.putNumber("Distance PID output", distancePID.getOutput());
		SmartDashboard.putNumber("Distance PID error", distancePID.getError());
	}

	public void disable() {
		// Stops all the motors
		robotDrive.stopMotor();
	}

	public void setLeftRight(double left, double right) {
		robotDrive.setLeftRightMotorOutputs(left, right);
	}

	public void setToggledLeftRight(double left, double right) {
		// sets the left and right motors
		if (!usingPID)
			setLeftRight(left, right);
	}

	public void setLeftRightVoltage(double leftVoltage, double rightVoltage) {
		leftVoltage *= 12;
		rightVoltage *= 12;
		leftVoltage /= ControllerPower.getInputVoltage();
		rightVoltage /= ControllerPower.getInputVoltage();
		setLeftRight(leftVoltage, rightVoltage);
	}

	public void setArcadeDrive(double left, double right) {
		// Set values for the Arcade drive
		robotDrive.arcadeDrive(left, right);
	}

	public boolean isHighGear() {
		// check the gear state, whether it is high or low
		switch (getCurrentGear()) {
		case HIGH:
			return true;
		default:
		case LOW:
			return false;
		}
	}

	public void resetEncoders() {
		// zero the encoders
		leftDriveEncoder.reset();
		rightDriveEncoder.reset();
	}

	public double getLeftDistance() {
		// distance of left encoder
		return leftDriveEncoder.getDistance();// convert distance from feet
		// to inches;
	}

	public double getAngle() {
		return getAngleEncoder();

	}

	public double getRightDistance() {
		// distance of right encoder
		return rightDriveEncoder.getDistance();
	}

	public boolean isUsingPID() {
		return usingPID;
	}

	public void usePID() {
		usingPID = true;
	}

	public void doneUsingPID() {
		usingPID = false;
	}

	// public void resetGyro() {
	// // zero the gyro
	// gyro.reset();
	// }
	//
	// public double getGyroRate() {
	// // recalibrate the gyro for
	// return gyro.getRate();
	// }
	//
	// public double getAngle() {
	// // Get the current gyro angle
	// // return gyro.getAngle();
	// return getAngleEncoder();
	// }

	public double getLeftVelocity() {
		return leftDriveEncoder.getRate();
	}

	public double getRightVelocity() {
		return rightDriveEncoder.getRate();
	}

	public double getAverageVelocity() {
		// average of both encoder velocities
		return (getLeftVelocity() + getRightVelocity()) / 2;
	}

	public double getAverageDist() {
		// average distance of both encoders
		return (getLeftDistance() + getRightDistance()) / 2;
	}

	public void calibrateGyro() {
		// recalibrate the gyro
		System.out.println("Gyro calibrating");
		gyro.calibrate();
		System.out.println("Done calibrating " + " The current rate is " + gyro.getRate());
	}

	protected enum GearState {
		HIGH, LOW
	}
}