package com.battleship.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** small helpers for discovering this machine's lan address and a free tcp port. */
public final class NetUtil {

    private NetUtil() { }

    /** best-effort guess at this machine's lan ipv4 address (falls back to loopback). */
    public static String getLocalIp() {
        try {
            List<String> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        candidates.add(addr.getHostAddress());
                    }
                }
            }
            return candidates.isEmpty() ? "127.0.0.1" : candidates.get(0);
        } catch (SocketException e) {
            return "127.0.0.1";
        }
    }

    /** asks the os for an available ephemeral tcp port. */
    public static int findFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (Exception e) {
            return 55055;
        }
    }
}
