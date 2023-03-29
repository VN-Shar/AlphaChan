package AlphaChan.main.handler;

import arc.util.*;
import arc.util.serialization.Base64Coder;
import mindustry.net.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.function.*;

public class NetworkHandler {

    private static NetworkHandler instance = new NetworkHandler();

    private NetworkHandler() {

    }

    public synchronized static NetworkHandler getInstance() {
        if (instance == null)
            instance = new NetworkHandler();
        return instance;
    }

    public static InputStream download(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
            return connection.getInputStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String downloadContent(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
            return Base64Coder.encodeLines(connection.getInputStream().readAllBytes()).replaceAll("\n", "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void pingServer(String ip, Consumer<Host> listener) {
        UpdatableHandler.run("PING", 0, () -> {
            try {
                String resultIP = ip;
                int port = 6567;
                if (ip.contains(":") && Strings.canParsePositiveInt(ip.split(":")[1])) {
                    resultIP = ip.split(":")[0];
                    port = Strings.parseInt(ip.split(":")[1]);
                }

                DatagramSocket socket = new DatagramSocket();
                socket.send(new DatagramPacket(new byte[] { -2, 1 }, 2, InetAddress.getByName(resultIP), port));

                socket.setSoTimeout(2000);

                DatagramPacket packet = new DatagramPacket(new byte[256], 256);

                long start = System.currentTimeMillis();
                socket.receive(packet);

                ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
                listener.accept(readServerData(buffer, ip, (int) (System.currentTimeMillis() - start)));
                socket.disconnect();
                socket.close();
            } catch (Exception e) {
                listener.accept(new Host(0, null, ip, null, 0, 0, 0, null, null, 0, null, null));
            }
        });
    }

    public static Host readServerData(ByteBuffer buffer, String ip, int ping) {
        Host host = NetworkIO.readServerData(ping, ip, buffer);
        host.ping = ping;
        return host;
    }

}
