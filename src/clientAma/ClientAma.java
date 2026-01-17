package clientAma;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClientAma {

    private static boolean looksLikeEnd(String line) {
        if (line == null) return true;
        return line.equals("END")
                || line.startsWith("END ")
                || line.equals("EOF")
                || line.equals("BYE")
                || line.startsWith("ERR")
                || line.startsWith("OK ");
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

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int portAma = 4000;

        try (Scanner sc = new Scanner(System.in)) {

            // boucle globale: on se reconnecte en recréant une Conn neuve
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
                                // connexion déjà tombée, on reconnecte
                                System.out.println("<< Connexion fermée. Reconnexion...");
                                break;
                            }
                            System.out.println(resp);

                            if (resp.startsWith("ERR")) {
                                continue;
                            }

                            System.out.println(">> Mode service (svc>). Tape BYE si le service le supporte.");

                            // boucle service
                            while (true) {
                                System.out.print("svc> ");
                                String svcCmd = sc.nextLine();
                                c.out.println(svcCmd);

                                String line;
                                try {
                                    line = c.in.readLine();
                                } catch (SocketException se) {
                                    System.out.println("<< Service a fermé la connexion. Reconnexion...");
                                    // on sort du mode service puis du menu => reconnexion
                                    line = null;
                                }

                                if (line == null) {
                                    // le service a fermé la socket (ex: inversion one-shot)
                                    break; // sort service
                                }

                                System.out.println(line);

                                // lire bloc éventuel
                                if (!looksLikeEnd(line)) {
                                    while (c.in.ready()) {
                                        String more = c.in.readLine();
                                        if (more == null) break;
                                        System.out.println(more);
                                        if (looksLikeEnd(more)) break;
                                    }
                                }

                                if ("BYE".equalsIgnoreCase(svcCmd)) {
                                    // si le service garde la connexion, on revient au menu AMA
                                    // sinon le prochain readLine() sera null et on reconnectera
                                    break;
                                }
                            }

                            // On arrive ici si service terminé/connexion fermée -> on reconnecte proprement
                            System.out.println("<< Reconnexion...");
                            break; // sort du menu AMA => fermeture Conn => nouvelle Conn
                        }

                        // LIST ou autre commande AMA
                        String line = c.in.readLine();
                        if (line == null) {
                            System.out.println("<< Connexion fermée. Reconnexion...");
                            break;
                        }
                        System.out.println(line);

                        if (!looksLikeEnd(line)) {
                            while (c.in.ready()) {
                                String more = c.in.readLine();
                                if (more == null) break;
                                System.out.println(more);
                                if (looksLikeEnd(more)) break;
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
