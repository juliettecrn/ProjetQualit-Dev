package clientProg;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientProg {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5000;

        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
             Scanner sc = new Scanner(System.in)) {

            // Welcome
            System.out.println(in.readLine());
            System.out.println(in.readLine()); // LOGIN ...

            // ---- phase LOGIN (réutilisable) ----
            while (true) {
                System.out.print("login> ");
                String login = sc.nextLine();
                System.out.print("password> ");
                String pwd = sc.nextLine();

                out.println("LOGIN " + login + " " + pwd);
                String resp = in.readLine();
                System.out.println(resp);

                if (resp != null && resp.startsWith("OK")) {
                    String cmds = in.readLine();
                    if (cmds != null) System.out.println(cmds);
                    break;
                }
            }

            // ---- commandes ----
            while (true) {
                System.out.print("prog> ");
                String cmd = sc.nextLine();
                out.println(cmd);

                String resp = in.readLine();
                if (resp == null) break;
                System.out.println(resp);

                // LOGOUT → retour à la phase LOGIN
                if (resp.startsWith("OK LOGOUT")) {
                    System.out.println(in.readLine()); // LOGIN <login> <password>
                    while (true) {
                        System.out.print("login> ");
                        String login = sc.nextLine();
                        System.out.print("password> ");
                        String pwd = sc.nextLine();

                        out.println("LOGIN " + login + " " + pwd);
                        String r = in.readLine();
                        System.out.println(r);

                        if (r != null && r.startsWith("OK")) {
                            String cmds = in.readLine();
                            if (cmds != null) System.out.println(cmds);
                            break;
                        }
                    }
                    continue;
                }

                if ("BYE".equals(resp)) {
                    break;
                }
            }
        }
    }
}
