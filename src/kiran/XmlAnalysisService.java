package kiran;

import briApi.BriService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlAnalysisService extends Thread implements BriService {
    private final Socket socket;

    public XmlAnalysisService(Socket socket) {
        this.socket = socket;
        setName("XmlAnalysisService");
    }

    @Override
    public String getServiceName() {
        return "xml-analyse";
    }

    @Override
    public String getServiceDescription() {
        return "Analyse un fichier XML depuis une URL. Commande: ANALYZE <url> <email>";
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            String line = in.readLine();
            if (line == null) return;
            line = line.trim();
            if (line.isEmpty()) {
                out.println("ERR Empty command");
                return;
            }

            String[] parts = line.split("\\s+", 3);
            if (parts.length != 3 || !"ANALYZE".equalsIgnoreCase(parts[0])) {
                out.println("ERR Usage: ANALYZE <url> <email>");
                return;
            }

            String urlStr = parts[1];
            String email = parts[2];

            byte[] data = new URL(urlStr).openStream().readAllBytes();
            Document doc = parseXml(data);

            AtomicInteger elements = new AtomicInteger();
            AtomicInteger attrs = new AtomicInteger();
            AtomicInteger textNodes = new AtomicInteger();
            countNodes(doc, elements, attrs, textNodes);

            String root = doc.getDocumentElement() != null ? doc.getDocumentElement().getNodeName() : "null";
            String report = "root=" + root
                    + " elements=" + elements.get()
                    + " attrs=" + attrs.get()
                    + " textNodes=" + textNodes.get()
                    + " bytes=" + data.length;

            out.println("MAIL SENT to " + email + " | " + report);
        } catch (Exception e) {
            System.err.println("[XmlAnalysisService] Erreur: " + e.getMessage());
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
                out.println("ERR " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (Exception ignored) {
            }
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static Document parseXml(byte[] data) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(data));
    }

    private static void countNodes(Node node, AtomicInteger elements, AtomicInteger attrs, AtomicInteger textNodes) {
        if (node == null) return;
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            elements.incrementAndGet();
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                attrs.addAndGet(attributes.getLength());
            }
        } else if (node.getNodeType() == Node.TEXT_NODE) {
            String text = node.getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                textNodes.incrementAndGet();
            }
        }

        NodeList children = node.getChildNodes();
        if (children == null) return;
        for (int i = 0; i < children.getLength(); i++) {
            countNodes(children.item(i), elements, attrs, textNodes);
        }
    }
}
