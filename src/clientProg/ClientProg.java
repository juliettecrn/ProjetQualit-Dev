package clientProg;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientProg {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5000; // doit matcher BRiLaunch.PORT_PROG

        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
             Scanner sc = new Scanner(System.in)) {

            System.out.println(in.readLine()); // WELCOME PROG
            System.out.println(in.readLine()); // LOGIN ...

            System.out.print("login> ");
            String login = sc.nextLine();
            System.out.print("password> ");
            String pwd = sc.nextLine();

            out.println("LOGIN " + login + " " + pwd);
            System.out.println(in.readLine()); // OK AUTH ou ERR
            String maybeCmds = in.readLine();
            if (maybeCmds != null && maybeCmds.startsWith("COMMANDS")) System.out.println(maybeCmds);

            while (true) {
                System.out.print("prog> ");
                String cmd = sc.nextLine();
                out.println(cmd);
                String resp = in.readLine();
                if (resp == null) break;
                System.out.println(resp);
                if ("BYE".equals(resp)) break;
            }
        }
    }
}
