package pt.isec.common.messages;

import java.net.InetAddress;

/**
 * Immutable DTO for UDP messages handled by the directory service.
 *
 * @param addr   sender address
 * @param port   sender port
 * @param data   payload bytes
 * @param length actual payload length
 */
public record UdpMessage(InetAddress addr, int port, byte[] data, int length) { }
