/*
 * Copyright (C) 2015 Federico Dossena (adolfintel.com).
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.netzarchitekten.upnp;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Android-friendly UPnP Gateway implementation (no org.w3c.dom.traversal).
 */
class Gateway {

    private final Inet4Address iface;
    private final InetAddress routerip;

    private String serviceType = null, controlURL = null;

    public Gateway(byte[] data, Inet4Address ip, InetAddress gatewayip) throws Exception {
        iface = ip;
        routerip = gatewayip;
        String location = null;

        // parse SSDP response headers
        StringTokenizer st = new StringTokenizer(new String(data), "\n");
        while (st.hasMoreTokens()) {
            String s = st.nextToken().trim();
            if (s.isEmpty() || s.startsWith("HTTP/1.") || s.startsWith("NOTIFY *")) {
                continue;
            }
            String name = s.substring(0, s.indexOf(':'));
            String val = s.length() >= name.length() ? s.substring(name.length() + 1).trim() : null;
            if (name.equalsIgnoreCase("location")) {
                location = val;
            }
        }
        if (location == null) {
            throw new Exception("Unsupported Gateway");
        }

        // parse gateway description XML
        HttpURLConnection conn = (HttpURLConnection) new URL(location).openConnection();
        InputStream in = conn.getInputStream();
        parseDescription(in);
        conn.disconnect();

        if (controlURL == null) {
            throw new Exception("Unsupported Gateway");
        }
        int slash = location.indexOf("/", 7); // find first slash after http://
        if (slash == -1) {
            throw new Exception("Unsupported Gateway");
        }
        String base = location.substring(0, slash);
        if (!controlURL.startsWith("/")) {
            controlURL = "/" + controlURL;
        }
        controlURL = base + controlURL;
    }

    /** Parse gateway description XML (find serviceType + controlURL). */
    private void parseDescription(InputStream in) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, null);

        String currentTag = null;
        String serviceTypeFound = null, controlURLFound = null;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = parser.getName();
            } else if (eventType == XmlPullParser.TEXT) {
                String text = parser.getText().trim();
                if (currentTag != null && !text.isEmpty()) {
                    if ("serviceType".equalsIgnoreCase(currentTag)) {
                        serviceTypeFound = text;
                    } else if ("controlURL".equalsIgnoreCase(currentTag)) {
                        controlURLFound = text;
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("service".equalsIgnoreCase(parser.getName())) {
                    if (serviceTypeFound != null && controlURLFound != null) {
                        if (serviceTypeFound.toLowerCase().contains(":wanipconnection:") ||
                                serviceTypeFound.toLowerCase().contains(":wanpppconnection:")) {
                            this.serviceType = serviceTypeFound.trim();
                            this.controlURL = controlURLFound.trim();
                            return;
                        }
                    }
                    serviceTypeFound = controlURLFound = null;
                }
                currentTag = null;
            }
            eventType = parser.next();
        }
    }

    private Map<String, String> command(String action, Map<String, String> params) throws Exception {
        Map<String, String> ret = new HashMap<>();

        StringBuilder soap = new StringBuilder();
        soap.append("<?xml version=\"1.0\"?>")
                .append("<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
                .append("<SOAP-ENV:Body>")
                .append("<m:").append(action).append(" xmlns:m=\"").append(serviceType).append("\">");

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                soap.append("<").append(entry.getKey()).append(">")
                        .append(entry.getValue())
                        .append("</").append(entry.getKey()).append(">");
            }
        }

        soap.append("</m:").append(action).append("></SOAP-ENV:Body></SOAP-ENV:Envelope>");

        byte[] req = soap.toString().getBytes();
        HttpURLConnection conn = (HttpURLConnection) new URL(controlURL).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setRequestProperty("SOAPAction", "\"" + serviceType + "#" + action + "\"");
        conn.setRequestProperty("Connection", "Close");
        conn.setRequestProperty("Content-Length", "" + req.length);
        conn.getOutputStream().write(req);

        // parse SOAP response
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(conn.getInputStream(), null);

        String currentTag = null;
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = parser.getName();
            } else if (eventType == XmlPullParser.TEXT) {
                if (currentTag != null) {
                    ret.put(currentTag, parser.getText());
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                currentTag = null;
            }
            eventType = parser.next();
        }

        conn.disconnect();
        return ret;
    }

    public String getGatewayIP() {
        return routerip.getHostAddress();
    }

    public String getLocalIP() {
        return iface.getHostAddress();
    }

    public String getExternalIP() {
        try {
            Map<String, String> r = command("GetExternalIPAddress", null);
            return r.get("NewExternalIPAddress");
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean openPort(int port, boolean udp, String appName) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
        Map<String, String> params = new HashMap<>();
        params.put("NewRemoteHost", "");
        params.put("NewProtocol", udp ? "UDP" : "TCP");
        params.put("NewInternalClient", iface.getHostAddress());
        params.put("NewExternalPort", "" + port);
        params.put("NewInternalPort", "" + port);
        params.put("NewEnabled", "1");
        params.put("NewPortMappingDescription", appName);
        params.put("NewLeaseDuration", "0");
        try {
            Map<String, String> r = command("AddPortMapping", params);
            return r.get("errorCode") == null;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean closePort(int port, boolean udp) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
        Map<String, String> params = new HashMap<>();
        params.put("NewRemoteHost", "");
        params.put("NewProtocol", udp ? "UDP" : "TCP");
        params.put("NewExternalPort", "" + port);
        try {
            command("DeletePortMapping", params);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isMapped(int port, boolean udp) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
        Map<String, String> params = new HashMap<>();
        params.put("NewRemoteHost", "");
        params.put("NewProtocol", udp ? "UDP" : "TCP");
        params.put("NewExternalPort", "" + port);
        try {
            Map<String, String> r = command("GetSpecificPortMappingEntry", params);
            if (r.get("errorCode") != null) {
                throw new Exception();
            }
            return r.get("NewInternalPort") != null;
        } catch (Exception ex) {
            return false;
        }
    }
}
