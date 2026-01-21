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
            System.out.println("Registering new robot --> " + newRobot);
            robotList.add(newRobot);
            return true;
        }
        System.out.println("Robot already registered --> " + robotMatch);
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
            System.err.println("Robot not registered: " + name);
            throw new IllegalArgumentException("Robot not registered: " + name);
        }
        System.out.println("Robot found, updating --> " + robotMatch);
        robotMatch.setOperation(operation);
        return robotMatch.isDisconnected();
    }

    // Check if robot is disconnected without updating operations
    // @returns true if robot is disconnected, false otherwise
    // @throws IllegalArgumentException if robot doesn't exist
    public boolean isRobotDisconnected(String name) {
        Robot robotMatch = findRobotByName(name);
        if (robotMatch == null) {
            System.err.println("Robot not registered: " + name);
            throw new IllegalArgumentException("Robot not registered: " + name);
        }
        return robotMatch.isDisconnected();
    }

    // disconnect robot from user rest calls by shortid from Dashboard
    public boolean disconnectRobot(String shortId) {

        Robot robotMatch = findRobotByShortName(shortId);

        if (robotMatch == null) {
            System.err.println("Trying to disconnect unknown robot -> " + shortId);
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
            System.err.println("Robot not found");
        else
            robotMatch.setStatus(status);

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
