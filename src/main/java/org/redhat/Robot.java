package org.redhat;

public class Robot {
    String name;
    String operation;
    int count = 0;
    boolean Disconnected = false;
    boolean status = false;

    public int getCount() {
        return count;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
        count++;
    }

    @Override
    public String toString() {
        return "Robot [name=" + name + ", operation=" + operation + ", count=" + count + ", Disconnected="
                + Disconnected + ", status=" + status + "]";
    }

    public Robot(String name){
        this.name = name;
    }

    public Robot(String name2, String operation2) {
        this.operation = operation2;
        this.name = name2;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisconnected(boolean b) {
        Disconnected = b;
    }

    public boolean isDisconnected() {
        return Disconnected;
    }

    public void setStatus(boolean b) {
        status = b;
    }
}
