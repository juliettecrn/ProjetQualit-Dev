package aron;

import briApi.BriService;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InternalMailService extends Thread implements BriService {
    private static final Map<String, List<String>> MAILBOXES = new ConcurrentHashMap<>();
    private final Socket socket;

    public InternalMailService(Socket socket) {
        this.socket = socket;
        setName("InternalMailService");
    }

    @Override
    public String getServiceName() {
        return "messagerie";
    }

    @Override
    public String getServiceDescription() {
        return "Messagerie interne: SEND <dest> <message> | READ <user>";
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            String line = in.readLine();
            if (line == null) return;
            line = line.trim();
            if (line.isEmpty()) {
                out.println("ERR Empty command");
                return;
            }

            String upper = line.toUpperCase();
            if (upper.startsWith("SEND ")) {
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 3) {
                    out.println("ERR Usage: SEND <dest> <message>");
                    return;
                }
                String dest = parts[1];
                String message = parts[2];
                mailboxFor(dest).add(message);
                out.println("OK SENT");
                return;
            }

            if (upper.startsWith("READ ")) {
                String[] parts = line.split("\\s+", 2);
                if (parts.length != 2) {
                    out.println("ERR Usage: READ <user>");
                    return;
                }
                String user = parts[1];
                List<String> messages = mailboxFor(user);
                if (messages.isEmpty()) {
                    out.println("NO MESSAGES");
                    return;
                }
                out.println(String.join(" | ", new ArrayList<>(messages)));
                messages.clear();
                return;
            }

            out.println("ERR Unknown command");
        } catch (IOException e) {
            System.err.println("[InternalMailService] Erreur: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static List<String> mailboxFor(String user) {
        return MAILBOXES.computeIfAbsent(user, k -> Collections.synchronizedList(new ArrayList<>()));
    }
}
