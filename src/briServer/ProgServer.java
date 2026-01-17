package briServer;

import briApi.BriService;

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

    // Auth simple en mémoire (login -> compte)
    private final Map<String, ProgrammerAccount> accounts = new HashMap<>();

    public ProgServer(int port, ServiceRegistry registry) {
        this.port = port;
        this.registry = registry;
        setName("ProgServer");

        // Ajoute tes comptes ici
        accounts.put("YOUR_LOGIN", new ProgrammerAccount("YOUR_PASSWORD", "file:/path/to/compiled/classes/"));
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
        private final Map<String, ProgrammerAccount> accounts;

        ProgHandler(Socket socket, ServiceRegistry registry, Map<String, ProgrammerAccount> accounts) {
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
                out.println("LOGIN <login> <password> | REGISTER <login> <password> <ftpUrl>");

                String login = null;

                // --- Auth ---
                while (true) {
                    String line = in.readLine();
                    if (line == null) return;
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.toUpperCase().startsWith("LOGIN ")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length != 3) {
                            out.println("ERR Usage: LOGIN <login> <password>");
                            continue;
                        }

                        String l = parts[1];
                        String p = parts[2];
                        ProgrammerAccount account = accounts.get(l);
                        if (account == null || !account.password.equals(p)) {
                            out.println("ERR Bad credentials");
                            continue;
                        }

                        login = l;
                        out.println("OK AUTH");
                        out.println("COMMANDS: ADD <serviceName> <className> [<baseUrl>] | UPDATE <serviceName> <className> [<baseUrl>] | FTP <url> | BYE");
                        break;
                    }

                    if (line.toUpperCase().startsWith("REGISTER ")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length != 4) {
                            out.println("ERR Usage: REGISTER <login> <password> <ftpUrl>");
                            continue;
                        }

                        String l = parts[1];
                        String p = parts[2];
                        String ftpUrl = parts[3];
                        if (accounts.containsKey(l)) {
                            out.println("ERR Login already exists");
                            continue;
                        }
                        if (!isValidBaseUrl(ftpUrl)) {
                            out.println("ERR Invalid ftpUrl/baseUrl");
                            continue;
                        }

                        accounts.put(l, new ProgrammerAccount(p, normalizeBaseUrl(ftpUrl)));
                        login = l;
                        out.println("OK REGISTERED");
                        out.println("OK AUTH");
                        out.println("COMMANDS: ADD <serviceName> <className> [<baseUrl>] | UPDATE <serviceName> <className> [<baseUrl>] | FTP <url> | BYE");
                        break;
                    }

                    out.println("ERR Please LOGIN or REGISTER first");
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
                        if (parts.length != 3 && parts.length != 4) {
                            out.println("ERR Usage: " + (isUpdate ? "UPDATE" : "ADD") + " <serviceName> <className> [<baseUrl>]");
                            continue;
                        }

                        String serviceName = parts[1];
                        String className = parts[2];
                        String baseUrl = parts.length == 4 ? parts[3] : null;

                        if (isUpdate && !registry.contains(serviceName)) {
                            out.println("ERR Unknown service: " + serviceName);
                            continue;
                        }

                        // Vérif package = login (obligatoire)
                        if (!className.startsWith(login + ".")) {
                            out.println("ERR Class must be in package '" + login + ".*'");
                            continue;
                        }

                        try {
                            ProgrammerAccount account = accounts.get(login);
                            String effectiveBaseUrl = baseUrl != null ? baseUrl : account.ftpUrl;
                            ServiceDescriptor desc = loadService(serviceName, className, effectiveBaseUrl);
                            ServiceDescriptor old = registry.put(desc);
                            if (old != null) {
                                old.closeLoader();
                            }
                            out.println("OK " + (isUpdate ? "UPDATED" : "ADDED") + " " + serviceName);
                        } catch (Exception e) {
                            out.println("ERR " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                        continue;
                    }

                    if (upper.startsWith("FTP ")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length != 2) {
                            out.println("ERR Usage: FTP <url>");
                            continue;
                        }
                        if (!isValidBaseUrl(parts[1])) {
                            out.println("ERR Invalid ftpUrl/baseUrl");
                            continue;
                        }
                        ProgrammerAccount account = accounts.get(login);
                        account.ftpUrl = normalizeBaseUrl(parts[1]);
                        out.println("OK FTP UPDATED");
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

        private static ServiceDescriptor loadService(String serviceName, String className, String baseUrl) throws Exception {
            URL url = toBaseUrl(baseUrl);
            URLClassLoader loader = new URLClassLoader(new URL[]{url}, ProgServer.class.getClassLoader());
            Class<?> clazz = loader.loadClass(className);

            // Vérif "Thread" obligatoire pour start()
            if (!Thread.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("Service must extend Thread");
            }

            if (!BriService.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("Service must implement BriService");
            }

            // Vérif constructeur public(Socket)
            Constructor<?> ctor = clazz.getDeclaredConstructor(Socket.class);
            if (!Modifier.isPublic(ctor.getModifiers())) {
                throw new IllegalArgumentException("Constructor(Socket) must be public");
            }

            return new ServiceDescriptor(serviceName, className, ctor, loader);
        }

        private static boolean isValidBaseUrl(String baseUrl) {
            try {
                toBaseUrl(baseUrl);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static String normalizeBaseUrl(String baseUrl) {
            if (baseUrl.endsWith("/")) {
                return baseUrl;
            }
            return baseUrl + "/";
        }

        private static URL toBaseUrl(String baseUrl) throws Exception {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            if (baseUrl.contains("://")) {
                return new URL(normalizeBaseUrl(baseUrl));
            }
            Path dir = Path.of(baseUrl).toAbsolutePath();
            return dir.toUri().toURL();
        }
    }

    static class ProgrammerAccount {
        private final String password;
        private String ftpUrl;

        ProgrammerAccount(String password, String ftpUrl) {
            this.password = password;
            this.ftpUrl = ftpUrl;
        }
    }
}
