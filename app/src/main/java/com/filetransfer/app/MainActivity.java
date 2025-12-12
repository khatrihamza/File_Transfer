package com.filetransfer.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import android.os.Environment;
import java.io.File;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener {
    private static final String TAG = "MainActivity";
    private WebView webView;

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    IntentFilter intentFilter = new IntentFilter();

    private static final int REQUEST_CODE_PICK_FILE = 1001;
    private Uri selectedFileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        webView.addJavascriptInterface(new JSBridge(this), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager != null) channel = manager.initialize(this, getMainLooper(), null);

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        receiver = new P2pBroadcastReceiver(manager, channel, this);

        requestPermissionsIfNeeded();
        // start server to accept incoming files
        FileTransferService.startServer(this, new FileTransferService.FileReceivedCallback() {
            @Override
            public void onFileReceived(final String filePath) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("window.onIncoming('" + escapeJs(filePath) + "')", null);
                        toastAndJs("Received: " + filePath);
                    }
                });
            }
        });
    }

    private void requestPermissionsIfNeeded() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[perms.size()]), 200);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }

    @Override
    public void onPeersAvailable(android.net.wifi.p2p.WifiP2pDeviceList peers) {
        final List<WifiP2pDevice> deviceList = new ArrayList<>(peers.getDeviceList());
        StringBuilder sb = new StringBuilder();
        for (WifiP2pDevice d : deviceList) {
            sb.append(d.deviceName).append("::").append(d.deviceAddress).append(";;");
        }
        final String js = "window.onPeers('" + escapeJs(sb.toString()) + "')";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(js, null);
            }
        });
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    public void discoverPeersFromJS() {
        if (manager != null && channel != null) {
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { toastAndJs("Discovery started"); }
                @Override public void onFailure(int reason) { toastAndJs("Discovery failed: " + reason); }
            });
        }
    }

    void toastAndJs(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                webView.evaluateJavascript("window.onStatus('" + escapeJs(msg) + "')", null);
            }
        });
    }

    public void pickFileFromJS() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select file"), REQUEST_CODE_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            toastAndJs("File selected: " + selectedFileUri);
        }
    }

    public void sendFileToPeer(String hostAddress) {
        if (selectedFileUri == null) { toastAndJs("No file selected"); return; }
        // Start async transfer to hostAddress on port 8988
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream is = getContentResolver().openInputStream(selectedFileUri);
                    if (is == null) { toastAndJs("Unable to open file"); return; }
                    FileTransferService.sendFile(is, hostAddress, 8988, new FileTransferService.ProgressCallback() {
                        @Override
                        public void onProgress(int percent) {
                            MainActivity.this.onProgress(percent);
                        }
                    });
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript("window.onTransferDone('sent')", null);
                        }
                    });
                } catch (Exception e) { Log.e(TAG, "sendFile error", e); toastAndJs("Send failed: " + e.getMessage()); }
            }
        }).start();
    }

    void onProgress(final int percent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript("window.onProgress(" + percent + ")", null);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private class JSBridge {
        Context ctx;
        JSBridge(Context c) { ctx = c; }

        @JavascriptInterface
        public void discover() { discoverPeersFromJS(); }

        @JavascriptInterface
        public void pickFile() { pickFileFromJS(); }

        @JavascriptInterface
        public void sendTo(String host) { sendFileToPeer(host); }

        @JavascriptInterface
        public void log(String s) { Log.d("JS", s); }
    }
}
