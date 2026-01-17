package clientAma;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientAma {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 4000; // doit matcher BRiLaunch.PORT_AMA

        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
             Scanner sc = new Scanner(System.in)) {

            // Lire welcome
            System.out.println(in.readLine());
            System.out.println(in.readLine());

            while (true) {
                System.out.print("ama> ");
                String cmd = sc.nextLine();
                out.println(cmd);

                // Le RUN délègue la socket au service : après OK RUNNING,
                // le service va parler directement sur la même socket.
                if (cmd.toUpperCase().startsWith("RUN ")) {
                    System.out.println(in.readLine()); // OK RUNNING ...
                    // Ensuite: interaction service (ici inversion = 1 ligne -> 1 réponse)
                    System.out.print("texte> ");
                    String text = sc.nextLine();
                    out.println(text);
                    System.out.println("retour> " + in.readLine());
                    break;
                }

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                    if (line.equals("END") || line.equals("BYE") || line.startsWith("ERR") || line.startsWith("OK")) {
                        break;
                    }
                }

                if (cmd.equalsIgnoreCase("BYE")) break;
            }
        }
    }
}
