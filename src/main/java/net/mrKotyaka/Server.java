package net.mrKotyaka;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");

    private final ExecutorService threadPool;
    private static Socket socket;
    private final ConcurrentHashMap<String, Map<String, Handler>> handlers;

    public Server(int poolSize) {
        this.threadPool = Executors.newFixedThreadPool(poolSize);
        this.handlers = new ConcurrentHashMap<>();
    }

    public void start(int port) {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.submit(() -> newConnect(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private void newConnect(Socket socket) {
        try (
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            final String requestLine = in.readLine();
            final String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                return;
            }

            Request request = new Request(parts);
            Map<String, Handler> handlerMap = handlers.get(request.getMethod());

            if (handlerMap.containsKey(request.getPath())) {
                Handler handler = handlerMap.get(request.getPath());
                handler.handle(request, out);
            } else {
                if (!validPaths.contains(request.getPath())) {
                    badRequest(out);
                } else {
                    response(out, request.getPath());
                }
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }

    private void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    private void response(BufferedOutputStream out, String path) throws IOException {
        final Path filePath = Path.of(".", "public", path);
        final String mimeType = Files.probeContentType(filePath);
        final long length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.contains(method)) {
            handlers.put(method, new HashMap<>());
        }
        handlers.get(method).put(path, handler);
    }
}