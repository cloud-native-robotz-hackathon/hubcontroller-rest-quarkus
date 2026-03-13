package org.redhat;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

// Tracks and manges the state of the connected robots
@ApplicationScoped
public class RobotStatusController {

    // List connected robots
    List<Robot> robotList = new ArrayList<>();

    // @returns the current list of robots
    public List<Robot> getRobotList() {
        return robotList;
    }

    // Register a new robot - only way to add robots to the system
    // @returns true if robot was registered, false if already exists
    public boolean registerRobot(String name) {
        Robot robotMatch = findRobotByName(name);
        if (robotMatch == null) {
            Robot newRobot = new Robot(name, null);
            System.out.println("[Status] register robot=" + newRobot.getName());
            robotList.add(newRobot);
            return true;
        }
        System.out.println("[Status] robot already registered: " + robotMatch.getName());
        return false;
    }

    // Check if a robot exists
    public boolean robotExists(String name) {
        return findRobotByName(name) != null;
    }

    // Update robot operation - only updates if robot exists
    // @returns true if robot is disconnected, false otherwise
    // @throws IllegalArgumentException if robot doesn't exist
    public boolean updateRobot(String name, String operation) {
        Robot robotMatch = findRobotByName(name);
        if (robotMatch == null) {
            System.out.println("[Status] update failed robot not registered: " + name);
            throw new IllegalArgumentException("Robot not registered: " + name);
        }
        System.out.println("[Status] update robot=" + robotMatch.getName() + " op=" + operation);
        robotMatch.setOperation(operation);
        return robotMatch.isDisconnected();
    }

    // Check if robot is disconnected without updating operations
    // @returns true if robot is disconnected, false otherwise
    // @throws IllegalArgumentException if robot doesn't exist
    public boolean isRobotDisconnected(String name) {
        Robot robotMatch = findRobotByName(name);
        if (robotMatch == null) {
            System.out.println("[Status] update failed robot not registered: " + name);
            throw new IllegalArgumentException("Robot not registered: " + name);
        }
        return robotMatch.isDisconnected();
    }

    // disconnect robot from user rest calls by shortid from Dashboard
    public boolean disconnectRobot(String shortId) {

        Robot robotMatch = findRobotByShortName(shortId);

        if (robotMatch == null) {
            System.out.println("[Status] disconnect unknown id=" + shortId);
            return false;
        } else {
            robotMatch.setDisconnected(!robotMatch.isDisconnected());
            return robotMatch.isDisconnected();
        }

    }

    // set sucessful connection status of robot
    public void setRobotStatus(String name, boolean status) {
        Robot robotMatch = findRobotByName(name);

        if (robotMatch == null)
            System.out.println("[Status] setStatus robot not found");
        else
            robotMatch.setStatus(status);

    }

    // set initialization status of robot
    // @returns true if status was set, false if robot not found
    public boolean setRobotInitStatus(String name, String initStatus, String initStatusVerbose) {
        Robot robotMatch = findRobotByName(name);

        if (robotMatch == null) {
            System.out.println("[Status] initStatus robot not found: " + name);
            return false;
        }
        
        robotMatch.setInitStatus(initStatus);
        robotMatch.setInitStatusVerbose(initStatusVerbose);
        System.out.println("[Status] initStatus robot=" + name + " status=" + initStatus);
        return true;
    }

    // set skupper state of robot
    // @returns true if state was set, false if robot not found
    public boolean setRobotSkupperState(String name, String skupperState) {
        Robot robotMatch = findRobotByName(name);

        if (robotMatch == null) {
            System.out.println("[Status] skupperState robot not found: " + name);
            return false;
        }
        
        robotMatch.setSkupperState(skupperState);
        System.out.println("[Status] skupperState robot=" + name + " state=" + skupperState);
        return true;
    }

    // set MicroShift credentials for robot, registering it if not yet known
    // @returns true if credentials were set
    public boolean setRobotCreds(String name, String caCert, String clientCert, String clientKey) {
        Robot robotMatch = findRobotByName(name);

        if (robotMatch == null) {
            registerRobot(name);
            robotMatch = findRobotByName(name);
        }
        
        robotMatch.setCaCert(caCert);
        robotMatch.setClientCert(clientCert);
        robotMatch.setClientKey(clientKey);
        System.out.println("[Status] setRobotCreds robot=" + name);
        return true;
    }

    // find robot by long name
    private Robot findRobotByName(String name) {
        Robot robotMatch = robotList.stream()
                .filter(robot -> name.equals(robot.getName()))
                .findAny()
                .orElse(null);
        return robotMatch;
    }


    // Find robot by short name from Dashboard
    public Robot findRobotByShortName(String shortId) {
        Robot robotMatch = robotList.stream()
                .filter(robot -> shortId.equals(robot.getName().replace(".", "")))
                .findAny()
                .orElse(null);
        return robotMatch;
    }

}
