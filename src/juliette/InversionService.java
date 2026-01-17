package juliette;

import briApi.BriService;

import java.io.*;
import java.net.Socket;

public class    InversionService extends Thread implements BriService {
    private final Socket socket;

    // OBLIGATOIRE : constructeur public(Socket)
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
        return "Inverse une chaine de caracteres (String -> String)";
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            String text = in.readLine();
            if (text == null) return;

            String reversed = new StringBuilder(text).reverse().toString();
            out.println(reversed);
        } catch (IOException e) {
            System.err.println("[InversionService] Erreur: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
