package com.baskar.tvhome;

import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Small LAN web page for uploading a new background from a phone or PC. */
class BackgroundServer extends Thread {
    static final int PORT = 8765;
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final String PAGE = "<!doctype html><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<title>TV Home</title><body style='font-family:sans-serif;max-width:44em;margin:2em auto;padding:0 1em'>"
            + "<h2>Change TV background</h2><input type=file id=f accept='image/*'>"
            + "<div id=e hidden style='margin-top:1em'>"
            + "<canvas id=c width=1920 height=1080 style='width:100%;background:#000;cursor:move;touch-action:none'></canvas>"
            + "<p>Drag the photo to reposition it.</p>"
            + "<label>Zoom <input type=range id=z max=400 style='width:60%;vertical-align:middle'> <span id=v></span></label>"
            + "</div>"
            + "<p><button id=b hidden style='font-size:1em;padding:.5em 1em'>Set as background</button></p>"
            + "<p id=s></p><script>"
            + "const W=1920,H=1080,g=c.getContext('2d');let m,raw,cover,k,ox=0,oy=0,px,py;"
            + "function draw(){const w=m.naturalWidth*k,h=m.naturalHeight*k,"
            + "mx=Math.max(0,(w-W)/2),my=Math.max(0,(h-H)/2);"
            + "ox=Math.min(mx,Math.max(-mx,ox));oy=Math.min(my,Math.max(-my,oy));"
            + "g.fillStyle='#000';g.fillRect(0,0,W,H);g.drawImage(m,(W-w)/2+ox,(H-h)/2+oy,w,h)}"
            + "function zoom(){k=cover*z.value/100;v.textContent=z.value+'%';draw()}"
            + "f.onchange=()=>{const file=f.files[0];if(!file)return;"
            + "raw=null;s.textContent='';e.hidden=true;b.hidden=true;m=new Image();"
            + "m.onload=()=>{const a=W/m.naturalWidth,d=H/m.naturalHeight;cover=Math.max(a,d);"
            + "z.min=Math.round(Math.min(a,d)/cover*100);z.value=100;ox=oy=0;zoom();e.hidden=false;b.hidden=false};"
            + "m.onerror=()=>{raw=file;b.hidden=false;"
            + "s.textContent='This browser cannot preview that photo, so it would be sent without zooming.'};"
            + "m.src=URL.createObjectURL(file)};"
            + "z.oninput=zoom;"
            + "c.onpointerdown=p=>{c.setPointerCapture(p.pointerId);px=p.clientX;py=p.clientY};"
            + "c.onpointermove=p=>{if(!c.hasPointerCapture(p.pointerId))return;const r=W/c.clientWidth;"
            + "ox+=(p.clientX-px)*r;oy+=(p.clientY-py)*r;px=p.clientX;py=p.clientY;draw()};"
            + "b.onclick=async()=>{s.textContent='Uploading...';"
            + "try{const body=raw||await new Promise(d=>c.toBlob(d,'image/jpeg',.92));"
            + "const r=await fetch('/background',{method:'POST',headers:{'X-TV-Home':'1'},body});"
            + "s.textContent=r.ok?'Done. Check your TV.':'Upload failed: '+await r.text()}"
            + "catch(x){s.textContent='Upload failed: '+x}}"
            + "</script>";

    interface Listener {
        void onBackgroundUploaded();
    }

    private final File target;
    private final Listener listener;
    private final ServerSocket socket;

    BackgroundServer(File target, Listener listener) throws IOException {
        this.target = target;
        this.listener = listener;
        socket = new ServerSocket(PORT);
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                client.setSoTimeout(30000);
                handle(client);
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private void handle(Socket client) throws IOException {
        InputStream in = new BufferedInputStream(client.getInputStream());
        OutputStream out = client.getOutputStream();
        String[] request = readLine(in).split(" ");
        int length = 0;
        boolean fromPage = false;
        for (String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
            String header = line.toLowerCase(Locale.ROOT);
            if (header.startsWith("content-length:")) {
                length = Integer.parseInt(header.substring(15).trim());
            } else if (header.startsWith("x-tv-home:")) {
                fromPage = true;
            }
        }
        if (request.length < 2) {
            respond(out, "400 Bad Request", "text/plain", "Bad request");
        } else if (request[0].equals("GET") && request[1].equals("/")) {
            respond(out, "200 OK", "text/html; charset=utf-8", PAGE);
        } else if (request[0].equals("POST") && request[1].equals("/background")) {
            // The custom header stops other websites posting here from a browser on this network.
            if (!fromPage) {
                respond(out, "403 Forbidden", "text/plain", "Use the upload page");
            } else if (length <= 0 || length > MAX_BYTES) {
                respond(out, "413 Payload Too Large", "text/plain", "Image must be under 20 MB");
            } else if (saveImage(in, length)) {
                respond(out, "200 OK", "text/plain", "OK");
                listener.onBackgroundUploaded();
            } else {
                respond(out, "415 Unsupported Media Type", "text/plain", "That file is not a readable image");
            }
        } else {
            respond(out, "404 Not Found", "text/plain", "Not found");
        }
    }

    private boolean saveImage(InputStream in, int length) throws IOException {
        File temp = new File(target.getParent(), target.getName() + ".tmp");
        try (FileOutputStream file = new FileOutputStream(temp)) {
            byte[] buffer = new byte[65536];
            int remaining = length;
            while (remaining > 0) {
                int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("Upload ended early");
                }
                file.write(buffer, 0, read);
                remaining -= read;
            }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(temp.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            temp.delete();
            return false;
        }
        return temp.renameTo(target);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int b = in.read(); b >= 0 && b != '\n'; b = in.read()) {
            if (b != '\r') {
                line.write(b);
            }
            if (line.size() > 8192) {
                throw new IOException("Header too long");
            }
        }
        return line.toString("UTF-8");
    }

    private static void respond(OutputStream out, String status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }
}
