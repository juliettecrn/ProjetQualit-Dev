package juliette;

import briApi.BriService;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class InversionService extends Thread implements BriService {
    private final Socket socket;

    public InversionService(Socket socket) {
        this.socket = socket;
        setName("InversionService");
    }

    @Override
    public String getServiceName() {
        return "inversion";
    }

    @Override
    public String getServiceDescription() {
        return "Inverse une chaine de caracteres (ligne -> ligne) jusqu'à BYE";
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            out.println("INVERSION READY (tape BYE pour quitter)");

            while (true) {
                String text = in.readLine();
                if (text == null) {
                    // client a fermé
                    return;
                }

                if ("BYE".equalsIgnoreCase(text.trim())) {
                    out.println("BYE");
                    return;
                }

                String reversed = new StringBuilder(text).reverse().toString();
                out.println(reversed);
            }

        } catch (IOException e) {
            System.err.println("[InversionService] Erreur: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
