package transport_v2;

import exceptions.InvalidPacketException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/****
 *
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                       Number used once                        |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |     Flags     |   Reserved 1  |   Reserved 2  |   Reserved 3  |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        Sequence Number                        |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                    Acknowledgment Number                      |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                       Source Address                          |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                    Destination Address                        |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                             data                              |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 *  Note that each tick mark represents one bit position.
 */

public class RawPacket implements Comparable<RawPacket> {
    public static final int MIN_SIZE = 24;

    public static final byte ACK_MASK =      0b00000001;
    public static final byte SYN_MASK =      0b00000010;
    public static final byte ANNOUNCE_MASK = 0b00000100;
    // private static final byte ZERO_MASK = 0b10000000; this bit must always be zero because evil stupid java only has signed byte.

    private static final byte[] BROADCAST_ADDRESS = new byte[] { 0, 0, 0, 0 };

    private final int nonce;
    private final byte flags;
    private final byte reserved1 = 0;
    private final byte reserved2 = 0;
    private final byte reserved3 = 0;
    private final int sequenceNumber;
    private int acknowledgmentNumber;
    private byte[] sourceAddress = new byte[4];
    private byte[] destinationAddress = new byte[4];
    private final byte[] data;

    public RawPacket(byte[] packet) throws InvalidPacketException {
        if (packet.length < MIN_SIZE) {
            throw new InvalidPacketException("Packet too small.");
        } else {
            ByteBuffer bb = ByteBuffer.wrap(packet);
            nonce = bb.getInt();
            flags = bb.get();
            if (flags < 0) {
                throw new InvalidPacketException(":(");
            }
            bb.get(); // Reserved 1
            bb.get(); // Reserved 2
            bb.get(); // Reserved 3
            sequenceNumber = bb.getInt();
            acknowledgmentNumber = bb.getInt();

            for (int i = 0; i < 4; i++) {
                sourceAddress[i] = bb.get();
            }
            for (int i = 0; i < 4; i++) {
                destinationAddress[i] = bb.get();
            }

            data = new byte[bb.remaining()];
            while (bb.remaining() > 0) {
                data[data.length - bb.remaining()] = bb.get();
            }
        }
    }

    public RawPacket(int nonce, byte flags, int sequenceNumber, int acknowledgmentNumber,
                     InetAddress sourceAddress, InetAddress destinationAddress) throws InvalidPacketException {
        this(nonce, flags, sequenceNumber, acknowledgmentNumber,
                sourceAddress.getAddress(), destinationAddress.getAddress(), null);
    }

    public RawPacket(int nonce, byte flags, int sequenceNumber, int acknowledgmentNumber,
                     InetAddress sourceAddress, InetAddress destinationAddress,
                     byte[] data) throws InvalidPacketException {
        this(nonce, flags, sequenceNumber, acknowledgmentNumber,
                sourceAddress.getAddress(), destinationAddress.getAddress(), data);
    }

    public RawPacket(int nonce, byte flags, int sequenceNumber, int acknowledgmentNumber,
                     byte[] sourceAddress, byte[] destinationAddress) throws InvalidPacketException {
        this(nonce, flags, sequenceNumber, acknowledgmentNumber, sourceAddress, destinationAddress, null);
    }

    public RawPacket(int nonce, byte flags, int sequenceNumber, int acknowledgmentNumber,
                     byte[] sourceAddress, byte[] destinationAddress, byte[] data) throws InvalidPacketException {
        if (flags < 0) {
            throw new InvalidPacketException(":(");
        } else if (sourceAddress.length != 4 || destinationAddress.length != 4) {
            throw new InvalidPacketException("Source and destination address need to be 4 bytes.");
        } else {
            this.nonce = nonce;
            this.flags = flags;
            this.sequenceNumber = sequenceNumber;
            this.acknowledgmentNumber = acknowledgmentNumber;
            this.sourceAddress = sourceAddress.clone();
            this.destinationAddress = destinationAddress.clone();
            if (data != null) {
                this.data = data.clone();
            } else {
                this.data = new byte[] {};
            }
        }
    }

    public boolean isAck() {
        return (flags & ACK_MASK) > 0;
    }

    public boolean isSyn() {
        return (flags & SYN_MASK) > 0;
    }

    public boolean isAnnounce() {
        return (flags & ANNOUNCE_MASK) > 0;
    }

    public int getNonce() {
        return nonce;
    }

    public byte getFlags() {
        return flags;
    }

    public byte getReserved1() {
        return reserved1;
    }

    public byte getReserved2() {
        return reserved2;
    }

    public byte getReserved3() {
        return reserved3;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getAcknowledgmentNumber() {
        return acknowledgmentNumber;
    }

    public byte[] getSourceAddress() {
        return sourceAddress;
    }

    public InetAddress getSourceIp() {
        try {
            return InetAddress.getByAddress(getSourceAddress());
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public byte[] getDestinationAddress() {
        return destinationAddress;
    }

    public InetAddress getDestinationIp() {
        try {
            return InetAddress.getByAddress(getDestinationAddress());
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getLength());

        buffer.putInt(nonce);
        buffer.put(flags);
        buffer.put((byte) 0); // Reserved 1
        buffer.put((byte) 0); // Reserved 2
        buffer.put((byte) 0); // Reserved 3
        buffer.putInt(sequenceNumber);
        buffer.putInt(acknowledgmentNumber);
        buffer.put(sourceAddress);
        buffer.put(destinationAddress);
        buffer.put(data);

        return buffer.array();
    }

    /**
     * The number of byte of the entire packet.
     * @return the length of the packet
     */
    public int getLength() {
        return MIN_SIZE + data.length;
    }

    @Override
    public int hashCode() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(nonce);
        buffer.put(sourceAddress);
        buffer.rewind();
        return buffer.hashCode();
    }

    public static RawPacket tryParse(byte[] packet) {
        RawPacket result = null;

        try {
            result =  new RawPacket(packet);
        } catch (InvalidPacketException e) { }

        return result;
    }

    public static int newNonce() {
        return (int) System.currentTimeMillis();
    }

    @Override
    public String toString() {
        String result = "";

        result += isAnnounce() ? "ANN" : "";
        result += isSyn() ? "SYN" : "";
        result += isAck() ? "ACK" : "";

        result += (!result.equals("")) ? ": " : "";

        result += "nonce=" + getNonce();
        result += ", seq=" + getSequenceNumber();
        result += ", ack=" + getAcknowledgmentNumber();
        result += ", len=" + data.length;

        result += ", src=" + getSourceIp();
        result += ", dst=" + getDestinationIp();
        result += ", data=" + new String(getData());

        return result;
    }

    public static RawPacket newAnnounce(InetAddress sourceAddress) {
        try {
            return new RawPacket(newNonce(), ANNOUNCE_MASK, 0, 0, sourceAddress.getAddress(), BROADCAST_ADDRESS);
        } catch(InvalidPacketException e) {
            return null;
        }
    }

    public static RawPacket newSynchronization(int sequenceNumber, InetAddress sourceAddress, InetAddress destinationAddress) {
        try {
            return new RawPacket(newNonce(), SYN_MASK, sequenceNumber, 0, sourceAddress, destinationAddress);
        } catch(InvalidPacketException e) {
            return null;
        }
    }

    public static RawPacket newSynAck(int sequenceNumber, int ackNumber, InetAddress sourceAddress, InetAddress destinationAddress) {
        try {
            return new RawPacket(newNonce(), (byte) (SYN_MASK | ACK_MASK), sequenceNumber, ackNumber, sourceAddress, destinationAddress);
        } catch(InvalidPacketException e) {
            return null;
        }
    }

    public static RawPacket newData(int sequenceNumber, InetAddress sourceAddress, InetAddress destinationAddress, byte[] data) {
        try {
            return new RawPacket(newNonce(), (byte) 0, sequenceNumber, 0, sourceAddress, destinationAddress, data);
        } catch(InvalidPacketException e) {
            return null;
        }
    }

    public static RawPacket newAcknowledgement(int acknowledgmentNumber, InetAddress sourceAddress, InetAddress destinationAddress) {
        try {
            return new RawPacket(newNonce(), ACK_MASK, 0, acknowledgmentNumber, sourceAddress, destinationAddress);
        } catch(InvalidPacketException e) {
            return null;
        }
    }

    public RawPacket duplicate() {
        try {
            return new RawPacket(newNonce(), flags, sequenceNumber, acknowledgmentNumber, sourceAddress, destinationAddress, data);
        } catch(InvalidPacketException e) {
            return null;
        }
    }

    @Override
    public int compareTo(RawPacket rawPacket) {
        if(Util.differenceWithWrapAround(getSequenceNumber(), rawPacket.getSequenceNumber()) < 1) {
            return -1;
        } else {
            return 1;
        }
    }
}
