package com.filetransfer.app;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransferService {
    private static final String TAG = "FileTransferService";

    public interface FileReceivedCallback { void onFileReceived(String path); }
    public interface ProgressCallback { void onProgress(int percent); }

    public static void startServer(Context ctx, final FileReceivedCallback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket server = null;
                try {
                    server = new ServerSocket(8988);
                    while (true) {
                        Socket client = server.accept();
                        InputStream is = client.getInputStream();
                        // simple protocol: first line filename length then filename then bytes
                        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!downloads.exists()) downloads.mkdirs();
                        File outFile = new File(downloads, "received_" + System.currentTimeMillis());
                        OutputStream os = null;
                        try {
                            os = new FileOutputStream(outFile);
                            byte[] buf = new byte[8192];
                            int read;
                            while ((read = is.read(buf)) != -1) {
                                os.write(buf, 0, read);
                            }
                            os.flush();
                        } finally {
                            if (os != null) try { os.close(); } catch (Exception e) {}
                        }
                        client.close();
                        if (cb != null) cb.onFileReceived(outFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Server error", e);
                } finally {
                    try { if (server != null) server.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    public static void sendFile(InputStream is, String host, int port, ProgressCallback cb) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        OutputStream os = null;
        try {
            os = socket.getOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int read;
            while ((read = is.read(buf)) != -1) {
                os.write(buf, 0, read);
                total += read;
                if (cb != null) cb.onProgress((int) Math.min(100, total / 1024));
            }
            os.flush();
        } finally {
            try { if (os != null) os.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
        }
    }
}
