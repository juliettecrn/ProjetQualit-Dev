package briServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

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
            System.out.println("[AmaServer] écoute sur " + port);
            while (true) {
                Socket client = ss.accept();
                new AmaHandler(client, registry).start();
            }
        } catch (IOException e) {
            System.err.println("[AmaServer] Erreur: " + e.getMessage());
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
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
            ) {
                out.println("WELCOME AMA");
                out.println("COMMANDS: LIST | RUN <serviceName> | BYE");

                while (true) {
                    String line = in.readLine();
                    if (line == null) break;
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.equalsIgnoreCase("BYE")) {
                        out.println("BYE");
                        break;
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
                        String serviceName = line.substring(4).trim();
                        ServiceDescriptor d = registry.get(serviceName);
                        if (d == null) {
                            out.println("ERR Unknown service: " + serviceName);
                            continue;
                        }

                        out.println("OK RUNNING " + serviceName);

                        // Délégation: on instancie le service avec la socket
                        // et on le lance, puis on attend sa fin
                        Thread serviceThread = (Thread) d.socketCtor.newInstance(socket);
                        serviceThread.start();
                        serviceThread.join(); // on attend que le service termine
                        return; // le service gère la fermeture éventuelle
                    }

                    out.println("ERR Unknown command");
                }
            } catch (Exception e) {
                // Ne pas spam stacktrace en prod
                System.err.println("[AmaHandler] Erreur: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
