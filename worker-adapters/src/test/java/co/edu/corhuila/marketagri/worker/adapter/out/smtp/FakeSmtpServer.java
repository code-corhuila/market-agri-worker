package co.edu.corhuila.marketagri.worker.adapter.out.smtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A minimal SMTP server for the tests, like Mailpit: it answers every command with the usual code,
 * except the replies queued in {@link #refuseNext}, and keeps each message it accepted.
 */
final class FakeSmtpServer implements AutoCloseable {

    final List<String> commands = Collections.synchronizedList(new ArrayList<>());
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    /** Replies used instead of the normal one for the next RCPT commands, in order. */
    final List<String> refuseNext = Collections.synchronizedList(new ArrayList<>());
    private final ServerSocket socket;
    private final Thread loop;

    FakeSmtpServer() throws IOException {
        socket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        loop = new Thread(this::serve, "fake-smtp");
        loop.setDaemon(true);
        loop.start();
    }

    int port() {
        return socket.getLocalPort();
    }

    private void serve() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                talk(client);
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void talk(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
        OutputStream out = client.getOutputStream();
        reply(out, "220 fake ESMTP");
        String line;
        while ((line = in.readLine()) != null) {
            commands.add(line);
            String verb = line.split(" ", 2)[0].toUpperCase();
            switch (verb) {
                case "EHLO" -> reply(out, "250-fake\r\n250 8BITMIME");
                case "RCPT" -> reply(out, refuseNext.isEmpty() ? "250 ok" : refuseNext.remove(0));
                case "DATA" -> {
                    reply(out, "354 go on");
                    StringBuilder message = new StringBuilder();
                    for (String data = in.readLine(); data != null && !data.equals("."); data = in.readLine()) {
                        message.append(data).append("\n");
                    }
                    messages.add(message.toString());
                    reply(out, "250 queued");
                }
                case "QUIT" -> {
                    reply(out, "221 bye");
                    return;
                }
                default -> reply(out, "250 ok");
            }
        }
    }

    private static void reply(OutputStream out, String text) throws IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
        try {
            loop.join(2000);        // the port is free only once the accept loop has ended
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
