package briServer;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProgServer extends Thread {
    private final int port;
    private final ServiceRegistry registry;

    // Auth simple en mémoire (login -> mdp)
    private final Map<String, String> accounts = new HashMap<>();

    public ProgServer(int port, ServiceRegistry registry) {
        this.port = port;
        this.registry = registry;
        setName("ProgServer");

        // Ajoute tes comptes ici
        accounts.put("YOUR_LOGIN", "YOUR_PASSWORD");
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("[ProgServer] écoute sur " + port);
            while (true) {
                Socket client = ss.accept();
                new ProgHandler(client, registry, accounts).start();
            }
        } catch (IOException e) {
            System.err.println("[ProgServer] Erreur: " + e.getMessage());
        }
    }

    static class ProgHandler extends Thread {
        private final Socket socket;
        private final ServiceRegistry registry;
        private final Map<String, String> accounts;

        ProgHandler(Socket socket, ServiceRegistry registry, Map<String, String> accounts) {
            this.socket = socket;
            this.registry = registry;
            this.accounts = accounts;
            setName("ProgHandler-" + socket.getPort());
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
            ) {
                out.println("WELCOME PROG");
                out.println("LOGIN <login> <password>");

                String login = null;

                // --- Auth ---
                while (true) {
                    String line = in.readLine();
                    if (line == null) return;
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (!line.toUpperCase().startsWith("LOGIN ")) {
                        out.println("ERR Please LOGIN first");
                        continue;
                    }

                    String[] parts = line.split("\\s+");
                    if (parts.length != 3) {
                        out.println("ERR Usage: LOGIN <login> <password>");
                        continue;
                    }

                    String l = parts[1];
                    String p = parts[2];

                    if (!accounts.containsKey(l) || !accounts.get(l).equals(p)) {
                        out.println("ERR Bad credentials");
                        continue;
                    }

                    login = l;
                    out.println("OK AUTH");
                    out.println("COMMANDS: ADD <serviceName> <className> <classesDir> | UPDATE <serviceName> <className> <classesDir> | BYE");
                    break;
                }

                // --- Commandes ---
                while (true) {
                    String line = in.readLine();
                    if (line == null) return;
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.equalsIgnoreCase("BYE")) {
                        out.println("BYE");
                        return;
                    }

                    String upper = line.toUpperCase();
                    if (upper.startsWith("ADD ") || upper.startsWith("UPDATE ")) {
                        boolean isUpdate = upper.startsWith("UPDATE ");
                        String[] parts = line.split("\\s+");
                        if (parts.length != 4) {
                            out.println("ERR Usage: " + (isUpdate ? "UPDATE" : "ADD") + " <serviceName> <className> <classesDir>");
                            continue;
                        }

                        String serviceName = parts[1];
                        String className = parts[2];
                        String classesDir = parts[3];

                        // Vérif package = login (obligatoire)
                        if (!className.startsWith(login + ".")) {
                            out.println("ERR Class must be in package '" + login + ".*'");
                            continue;
                        }

                        try {
                            ServiceDescriptor desc = loadService(serviceName, className, classesDir);
                            registry.put(desc);
                            out.println("OK " + (isUpdate ? "UPDATED" : "ADDED") + " " + serviceName);
                        } catch (Exception e) {
                            out.println("ERR " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                        continue;
                    }

                    out.println("ERR Unknown command");
                }

            } catch (Exception e) {
                System.err.println("[ProgHandler] Erreur: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private static ServiceDescriptor loadService(String serviceName, String className, String classesDir) throws Exception {
            // classesDir = dossier qui contient la racine des packages compilés
            // ex: /.../out/production/servicesExemples
            Path dir = Path.of(classesDir).toAbsolutePath();
            URL url = dir.toUri().toURL();

            URLClassLoader loader = new URLClassLoader(new URL[]{url}, ProgServer.class.getClassLoader());
            Class<?> clazz = loader.loadClass(className);

            // Vérif "Thread" obligatoire pour start()
            if (!Thread.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("Service must extend Thread");
            }

            // Vérif constructeur public(Socket)
            Constructor<?> ctor = clazz.getDeclaredConstructor(Socket.class);
            if (!Modifier.isPublic(ctor.getModifiers())) {
                throw new IllegalArgumentException("Constructor(Socket) must be public");
            }

            return new ServiceDescriptor(serviceName, className, ctor, loader);
        }
    }
}
