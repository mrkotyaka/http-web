package net.mrKotyaka;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class Main {
    private static final int port = 9999;
    private static final int threadPool = 64;

    public static void main(String[] args) {
        Server server = new Server(threadPool);

        Handler defaultHandler = (request, response) -> {
            try {
                final var filePath = Path.of(".", "public", request.getPath());
                final var mimeType = Files.probeContentType(filePath);
                final var length = Files.size(filePath);
                response.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                Files.copy(filePath, response);
                response.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        server.addHandler("GET", "/app.js", defaultHandler);
        server.addHandler("GET", "/events.html", defaultHandler);
        server.addHandler("GET", "/events.js", defaultHandler);
        server.addHandler("GET", "/forms.html", defaultHandler);
        server.addHandler("GET", "/index.html", defaultHandler);
        server.addHandler("GET", "/links.html", defaultHandler);
        server.addHandler("GET", "/spring.svg", defaultHandler);
        server.addHandler("GET", "/spring.png", defaultHandler);
        server.addHandler("GET", "/styles.css", defaultHandler);

        server.addHandler("GET", "/classic.html", (request, responseStream) -> {
            try {
                final Path filePath = Path.of(".", "public", "/classic.html");
                final String template = Files.readString(filePath);
                final byte[] content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                responseStream.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + request.getMethod() + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                responseStream.write(content);
                responseStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        server.start(port);
    }
}