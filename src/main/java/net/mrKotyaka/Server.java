package net.mrKotyaka;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final ExecutorService threadPool;
    private static Socket socket;
    private final ConcurrentHashMap<String, Map<String, Handler>> handlers;
    public static final String GET = "GET";
    public static final String POST = "POST";
    final List<String> methods = List.of(GET, POST);

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
                final var in = new BufferedInputStream(socket.getInputStream());
                final var out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            final var limit = 4096;
            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);

            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);

            if (requestLineEnd == -1) {
                badRequest(out);
                socket.close();
                return;
            }

            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                socket.close();
                return;
            }

            final var method = requestLine[0];
            if (!methods.contains(method)) {
                badRequest(out);
                socket.close();
                return;
            }

            System.out.println("method - " + method);

            final var path = requestLine[1];
            if (!path.startsWith("/")) {
                badRequest(out);
                return;
            }
            System.out.println("path  - " + path);

            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                badRequest(out);
                socket.close();
                return;
            }
            in.reset();
            in.skip(headersStart);
            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            System.out.println("headers - " + headers);

            List<NameValuePair> paramsBody = new ArrayList<>();
            if (!method.equals(GET)) {
                String body;
                in.skip(headersDelimiter.length);
                final var contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    final var bodyBytes = in.readNBytes(length);
                    body = new String(bodyBytes);
                    paramsBody = URLEncodedUtils.parse(body, StandardCharsets.UTF_8);
                    System.out.println("body = " + body);
                }
            }
            List<NameValuePair> paramsQuery = URLEncodedUtils.parse(URI.create(requestLine[1]), StandardCharsets.UTF_8);

            Request request = new Request(method, path, paramsQuery, paramsBody);
            System.out.println("request");
            Map<String, Handler> handlerMap = handlers.get(request.getMethod());
            String requestPath = request.getPath().split("\\?")[0];
            if (handlerMap != null && handlerMap.containsKey(requestPath)) {
                Handler handler = handlerMap.get(requestPath);
                handler.handle(request, out);
                System.out.println("default handlers started");
            } else {
                badRequest(out);
            }
            System.out.println("requestPath - " + requestPath);
            System.out.println(request.printQueryParams());
            System.out.println(request.getQueryParam("value"));
            System.out.println(request.getQueryParam("title"));

            System.out.println("socket.close()");
            socket.close();

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

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.contains(method)) {
            handlers.put(method, new ConcurrentHashMap<>());
        }
        handlers.get(method).put(path, handler);
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }
}