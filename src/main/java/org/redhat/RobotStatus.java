package org.redhat;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RobotStatus {

    List<Robot> robotList = new ArrayList<>();

    public List<Robot> getRobotList() {
        return robotList;
    }

    public boolean addRobot(String name) {
        Robot robotMatch = robotList.stream()
                .filter(robot -> name.equals(robot.getName()))
                .findAny()
                .orElse(null);

                if (robotMatch == null) {
                    robotList.add(new Robot(name));
                    return true;
                }
                return robotMatch.isDisconnected();


    }

    public boolean addOrUpdateRobot(String name, String operation) {
        Robot robotMatch = robotList.stream()
                .filter(robot -> name.equals(robot.getName()))
                .findAny()
                .orElse(null);

                if (robotMatch == null) {

                    robotMatch = new Robot(name, operation);
                    System.out.println("Robot not found, adding --> " + robotMatch);
                    robotList.add(robotMatch);
                }
                else
                {
                    System.out.println("Robot found --> " + robotMatch);
                    robotMatch.setOperation(operation);
                }
                return robotMatch.isDisconnected();
    }

    public boolean disconnectRobot(String shortId) {
        Robot robotMatch = robotList.stream()
                .filter(robot -> shortId.equals(robot.getName().replace(".", "")))
                .findAny()
                .orElse(null);

                if (robotMatch == null) {
                    System.err.println("Robot not found -> " + shortId);
                    return false;
                }
                else
                {
                    robotMatch.setDisconnected(!robotMatch.isDisconnected());
                    return robotMatch.isDisconnected();
                }

    }

    public void setStatus(String userKey, boolean b) {
        Robot robotMatch = robotList.stream()
        .filter(robot -> userKey.equals(robot.getName()))
        .findAny()
        .orElse(null);

        if (robotMatch == null) {
           System.err.println("Robot not found");
        }
        else
        {
            robotMatch.setStatus(b);

        }
    }

}
