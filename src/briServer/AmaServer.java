package briServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AmaServer extends Thread {
    private final int port;
    private final ServiceRegistry registry;

    public AmaServer(int port, ServiceRegistry registry) {
        this.port = port;
        this.registry = registry;
        setName("AmaServer");
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            System.out.println("[AmaServer] écoute sur " + port);

            while (true) {
                try {
                    Socket client = ss.accept();
                    System.out.println("[AmaServer] accepted " + client.getRemoteSocketAddress());
                    new AmaHandler(client, registry).start();
                } catch (Exception e) {
                    System.err.println("[AmaServer] accept/handler error: " + e);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("[AmaServer] fatal: " + e);
            e.printStackTrace();
        }
    }

    static class AmaHandler extends Thread {
        private final Socket socket;
        private final ServiceRegistry registry;

        AmaHandler(Socket socket, ServiceRegistry registry) {
            this.socket = socket;
            this.registry = registry;
            setName("AmaHandler-" + socket.getPort());
        }

        @Override
        public void run() {
            System.out.println("[AmaHandler] start " + socket.getRemoteSocketAddress());

            BufferedReader in = null;
            PrintWriter out = null;

            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                out.println("WELCOME AMA");
                out.println("COMMANDS: LIST | RUN <serviceName> | BYE");

                while (true) {
                    String line = in.readLine();
                    if (line == null) {
                        safeClose(socket);
                        System.out.println("[AmaHandler] end (client closed)");
                        return;
                    }

                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.equalsIgnoreCase("BYE")) {
                        out.println("BYE");
                        safeClose(socket);
                        System.out.println("[AmaHandler] end (BYE)");
                        return;
                    }

                    if (line.equalsIgnoreCase("LIST")) {
                        out.println("SERVICES " + registry.all().size());
                        for (ServiceDescriptor d : registry.all()) {
                            out.println("- " + d.serviceName + " (" + d.className + ")");
                        }
                        out.println("END");
                        continue;
                    }

                    if (line.toUpperCase().startsWith("RUN ")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length != 2) {
                            out.println("ERR Usage: RUN <serviceName>");
                            continue;
                        }

                        String serviceName = parts[1];
                        ServiceDescriptor d = registry.get(serviceName);
                        if (d == null) {
                            out.println("ERR Unknown service: " + serviceName);
                            continue;
                        }

                        out.println("OK RUNNING " + serviceName);
                        out.flush();

                        Thread serviceThread = (Thread) d.socketCtor.newInstance(socket);
                        serviceThread.start();

                        System.out.println("[AmaHandler] delegated to service " + serviceName + " then exit handler");
                        return;
                    }

                    out.println("ERR Unknown command");
                }

            } catch (Exception e) {
                System.err.println("[AmaHandler] error: " + e);
                e.printStackTrace();
                safeClose(socket);
            }
        }

        private static void safeClose(Socket s) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }
}