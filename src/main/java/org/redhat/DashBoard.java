package org.redhat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/dashboard/{clientId}")
@ApplicationScoped
public class DashBoard {

    @Inject
    RobotStatus robotStatus;

    @Inject
    ObjectMapper mapper;

    private Map<String, Session> sessions = new ConcurrentHashMap<>();

    public record RobotCommand(String name, String command) {

    }

    @OnOpen
    public void onOpen(Session session, @PathParam("clientId") String clientId) {
        sessions.put(clientId, session);
    }

    @OnClose
    public void onClose(Session session, @PathParam("clientId") String clientId) {
        sessions.remove(clientId);
    }

    @OnError
    public void onError(Session session, @PathParam("clientId") String clientId, Throwable throwable) {
        sessions.remove(clientId);
    }

    @Scheduled(every = "5s")
    void push() {
        if (sessions != null) {
            broadcast();
        }
    }

    @Scheduled(every = "11s")
    void check() {


    }


    private void broadcast() {
        sessions.values().forEach(s -> {
            try {
                s.getAsyncRemote().sendObject(mapper.writeValueAsString(robotStatus.getRobotList()), result -> {
                    if (result.getException() != null) {
                        System.out.println("Unable to send message: " + result.getException());
                    }
                });
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
    }

}