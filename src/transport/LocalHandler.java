package transport;

import exceptions.InvalidPacketException;

import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

public class LocalHandler implements PacketListener {
    private final SocketImpl socket;
    private final LinkedBlockingQueue<Packet> packetQueue;
    // All (out of order) received sequence numbers
    private final SortedSet<Integer> received = new TreeSet<>();
    // All (out of order) received packets
    private final HashMap<Integer, Packet> receivedPackets = new HashMap<>();

    public LocalHandler(SocketImpl socket, LinkedBlockingQueue<Packet> packetQueue) {
        this.socket = socket;
        this.packetQueue = packetQueue;
    }

    @Override
    public void onPacketReceived(RawPacket packet) {
        // Syn and SynAck are handled elsewhere.
        if(packet.isSyn()) {
            return;
        }

        // Packets for others are send on elsewhere
        if(!packet.getDestinationIp().equals(socket.getAddress())) {
            return;
        }

        if(packet.isAck()) { // ACK
            socket.gotAck(packet.getSourceIp(), packet.getAcknowledgmentNumber());
        } else if(!packet.isAck()) { // DATA
            if(socket.hasOtherClient(packet.getSourceIp())) {
                received.add(packet.getSequenceNumber());
                receivedPackets.put(packet.getSequenceNumber(), new PacketImpl(packet));

                // Send Ack
                try {
                    socket.send(new RawPacket(RawPacket.ACK_MASK, 0, packet.getSequenceNumber(),
                            (int) System.currentTimeMillis(), socket.getAddress(), packet.getSourceIp()));
                } catch (InvalidPacketException e) {  }

                while(received.size() > 0 && received.first() == socket.lastSequenceNumber(packet.getSourceIp()) + 1) {
                    int packetNo = received.first();
                    received.remove(packetNo);

                    Packet queuePacket = receivedPackets.remove(packetNo);

                    try {
                        packetQueue.put(queuePacket);
                        socket.gotSequenceNumber(queuePacket.getSourceAddress(), packetNo);

                    } catch (InterruptedException e) {  }
                }
            }
        }
    }
}
