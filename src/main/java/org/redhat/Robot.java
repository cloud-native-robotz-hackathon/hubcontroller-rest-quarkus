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
    String initStatusVerbose; // detailed error message for initialization status (shown on hover)
    String skupperState = "Skupper"; // Skupper connection state: Skupper, Token Request, Secret Cert Created, Cert retrieved
    transient String caCert;     // CA certificate (PEM) for the robot's MicroShift API (not serialized to dashboard)
    transient String clientCert;  // client certificate (PEM) for authenticating to the robot's MicroShift (not serialized to dashboard)
    transient String clientKey;   // client private key (PEM) for authenticating to the robot's MicroShift (not serialized to dashboard)

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
                + isDisconnected + ", status=" + status + ", initStatus=" + initStatus + ", initStatusVerbose=" + initStatusVerbose + ", skupperState=" + skupperState + "]";
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

    // get initialization status verbose (detailed error message)
    public String getInitStatusVerbose() {
        return initStatusVerbose;
    }

    // set initialization status verbose (detailed error message)
    public void setInitStatusVerbose(String initStatusVerbose) {
        this.initStatusVerbose = initStatusVerbose;
    }

    // get skupper state
    public String getSkupperState() {
        return skupperState;
    }

    // set skupper state
    public void setSkupperState(String skupperState) {
        this.skupperState = skupperState;
    }

    public String getCaCert() {
        return caCert;
    }

    public void setCaCert(String caCert) {
        this.caCert = caCert;
    }

    public String getClientCert() {
        return clientCert;
    }

    public void setClientCert(String clientCert) {
        this.clientCert = clientCert;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

}
