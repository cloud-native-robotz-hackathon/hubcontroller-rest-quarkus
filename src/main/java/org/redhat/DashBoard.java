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

// Backend for the Dashboard Gui, providing updates through Websocket connections
@ServerEndpoint(value = "/dashboard/{clientId}")
@ApplicationScoped
public class DashBoard {

    // Access the status of the robots
    @Inject
    RobotStatusController robotStatus;

    // json mapper
    @Inject
    ObjectMapper mapper;

    // stores the websocket sessions
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(final Session session, @PathParam("clientId") final String clientId) {
        sessions.put(clientId, session);
    }

    @OnClose
    public void onClose(final Session session, @PathParam("clientId") final String clientId) {
        sessions.remove(clientId);
    }

    @OnError
    public void onError(final Session session, @PathParam("clientId") final String clientId,
            final Throwable throwable) {
        sessions.remove(clientId);
    }

    // push updates to dashboard
    @Scheduled(every = "5s")
    void push() {
        if (sessions != null) {
            broadcast();
        }
    }

    // send updates to dashboard as RobotCommand list
    private void broadcast() {
        sessions.values().forEach(s -> {
            try {
                s.getAsyncRemote().sendObject(mapper.writeValueAsString(robotStatus.getRobotList()), result -> {
                    if (result.getException() != null) {
                        System.err.println("Unable to send message: -> " + result.getException());

                    }
                });
            } catch (final JsonProcessingException e) {
                System.err.println("Unable to serialize message ->  " + e);
            }
        });
    }

}