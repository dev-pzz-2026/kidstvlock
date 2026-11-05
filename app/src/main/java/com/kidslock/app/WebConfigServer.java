package com.kidslock.app;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebConfigServer {
    private static final String TAG = "WebConfigServer";
    private static final int PORT = 8080;
    private static WebConfigServer instance;
    
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private int actualPort = PORT;  // 实际绑定的端口，可能不同于默认 PORT
    private ExecutorService executor = Executors.newCachedThreadPool();
    private Context context;
    
    public static synchronized WebConfigServer getInstance(Context context) {
        if (instance == null) {
            instance = new WebConfigServer(context.getApplicationContext());
        }
        return instance;
    }
    
    private WebConfigServer(Context context) {
        this.context = context;
    }
    
    public synchronized void start() {
        // 如果已有有效的 serverSocket（未关闭），直接复用，避免重复绑定导致 EADDRINUSE
        if (serverSocket != null && !serverSocket.isClosed()) {
            running = true;
            Log.i(TAG, "Web server reusing existing serverSocket on port " + actualPort);
            return;
        }
        // 兜底：如果 running 为 true 但 serverSocket 已关闭/无效，重置状态并重新启动
        if (running && (serverSocket == null || serverSocket.isClosed())) {
            Log.w(TAG, "running=true but serverSocket invalid, resetting state");
            running = false;
            actualPort = PORT;
        }
        if (running) {
            Log.i(TAG, "Web server already running on port " + actualPort);
            return;
        }
        // 默认只绑定 8080 端口，不使用多端口轮询
        try {
            serverSocket = new ServerSocket(PORT, 50, java.net.InetAddress.getByName("0.0.0.0"));
            serverSocket.setReuseAddress(true);
            actualPort = PORT;  // 记录实际绑定端口
            running = true;
            Log.i(TAG, "Web server started on port " + actualPort);
        } catch (Exception e) {
            Log.e(TAG, "Port " + PORT + " bind failed", e);
            running = false;
            serverSocket = null;
            return;
        }
        executor.execute(() -> {
            try {
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        executor.execute(new ClientHandler(client));
                    } catch (Exception e) {
                        if (running) {
                            Log.e(TAG, "Accept error, serverSocket may be closed", e);
                            running = false;
                            actualPort = PORT;
                            serverSocket = null;
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Server loop error", e);
                running = false;
                actualPort = PORT;
                serverSocket = null;
            }
        });
    }
    
    /**
     * 查询端口真实监听状态（最高优先级）。
     * 通过尝试连接实际绑定的端口来确认是否真正在监听，不依赖 running 内存标志。
     */
    public synchronized boolean isRunning() {
        // 最高优先级：通过查询端口真实监听状态判断
        if (actualPort <= 0 || actualPort > 65535) {
            running = false;
            serverSocket = null;
            Log.w(TAG, "actualPort invalid, resetting state");
            return false;
        }
        try (java.net.Socket probe = new java.net.Socket()) {
            probe.connect(new java.net.InetSocketAddress("127.0.0.1", actualPort), 500);
            running = true;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Port " + actualPort + " not actually listening, resetting state");
            running = false;
            actualPort = PORT;
            serverSocket = null;
            return false;
        }
    }
    
    public void stop() {
        running = false;
        actualPort = PORT;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Stop error", e);
        }
    }
    
    public String getServerUrl() {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    String ip = Formatter.formatIpAddress(ipInt);
                    return "http://" + ip + ":" + actualPort;
                }
            }
        } catch (Exception e) {
        }
        return "http://127.0.0.1:" + actualPort;
    }
    
    private class ClientHandler implements Runnable {
        private Socket socket;
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                
                String requestLine = br.readLine();
                if (requestLine == null) return;
                String[] parts = requestLine.split(" ");
                String method = parts[0];
                String path = parts.length > 1 ? parts[1] : "/";
                
                // Read headers
                Map<String, String> headers = new HashMap<>();
                String line;
                int contentLength = 0;
                while ((line = br.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(line.split(":")[1].trim());
                        } catch (Exception e) {}
                    }
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        headers.put(line.substring(0, idx).trim().toLowerCase(), line.substring(idx + 1).trim());
                    }
                }
                
                String body = "";
                if (contentLength > 0) {
                    char[] buf = new char[contentLength];
                    br.read(buf, 0, contentLength);
                    body = new String(buf);
                }
                
                if (path.equals("/") || path.startsWith("/index")) {
                    sendResponse(out, 200, "text/html; charset=utf-8", getIndexHtml());
                } else if (path.equals("/api/config") && method.equals("GET")) {
                    sendResponse(out, 200, "application/json; charset=utf-8", getConfigJson());
                } else if (path.equals("/api/config") && method.equals("POST")) {
                    saveConfig(body);
                    sendResponse(out, 200, "application/json; charset=utf-8", "{\"success\":true}");
                } else if (path.equals("/api/banks") && method.equals("GET")) {
                    sendResponse(out, 200, "application/json; charset=utf-8", getBanksJson());
                } else if (path.equals("/api/bank/upload") && method.equals("POST")) {
                    String result = uploadBank(body, headers);
                    sendResponse(out, 200, "application/json; charset=utf-8", result);
                } else if (path.equals("/api/bank/setdefault") && method.equals("POST")) {
                    try {
                        JSONObject json = new JSONObject(body);
                        String bankFile = json.getString("bankFile");
                        PrefManager pref = new PrefManager(context);
                        pref.setCurrentBankFile(bankFile);
                        // 重启计时服务以应用新题库
                        try {
                            Intent svc = new Intent(context, LockService.class);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(svc);
                            } else {
                                context.startService(svc);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "restart LockService failed", e);
                        }
                        sendResponse(out, 200, "application/json; charset=utf-8", "{\"success\":true}");
                    } catch (Exception e) {
                        sendResponse(out, 400, "application/json; charset=utf-8", "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                } else {
                    sendResponse(out, 404, "text/plain", "Not Found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Client error", e);
            } finally {
                try { socket.close(); } catch (Exception e) {}
            }
        }
        
        private void sendResponse(OutputStream out, int code, String contentType, String body) throws Exception {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            bw.write("HTTP/1.1 " + code + " OK\r\n");
            bw.write("Content-Type: " + contentType + "\r\n");
            bw.write("Connection: close\r\n");
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            bw.write("Content-Length: " + bodyBytes.length + "\r\n");
            bw.write("\r\n");
            bw.flush();
            out.write(bodyBytes);
            out.flush();
        }
        
        private String getConfigJson() {
            try {
                PrefManager pref = new PrefManager(context);
                JSONObject json = new JSONObject();
                json.put("watchLimitMinutes", pref.getWatchLimitMinutes());
                json.put("unlockCount", pref.getUnlockCount());
                json.put("lockDurationMinutes", pref.getLockDurationMinutes());
                json.put("lockIntervalMinutes", pref.getLockIntervalMinutes());
                json.put("relockDelaySeconds", pref.getRelockDelaySeconds());
                json.put("autoStart", pref.isAutoStart());
                json.put("currentBankFile", pref.getCurrentBankFile());
                json.put("answerMode", pref.getAnswerMode());
                return json.toString();
            } catch (Exception e) {
                return "{}";
            }
        }
        
        private void saveConfig(String body) {
            try {
                JSONObject json = new JSONObject(body);
                PrefManager pref = new PrefManager(context);
                boolean timerWasActive = pref.isTimerActive();
                int oldMinutes = pref.getWatchLimitMinutes();
                if (json.has("watchLimitMinutes")) pref.setWatchLimitMinutes(json.getInt("watchLimitMinutes"));
                if (json.has("unlockCount")) pref.setUnlockCount(json.getInt("unlockCount"));
                if (json.has("lockDurationMinutes")) pref.setLockDurationMinutes(json.getInt("lockDurationMinutes"));
                if (json.has("lockIntervalMinutes")) pref.setLockIntervalMinutes(json.getInt("lockIntervalMinutes"));
                if (json.has("relockDelaySeconds")) pref.setRelockDelaySeconds(json.getInt("relockDelaySeconds"));
                if (json.has("autoStart")) pref.setAutoStart(json.getBoolean("autoStart"));
                if (json.has("currentBankFile")) pref.setCurrentBankFile(json.getString("currentBankFile"));
                if (json.has("answerMode")) pref.setAnswerMode(json.getString("answerMode"));
                // 如果计时器正在运行且观看时长变化，重新启动计时器
                if (timerWasActive && json.has("watchLimitMinutes")) {
                    int newMinutes = json.getInt("watchLimitMinutes");
                    if (newMinutes != oldMinutes && newMinutes > 0) {
                        pref.startTimer(newMinutes);
                        // 重新设置闹钟后备唤醒，确保闹钟时间与新结束时间一致
                        LockService.rescheduleWakeupAlarm(context);
                    }
                }
                // 重启计时服务以应用新配置
                try {
                    Intent svc = new Intent(context, LockService.class);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(svc);
                    } else {
                        context.startService(svc);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "restart LockService failed", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Save config error", e);
            }
        }
        
        private String getBanksJson() {
            try {
                JSONArray arr = new JSONArray();
                // Assets
                String[] files = context.getAssets().list("");
                if (files != null) {
                    for (String f : files) {
                        if (f.endsWith(".json") && isValidBank(context, f, true)) {
                            JSONObject obj = new JSONObject();
                            obj.put("name", f);
                            obj.put("source", "assets");
                            arr.put(obj);
                        }
                    }
                }
                // Internal files dir
                File dir = context.getFilesDir();
                File[] files2 = dir.listFiles();
                if (files2 != null) {
                    for (File f : files2) {
                        if (f.getName().endsWith(".json") && isValidBankFile(f)) {
                            JSONObject obj = new JSONObject();
                            obj.put("name", f.getName());
                            obj.put("source", "internal");
                            arr.put(obj);
                        }
                    }
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }
        
         private boolean isValidBank(Context ctx, String fileName, boolean isAsset) {
             try {
                 String json;
                 if (isAsset) {
                     InputStream is = ctx.getAssets().open(fileName);
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                     StringBuilder sb = new StringBuilder();
                     String line;
                     while ((line = br.readLine()) != null) sb.append(line);
                     br.close();
                     json = sb.toString();
                 } else {
                     File f = new File(ctx.getFilesDir(), fileName);
                     FileInputStream fis = new FileInputStream(f);
                     BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
                     StringBuilder sb = new StringBuilder();
                     String line;
                     while ((line = br.readLine()) != null) sb.append(line);
                     br.close();
                     json = sb.toString();
                 }
                 JSONObject root = new JSONObject(json);
                 return root.has("questions") || root.has("characters") || root.has("pieces");
             } catch (Exception e) {
                 return false;
             }
         }

         private boolean isValidBankFile(File f) {
             return isValidBank(context, f.getName(), false);
         }

        private String uploadBank(String body, Map<String, String> headers) {
            try {
                // Simple JSON upload: {"filename":"test.json","content":"..."}
                JSONObject json = new JSONObject(body);
                String filename = json.getString("filename");
                String content = json.getString("content");
                if (!filename.endsWith(".json")) filename += ".json";
                File outFile = new File(context.getFilesDir(), filename);
                FileOutputStream fos = new FileOutputStream(outFile);
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.close();
                return "{\"success\":true,\"filename\":\"" + filename + "\"}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        private String getIndexHtml() {
            return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>儿童锁屏配置</title>" +
                    "<style>body{font-family:sans-serif;margin:20px;background:#f5f5f5}h1{color:#333}.card{background:white;padding:20px;margin:10px 0;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1)}label{display:block;margin:10px 0 5px}input,select{padding:8px;width:300px;max-width:100%}button{padding:10px 20px;background:#007bff;color:white;border:none;border-radius:4px;cursor:pointer;margin:5px}button:hover{background:#0056b3}.success{color:green}</style>" +
                    "</head><body><h1>儿童锁屏配置</h1>" +
                    "<div class=\"card\"><h2>锁屏设置</h2>" +
                    "<label>观看时长（分钟）</label><input type=\"number\" id=\"watchLimitMinutes\">" +
                    "<label>答对几次解锁（题数）</label><input type=\"number\" id=\"unlockCount\">" +
                    "<label>锁定时长（分钟，0表示无限）</label><input type=\"number\" id=\"lockDurationMinutes\">" +
                    "<label>锁屏间隔（分钟，0表示不自动）</label><input type=\"number\" id=\"lockIntervalMinutes\">" +
                    "<label>重新锁屏延迟（秒）</label><input type=\"number\" id=\"relockDelaySeconds\">" +
                    "<label><input type=\"checkbox\" id=\"autoStart\" style=\"width:auto\"> 开机自启</label>" +
                    "<button onclick=\"saveConfig()\">保存设置</button><span id=\"configMsg\" class=\"success\"></span>" +
                    "</div>" +
                    "<div class=\"card\"><h2>题库设置</h2>" +
                    "<label>答题模式</label><select id=\"answerMode\"><option value=\"cumulative\">累计答题解锁</option><option value=\"continuous\">连续答题解锁</option></select><br>" +
                    "<label>默认生效题库</label><select id=\"bankSelect\"></select><button onclick=\"setDefaultBank()\">设置默认题库</button><br>" +
                    "<label>上传题库JSON文件</label><input type=\"text\" id=\"bankFilename\" placeholder=\"文件名.json\"><textarea id=\"bankContent\" style=\"width:100%;height:200px\" placeholder=\"粘贴JSON内容\"></textarea><br><button onclick=\"uploadBank()\">上传题库</button><span id=\"bankMsg\" class=\"success\"></span>" +
                    "</div>" +
                    "<script>" +
                    "fetch('/api/config').then(r=>r.json()).then(d=>{document.getElementById('watchLimitMinutes').value=d.watchLimitMinutes||30;document.getElementById('unlockCount').value=d.unlockCount||3;document.getElementById('lockDurationMinutes').value=d.lockDurationMinutes||0;document.getElementById('lockIntervalMinutes').value=d.lockIntervalMinutes||30;document.getElementById('relockDelaySeconds').value=d.relockDelaySeconds||2;document.getElementById('autoStart').checked=d.autoStart!==false;document.getElementById('answerMode').value=d.answerMode||'cumulative';window.currentBank=d.currentBankFile;});" +
                    "fetch('/api/banks').then(r=>r.json()).then(list=>{let sel=document.getElementById('bankSelect');list.forEach(b=>{let opt=document.createElement('option');opt.value=b.name;opt.text=b.name+(b.source==='assets'?' [内置]':' [上传]');if(window.currentBank===b.name) opt.selected=true;sel.add(opt);});});" +
                    "function saveConfig(){fetch('/api/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({watchLimitMinutes:parseInt(document.getElementById('watchLimitMinutes').value),unlockCount:parseInt(document.getElementById('unlockCount').value),lockDurationMinutes:parseInt(document.getElementById('lockDurationMinutes').value),lockIntervalMinutes:parseInt(document.getElementById('lockIntervalMinutes').value),relockDelaySeconds:parseInt(document.getElementById('relockDelaySeconds').value),autoStart:document.getElementById('autoStart').checked,answerMode:document.getElementById('answerMode').value})}).then(r=>r.json()).then(d=>{document.getElementById('configMsg').textContent='保存成功，立即生效';setTimeout(()=>document.getElementById('configMsg').textContent='',2000);});}" +
                    "function setDefaultBank(){let f=document.getElementById('bankSelect').value;fetch('/api/bank/setdefault',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({bankFile:f})}).then(r=>r.json()).then(d=>{document.getElementById('bankMsg').textContent='设置成功';setTimeout(()=>document.getElementById('bankMsg').textContent='',2000);});}" +
                    "function uploadBank(){let fn=document.getElementById('bankFilename').value,ct=document.getElementById('bankContent').value;fetch('/api/bank/upload',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({filename:fn,content:ct})}).then(r=>r.json()).then(d=>{document.getElementById('bankMsg').textContent=d.success?'上传成功':'上传失败';if(d.success){location.reload();}});}" +
                    "</script></body></html>";
        }
    }
}
