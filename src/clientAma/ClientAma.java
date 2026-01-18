package clientAma;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClientAma {

    private static boolean isBlockEnd(String line) {
        if (line == null) return true;
        line = line.trim();
        return line.equals("END")
                || line.startsWith("END ")
                || line.equals("EOF");
    }

    private static boolean isSimpleEnd(String line) {
        if (line == null) return true;
        line = line.trim();
        return line.equals("BYE")
                || line.startsWith("ERR")
                || line.startsWith("OK STORED") // fin PUT
                || line.startsWith("OK USER")   // messagerie
                || line.startsWith("OK SENT")   // messagerie
                || line.startsWith("OK READ")   // messagerie (suivi d’un bloc END)
                || line.startsWith("OK LS")     // filex (suivi d’un bloc END n)
                || line.startsWith("OK GET")    // filex (suivi d’un bloc EOF)
                || line.startsWith("OK PUT")    // filex (ensuite client envoie base64/EOF)
                || line.startsWith("OK ");      // fallback
    }

    private static class Conn implements Closeable {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;

        Conn(String host, int port) throws IOException {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        @Override
        public void close() {
            try { out.close(); } catch (Exception ignored) {}
            try { in.close(); } catch (Exception ignored) {}
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Lit toutes les lignes “déjà envoyées” par le serveur, sans se baser sur ready().
     * On utilise un petit timeout pour vider ce qui est immédiatement disponible.
     */
    private static void drainServerOutput(Conn c, int maxLines) throws IOException {
        int oldTimeout = c.socket.getSoTimeout();
        c.socket.setSoTimeout(120); // petit timeout : on lit ce qui arrive tout de suite
        try {
            for (int i = 0; i < maxLines; i++) {
                try {
                    String l = c.in.readLine();
                    if (l == null) return;
                    System.out.println(l);

                    if (isBlockEnd(l)) return;

                } catch (SocketTimeoutException ste) {
                    return; // plus rien d'immédiat
                }
            }
        } finally {
            c.socket.setSoTimeout(oldTimeout);
        }
    }

    /**
     * Lit la réponse du service après une commande:
     * - lit au moins 1 ligne
     * - si c’est un bloc (LS/READ/GET), lit jusqu’à END/EOF
     */
    private static void readServiceResponse(Conn c) throws IOException {
        String first = c.in.readLine();
        if (first == null) throw new EOFException("Connection closed");
        System.out.println(first);

        String t = first.trim().toUpperCase();

        boolean expectsBlock =
                t.startsWith("OK LS")
                        || t.startsWith("OK READ")
                        || t.startsWith("OK GET");

        if (expectsBlock) {
            while (true) {
                String l = c.in.readLine();
                if (l == null) throw new EOFException("Connection closed");
                System.out.println(l);
                if (isBlockEnd(l)) break;
            }
            return;
        }


        if (t.startsWith("OK PUT")) {
            return;
        }

    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int portAma = 4000;

        try (Scanner sc = new Scanner(System.in)) {

            while (true) {
                try (Conn c = new Conn(host, portAma)) {

                    // welcome
                    System.out.println(c.in.readLine());
                    System.out.println(c.in.readLine());

                    // menu AMA
                    while (true) {
                        System.out.print("ama> ");
                        String cmd = sc.nextLine().trim();
                        if (cmd.isEmpty()) continue;

                        // BYE => fin totale
                        if (cmd.equalsIgnoreCase("BYE")) {
                            c.out.println("BYE");
                            String bye = c.in.readLine();
                            if (bye != null) System.out.println(bye);
                            return;
                        }

                        c.out.println(cmd);

                        // RUN => mode service
                        if (cmd.toUpperCase().startsWith("RUN ")) {
                            String resp = c.in.readLine();
                            if (resp == null) {
                                System.out.println("<< Connexion fermée. Reconnexion...");
                                break;
                            }
                            System.out.println(resp);

                            if (resp.startsWith("ERR")) {
                                continue;
                            }

                            System.out.println(">> Mode service (svc>). Tape BYE si le service le supporte.");

                            drainServerOutput(c, 10);

                            // boucle service
                            while (true) {
                                System.out.print("svc> ");
                                String svcCmd = sc.nextLine();
                                c.out.println(svcCmd);

                                try {
                                    readServiceResponse(c);
                                } catch (SocketException se) {
                                    System.out.println("<< Service a fermé la connexion. Reconnexion...");
                                    break;
                                } catch (EOFException eof) {
                                    System.out.println("<< Connexion fermée. Reconnexion...");
                                    break;
                                }

                                if (svcCmd.toUpperCase().startsWith("PUT ")) {
                                    while (true) {
                                        System.out.print("svc> ");
                                        String dataLine = sc.nextLine();
                                        c.out.println(dataLine);

                                        if ("EOF".equalsIgnoreCase(dataLine.trim())) {
                                            try {
                                                readServiceResponse(c);
                                            } catch (Exception e) {
                                                System.out.println("<< Connexion fermée. Reconnexion...");
                                            }
                                            break;
                                        }
                                    }
                                    continue;
                                }

                                if ("BYE".equalsIgnoreCase(svcCmd.trim())) {
                                    break;
                                }
                            }

                            System.out.println("<< Reconnexion...");
                            break;
                        }

                        String first = c.in.readLine();
                        if (first == null) {
                            System.out.println("<< Connexion fermée. Reconnexion...");
                            break;
                        }
                        System.out.println(first);

                        if (!isSimpleEnd(first)) {
                            while (true) {
                                String more = c.in.readLine();
                                if (more == null) break;
                                System.out.println(more);
                                if (isBlockEnd(more) || more.equals("END")) break;
                            }
                        } else if (first.startsWith("SERVICES ")) {
                            while (true) {
                                String more = c.in.readLine();
                                if (more == null) break;
                                System.out.println(more);
                                if (more.equals("END")) break;
                            }
                        }
                    }

                } catch (IOException e) {
                    System.out.println("<< Impossible de se connecter au serveur AMA: " + e.getMessage());
                    System.out.println("Vérifie que BRiLaunch tourne.");
                    return;
                }
            }

        }
    }
}
