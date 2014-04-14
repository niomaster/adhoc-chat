package transport_v2;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

public class DataHandler implements PacketListener {
    private static final long MAX_DIFFERENCE = 5;
    private final SocketImpl socket;
    private final LinkedBlockingQueue<Packet> queue;
    private final HashMap<InetAddress, Integer> lastInOrderSequenceNumber = new HashMap<>();
    private final HashMap<InetAddress, SortedSet<RawPacket>> outOfOrderPackets = new HashMap<>();

    public DataHandler(SocketImpl socket, LinkedBlockingQueue<Packet> queue) {
        this.socket = socket;
        this.queue = queue;
    }

    @Override
    public void onPacketReceived(RawPacket packet) {
        if(!packet.getDestinationIp().equals(socket.getIp())) {
            return;
        }

        if(packet.isSyn()) {
            // TODO check if lastInOrderSequenceNumber and outOfOrderPackets contains key packet.getSourceIp()

            // If the sequence number of the SYN is smaller than the last in order sequence number or
            //  there is a packet received out of order with a sequence number more than MAX_DIFFERENCE larger
            //  then the other client is suspected of having restarted.
            if (Util.differenceWithWrapAround(packet.getSequenceNumber(), lastInOrderSequenceNumber.get(packet.getSourceIp())) < -1 ||
                    !outOfOrderPackets.get(packet.getSourceIp()).tailSet(packet).isEmpty() &&
                            Util.differenceWithWrapAround(packet.getSequenceNumber(), outOfOrderPackets.get(packet.getSourceIp()).last().getSequenceNumber()) > MAX_DIFFERENCE) {
                lastInOrderSequenceNumber.put(packet.getSourceIp(), packet.getSequenceNumber());
                outOfOrderPackets.put(packet.getSourceIp(), new TreeSet<RawPacket>());
                // TODO clear sentButNoAck
            } else {
                outOfOrderPackets.get(packet.getSourceIp()).add(packet);
            }
        } else if (!packet.isAck() && !packet.isAnnounce()) { // TODO misschien betere/andere check
            // data packet
            outOfOrderPackets.get(packet.getSourceIp()).add(packet);
        }

        Iterator<RawPacket> it = outOfOrderPackets.get(packet.getSourceIp()).iterator();
        while (it.hasNext()) {
            RawPacket cur = it.next();
            if (cur.getSequenceNumber() == lastInOrderSequenceNumber.get(packet.getSourceIp()) +1) {
                queue.add(new PacketImpl(cur));
                lastInOrderSequenceNumber.put(packet.getSourceIp(), cur.getSequenceNumber());
                it.remove();
            } else {
                break;
            }
        }
    }
}