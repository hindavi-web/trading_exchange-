package com.engine.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SseBroadcaster {
    private final List<OutputStream> clients = new CopyOnWriteArrayList<>();

    public void register(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        clients.add(os);

        // Send initial heartbeat
        sendToStream(os, "connected", "{\"status\":\"ok\"}");
    }

    public void broadcast(String event, String json) {
        String message = "event: " + event + "\ndata: " + json + "\n\n";
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        for (OutputStream os : clients) {
            try {
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                clients.remove(os);
                try { os.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void sendToStream(OutputStream os, String event, String json) {
        try {
            String message = "event: " + event + "\ndata: " + json + "\n\n";
            os.write(message.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            clients.remove(os);
        }
    }

    public int getClientCount() {
        return clients.size();
    }
}
