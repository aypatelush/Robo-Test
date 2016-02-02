package org.usfirst.frc.team2791.helpers;

import org.usfirst.frc.team2791.shakerJoystick.Driver;
import org.usfirst.frc.team2791.subsystems.ShakerDriveTrain;
import static org.usfirst.frc.team2791.robot.Robot.driverJoystick;
import static org.usfirst.frc.team2791.robot.Robot.driveTrain;

/**
 * Created by Akhil on 1/28/2016.
 */
public class DriveHelper extends ShakerHelper {

	public DriveHelper() {

	}

	public void teleopRun() {
		// Reads the current drive type to chooser what layout should be used
		// Tank: Left Stick (Y Dir) and Right Stick (Y Dir)
		// GTA: Left Trigger, Right Trigger, Right Stick (Y Dir)
		// Arcade: Left Stick (Y Dir), Right Stick (X Dir)
		switch (driveTrain.getDriveType()) {
		default:
		case TANK:
			driveTrain.setLeftRight(-driverJoystick.getAxisLeftY(), -driverJoystick.getAxisRightY());
			break;
		case GTA:
			driveTrain.setLeftRight(driverJoystick.getGtaDriveLeft(), driverJoystick.getGtaDriveRight());
			break;
		case ARCADE:
			driveTrain.setLeftRight(-driverJoystick.getAxisLeftY(), -driverJoystick.getAxisRightX());
		case SINGLE_ARCADE:
			driveTrain.setLeftRight(-driverJoystick.getAxisLeftY(), -driverJoystick.getAxisLeftX());
		}
		// Driver button layout
		// RB HIGH GEAR
		// LB LOW GEAR
		if (driverJoystick.getButtonRB())
			driveTrain.setHighGear();
		if (driverJoystick.getButtonLB())
			driveTrain.setLowGear();
		if (driverJoystick.getButtonSel())
			driveTrain.reset();

	}

	public void disableRun() {
		// runs disable methods of subsystems that fall under the driver
		driveTrain.disable();
	}

	public void updateSmartDash() {
		// updateSmartDash the smartDashboard values of subsystems
		driveTrain.updateSmartDash();

	}

	public void reset() {

	}

}
