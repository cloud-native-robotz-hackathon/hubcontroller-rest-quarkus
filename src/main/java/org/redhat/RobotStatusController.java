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

    // add a robot if it doesnt yet exists
    // @returns wether the robot is current disconnected
    public boolean addRobot(String name) {

        Robot robotMatch = findRobotByName(name);
        return addOrUpdate(name, null, robotMatch);
    }

    public boolean addOrUpdateRobot(String name, String operation) {

        Robot robotMatch = findRobotByName(name);
        return addOrUpdate(name, operation, robotMatch);
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

    private boolean addOrUpdate(String name, String operation, Robot robotMatch) {
        if (robotMatch == null) {

            robotMatch = new Robot(name, operation);
            System.out.println("Robot not found, adding --> " + robotMatch);
            robotList.add(robotMatch);
        } else {
            System.out.println("Robot found, updating --> " + robotMatch);
            robotMatch.setOperation(operation);
        }
        return robotMatch.isDisconnected();
    }

    // Find robot by short name from Dashboard
    private Robot findRobotByShortName(String shortId) {
        Robot robotMatch = robotList.stream()
                .filter(robot -> shortId.equals(robot.getName().replace(".", "")))
                .findAny()
                .orElse(null);
        return robotMatch;
    }

}
