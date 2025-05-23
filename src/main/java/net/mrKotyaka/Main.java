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