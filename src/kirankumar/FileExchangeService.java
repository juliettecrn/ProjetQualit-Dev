package kirankumar;

import briApi.BriService;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;

public class FileExchangeService extends Thread implements BriService {

    private final Socket socket;

    private static final Path STORAGE_DIR = Paths.get("bri_storage", "kirankumar");

    public FileExchangeService(Socket socket) {
        this.socket = socket;
        setName("FileExchangeService");
    }

    @Override
    public String getServiceName() {
        return "filex";
    }

    @Override
    public String getServiceDescription() {
        return "Service d'echange de fichiers via Base64 (String only): PUT/GET/LS";
    }

    @Override
    public void run() {
        try {
            Files.createDirectories(STORAGE_DIR);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true
            );

            out.println("FILEX READY");
            out.println("COMMANDS: LS | PUT <filename> | GET <filename> | BYE");
            out.println("PUT protocol: after OK, send base64 lines then EOF");
            out.println("GET protocol: server sends OK then base64 lines then EOF");

            while (true) {
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("BYE")) {
                    out.println("BYE");
                    out.flush();
                    break;
                }

                if (line.equalsIgnoreCase("LS")) {
                    listFiles(out);
                    out.flush();
                    continue;
                }

                if (line.toUpperCase().startsWith("PUT ")) {
                    String filename = line.substring(4).trim();
                    handlePut(filename, in, out);
                    out.flush();
                    continue;
                }

                if (line.toUpperCase().startsWith("GET ")) {
                    String filename = line.substring(4).trim();
                    handleGet(filename, out);
                    out.flush();
                    continue;
                }

                out.println("ERR Unknown command");
                out.flush();
            }

        } catch (Exception e) {
            System.err.println("[FileExchangeService] " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void listFiles(PrintWriter out) throws IOException {
        out.println("OK LS");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(STORAGE_DIR)) {
            int count = 0;
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    out.println(p.getFileName().toString());
                    count++;
                }
            }
            out.println("END " + count);
        }
    }

    private void handlePut(String filename, BufferedReader in, PrintWriter out) {
        try {
            Path safePath = resolveSafePath(filename);

            out.println("OK PUT " + safePath.getFileName());
            out.flush();

            StringBuilder b64 = new StringBuilder();

            while (true) {
                String l = in.readLine();
                if (l == null) throw new IOException("Client disconnected during PUT");

                l = l.trim();
                if (l.equals("EOF")) break;

                if (!l.isEmpty()) {
                    b64.append(l);
                }
            }

            byte[] data;
            try {
                data = Base64.getDecoder().decode(b64.toString());
            } catch (IllegalArgumentException ex) {
                out.println("ERR PUT Bad base64");
                out.flush();
                return;
            }

            Files.write(safePath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            out.println("OK STORED " + safePath.getFileName() + " " + data.length + " bytes");
            out.flush();

        } catch (Exception e) {
            out.println("ERR PUT " + e.getMessage());
            out.flush();
        }
    }

    private void handleGet(String filename, PrintWriter out) {
        try {
            Path safePath = resolveSafePath(filename);
            if (!Files.exists(safePath) || !Files.isRegularFile(safePath)) {
                out.println("ERR GET Not found");
                out.flush();
                return;
            }

            byte[] data = Files.readAllBytes(safePath);
            String b64 = Base64.getEncoder().encodeToString(data);

            out.println("OK GET " + safePath.getFileName() + " " + data.length + " bytes");
            out.flush();

            int chunkSize = 76;
            for (int i = 0; i < b64.length(); i += chunkSize) {
                out.println(b64.substring(i, Math.min(i + chunkSize, b64.length())));
            }
            out.println("EOF");
            out.flush();

        } catch (Exception e) {
            out.println("ERR GET " + e.getMessage());
            out.flush();
        }
    }

    private Path resolveSafePath(String filename) throws IOException {
        filename = filename.replace("\\", "/").trim();
        if (filename.contains("..") || filename.startsWith("/") || filename.startsWith("~") || filename.contains(":")) {
            throw new IOException("Invalid filename");
        }
        return STORAGE_DIR.resolve(filename).normalize();
    }
}
