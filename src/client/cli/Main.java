package client.cli;

import transport.Socket;
import transport.SocketImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

    private Socket socket;

    public Main(Socket socket) {
        this.socket = socket;
        socket.broadcast("test".getBytes());
        socket.broadcast(new byte[] {1, 2, 3});
    }

    /**
     * Reads a string from the console and sends this string over the socket-connection to the Peer proces. On Peer.EXIT the method ends
     */
    public void handleTerminalInput() {
        String line = readString("");

        while (!line.equals("exit") & socket.isConnected()) {
            socket.broadcast(line.getBytes());

            line = readString("");
        }

        System.exit(0);
    }

    /** read a line from the default input */
    static public String readString(String text) {
        System.out.print(text);
        String out = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    System.in));
            out = in.readLine();
        } catch (IOException e) {
        }

        return (out == null) ? "" : out;
    }

    public static void main(String[] args) {
        Socket socket;
        try {
            socket = new SocketImpl(1234);
            socket.connect();
            if (socket.isConnected()) {
                new Main(socket);
            } else {
                System.out.println("Could not connect :(");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
