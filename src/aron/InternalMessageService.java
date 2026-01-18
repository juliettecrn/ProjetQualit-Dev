package aron;

import briApi.BriService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InternalMessageService extends Thread implements BriService {
    private static final Map<String, List<String>> MAILBOXES = new ConcurrentHashMap<>();

    private final Socket socket;

    public InternalMessageService(Socket socket) {
        this.socket = socket;
        setName("InternalMessageService");
    }

    @Override
    public String getServiceName() {
        return "messagerie";
    }

    @Override
    public String getServiceDescription() {
        return "Messagerie interne partagee: USER, SEND <dest> <message>, READ";
    }

    @Override
    public void run() {
        String user = null;
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            out.println("MESSAGERIE READY");
            out.println("COMMANDS: USER <pseudo> | SEND <dest> <message> | READ | BYE");

            while (true) {
                String line = in.readLine();
                if (line == null) return;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("BYE")) {
                    out.println("BYE");
                    return;
                }

                if (line.toUpperCase().startsWith("USER ")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length != 2 || parts[1].isEmpty()) {
                        out.println("ERR Usage: USER <pseudo>");
                        continue;
                    }
                    user = parts[1].trim();
                    out.println("OK USER " + user);
                    continue;
                }

                if (user == null) {
                    out.println("ERR Please USER <pseudo> first");
                    continue;
                }

                if (line.toUpperCase().startsWith("SEND ")) {
                    String[] parts = line.split("\\s+", 3);
                    if (parts.length < 3 || parts[1].isEmpty() || parts[2].isEmpty()) {
                        out.println("ERR Usage: SEND <dest> <message>");
                        continue;
                    }
                    String dest = parts[1].trim();
                    String message = parts[2].trim();
                    storeMessage(dest, "FROM " + user + ": " + message);
                    out.println("OK SENT " + dest);
                    continue;
                }

                if (line.equalsIgnoreCase("READ")) {
                    List<String> messages = drainMessages(user);
                    out.println("OK READ " + messages.size());
                    for (String msg : messages) {
                        out.println(msg);
                    }
                    out.println("END");
                    continue;
                }

                out.println("ERR Unknown command");
            }
        } catch (IOException e) {
            System.err.println("[InternalMessageService] " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void storeMessage(String dest, String message) {
        List<String> box = MAILBOXES.computeIfAbsent(dest, k -> Collections.synchronizedList(new ArrayList<>()));
        box.add(message);
    }

    private static List<String> drainMessages(String user) {
        List<String> box = MAILBOXES.computeIfAbsent(user, k -> Collections.synchronizedList(new ArrayList<>()));
        List<String> copy;
        synchronized (box) {
            copy = new ArrayList<>(box);
            box.clear();
        }
        return copy;
    }
}
