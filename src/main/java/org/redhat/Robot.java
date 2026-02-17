package org.redhat;

import io.quarkus.runtime.annotations.RegisterForReflection;

// Robot object
@RegisterForReflection
public class Robot {

    String name; // robot identifier
    String operation; // current robot operation
    int operationCount = 0; // number of robot operations
    boolean isDisconnected = false; // wether the rest connection forwarding to the roobot is disconnected
    boolean status = false;
    String initStatus; // current initialization status of the robot
    String skupperState = "Skupper"; // Skupper connection state: Skupper, Token Request, Secret Cert Created, Cert retrieved

    public Robot(String name) {
        this.name = name;
    }

    public Robot(String name, String operation) {
        this.operation = operation;
        this.name = name;
    }

    // number of operation calls in the robot
    public int getOperationCount() {
        return operationCount;
    }

    // last operation
    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
        operationCount++;
    }

    @Override
    public String toString() {
        return "Robot [name=" + name + ", operation=" + operation + ", count=" + operationCount + ", isDisconnected="
                + isDisconnected + ", status=" + status + ", initStatus=" + initStatus + ", skupperState=" + skupperState + "]";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // wether the robot is disconnected from user rest calls
    public void setDisconnected(boolean b) {
        isDisconnected = b;
    }

    // wether the robot is disconnected from user rest calls
    public boolean isDisconnected() {
        return isDisconnected;
    }

    // wether there has been a successful connection to the robot
    public void setStatus(boolean b) {
        status = b;
    }

    // get initialization status
    public String getInitStatus() {
        return initStatus;
    }

    // set initialization status
    public void setInitStatus(String initStatus) {
        this.initStatus = initStatus;
    }

    // get skupper state
    public String getSkupperState() {
        return skupperState;
    }

    // set skupper state
    public void setSkupperState(String skupperState) {
        this.skupperState = skupperState;
    }
}
