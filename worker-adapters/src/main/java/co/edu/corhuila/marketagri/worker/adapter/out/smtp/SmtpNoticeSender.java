package co.edu.corhuila.marketagri.worker.adapter.out.smtp;

import co.edu.corhuila.marketagri.worker.adapter.correlation.Correlation;
import co.edu.corhuila.marketagri.worker.adapter.out.retry.Retry;
import co.edu.corhuila.marketagri.worker.application.port.out.NoticeSender;
import co.edu.corhuila.marketagri.worker.domain.model.Notice;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * The e-mail channel of ADR-016 point 3: plain SMTP (RFC 5321) over a socket, a plain-text UTF-8
 * body in base64. Enough for Mailpit, the SMTP server of every course environment; a provider that
 * needs TLS or a login is another adapter behind the same port. A {@code 4xx} reply or a network
 * error is a {@link Retry.TransientFailure}; a {@code 5xx} reply is final.
 */
public final class SmtpNoticeSender implements NoticeSender {

    private static final Pattern PLAIN_ADDRESS = Pattern.compile("[^\\s<>\"]+@[^\\s<>\"]+");
    private static final String CRLF = "\r\n";
    private static final int BASE64_LINE = 76;

    private final String host;
    private final int port;
    private final String from;
    private final Duration timeout;
    private final Retry retry;

    public SmtpNoticeSender(String host, int port, String from, Duration timeout, Retry retry) {
        this.host = host;
        this.port = port;
        this.from = requireAddress(from);
        this.timeout = timeout;
        this.retry = retry;
    }

    @Override
    public void send(Notice notice) {
        String to = requireAddress(notice.to());
        String message = message(to, notice);
        retry.run("SMTP", () -> {
            deliver(to, message);
            return null;
        });
    }

    private void deliver(String to, String message) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            socket.setSoTimeout((int) timeout.toMillis());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            OutputStream out = socket.getOutputStream();
            expect(in, 220);
            command(out, in, "EHLO market-agri-worker", 250);
            command(out, in, "MAIL FROM:<" + from + ">", 250);
            command(out, in, "RCPT TO:<" + to + ">", 250);
            command(out, in, "DATA", 354);
            command(out, in, message + ".", 250);
            write(out, "QUIT");
        } catch (IOException network) {
            throw new Retry.TransientFailure("SMTP " + host + ":" + port + " " + network.getClass().getSimpleName());
        }
    }

    private String message(String to, Notice notice) {
        String body = notice.body().replace("\r\n", "\n").replace("\n", CRLF);
        String encoded = Base64.getMimeEncoder(BASE64_LINE, CRLF.getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return "From: " + from + CRLF
                + "To: " + to + CRLF
                + "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(notice.subject().getBytes(StandardCharsets.UTF_8)) + "?=" + CRLF
                + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + CRLF
                + "X-Correlation-Id: " + Correlation.current() + CRLF
                + "MIME-Version: 1.0" + CRLF
                + "Content-Type: text/plain; charset=UTF-8" + CRLF
                + "Content-Transfer-Encoding: base64" + CRLF
                + CRLF
                + encoded + CRLF;
    }

    private static void command(OutputStream out, BufferedReader in, String line, int expected) throws IOException {
        write(out, line);
        expect(in, expected);
    }

    private static void write(OutputStream out, String line) throws IOException {
        out.write((line + CRLF).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads one reply, multi-line included ({@code 250-…} … {@code 250 …}), and checks its code. */
    private static void expect(BufferedReader in, int expected) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null || line.length() < 3) {
                throw new IOException("SMTP connection closed");
            }
        } while (line.length() > 3 && line.charAt(3) == '-');
        int code = Integer.parseInt(line.substring(0, 3));
        if (code == expected) {
            return;
        }
        if (code >= 400 && code < 500) {
            throw new Retry.TransientFailure("SMTP replied " + line);
        }
        throw new IllegalStateException("SMTP replied " + line);
    }

    /** An address with a line break or angle brackets could inject a header or a recipient. */
    private static String requireAddress(String address) {
        if (address == null || !PLAIN_ADDRESS.matcher(address).matches()) {
            throw new IllegalArgumentException("not a plain e-mail address");
        }
        return address;
    }
}
