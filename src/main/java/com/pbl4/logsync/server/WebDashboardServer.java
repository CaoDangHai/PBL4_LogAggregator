package com.pbl4.logsync.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WebDashboardServer {
    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 1. API Endpoint trả về JSON (Không reload trang)
        server.createContext("/api/data", exchange -> {
            String jsonResponse = generateJsonData();
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        // 2. Giao diện chính yếu
        server.createContext("/", exchange -> {
            String html = generateDashboardHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        server.setExecutor(null);
        server.start();
    }

    // --- HÀM TẠO JSON ---
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String generateJsonData() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // Nodes Data
        json.append("\"nodes\":[");
        List<Map.Entry<String, LogServer.NodeStatus>> entries = new ArrayList<>(LogServer.nodeStatuses.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, LogServer.NodeStatus> entry = entries.get(i);
            json.append(String.format("{\"id\":\"%s\",\"status\":\"%s\",\"lastSeq\":%d,\"lastSeen\":\"%s\"}", 
                escapeJson(entry.getKey()), entry.getValue().status, entry.getValue().lastSeq, entry.getValue().lastSeen));
            if (i < entries.size() - 1) json.append(",");
        }
        json.append("],");

        // History Data
        json.append("\"history\":[");
        List<LogServer.ConnectionEvent> hist = new ArrayList<>(LogServer.connectionHistory);
        for (int i = 0; i < hist.size(); i++) {
            LogServer.ConnectionEvent ev = hist.get(i);
            json.append(String.format("{\"id\":\"%s\",\"event\":\"%s\",\"time\":\"%s\"}", 
                escapeJson(ev.clientId), ev.eventType, ev.timestamp));
            if (i < hist.size() - 1) json.append(",");
        }
        json.append("],");

        // Logs Data
        json.append("\"logs\":[");
        List<LogServer.LogEntry> logs = new ArrayList<>(LogServer.receivedLogs);
        Collections.reverse(logs); // Trả về mới nhất lên đầu
        for (int i = 0; i < logs.size(); i++) {
            LogServer.LogEntry log = logs.get(i);
            json.append(String.format("{\"id\":\"%s\",\"seq\":%d,\"level\":%d,\"msg\":\"%s\",\"time\":\"%s\"}", 
                escapeJson(log.clientId), log.sequence, log.level, escapeJson(log.message), log.timestamp));
            if (i < logs.size() - 1) json.append(",");
        }
        json.append("]");
        json.append("}");
        return json.toString();
    }

    // --- HTML, CSS, JS ---
    private static String generateDashboardHtml() {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset='UTF-8'>
            <title>Enterprise Log Sync Monitoring</title>
            <style>
                body { font-family: 'Segoe UI', sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; margin: 0; }
                h1 { color: #38bdf8; margin-top: 0; }
                .grid-container { display: grid; grid-template-columns: 350px 1fr; gap: 20px; }
                .card { background: #1e293b; padding: 16px; border-radius: 8px; border: 1px solid #334155; margin-bottom: 20px; }
                table { width: 100%; border-collapse: collapse; font-size: 13px; }
                th, td { padding: 10px; text-align: left; border-bottom: 1px solid #334155; }
                th { color: #94a3b8; font-size: 11px; text-transform: uppercase; position: sticky; top: 0; background: #1e293b; }
                tr.clickable:hover { background: #334155; cursor: pointer; }
                .badge-online { background: #166534; color: #4ade80; padding: 2px 8px; border-radius: 4px; font-size: 11px; }
                .badge-offline { background: #991b1b; color: #f87171; padding: 2px 8px; border-radius: 4px; font-size: 11px; }
                
                /* Filter Styles */
                .filters { display: flex; gap: 15px; flex-wrap: wrap; margin-bottom: 15px; padding-bottom: 15px; border-bottom: 1px solid #334155; }
                .filter-group { display: flex; flex-direction: column; gap: 5px; }
                .filter-group label { font-size: 12px; color: #94a3b8; font-weight: bold; }
                input[type="text"], input[type="datetime-local"] { background: #0f172a; color: #fff; border: 1px solid #334155; padding: 6px; border-radius: 4px; }
                .checkbox-list { display: flex; gap: 10px; font-size: 13px; }
                .table-container { max-height: 600px; overflow-y: auto; }

                /* Modal Styles */
                .modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.7); z-index: 1000; }
                .modal-content { background: #1e293b; width: 500px; margin: 100px auto; padding: 20px; border-radius: 8px; border: 1px solid #334155; }
                .close-btn { float: right; color: #94a3b8; font-size: 20px; cursor: pointer; font-weight: bold; }
                .close-btn:hover { color: #fff; }
                
                .lvl-err { color: #ef4444; font-weight: bold; }
                .lvl-warn { color: #eab308; font-weight: bold; }
                .lvl-info { color: #22c55e; font-weight: bold; }
            </style>
        </head>
        <body>
            <h1>PBL4: Reliable Log Distributed System</h1>
            <div class="grid-container">
                
                <!-- Cột trái: Trạng thái Node -->
                <div>
                    <div class="card">
                        <h2>Node Status</h2>
                        <p style="font-size:12px; color:#94a3b8;">Click on a Node to view connection history</p>
                        <table>
                            <thead><tr><th>Node ID</th><th>Status</th><th>Checkpoint</th></tr></thead>
                            <tbody id="nodesTbody"></tbody>
                        </table>
                    </div>
                </div>

                <!-- Cột phải: Logs + Bộ lọc -->
                <div class="card">
                    <div class="filters">
                        <div class="filter-group">
                            <label>Time Range</label>
                            <div style="display:flex; gap:5px;">
                                <input type="datetime-local" id="filterStart">
                                <span style="line-height:28px;">-</span>
                                <input type="datetime-local" id="filterEnd">
                            </div>
                        </div>
                        <div class="filter-group">
                            <label>Log Level</label>
                            <div class="checkbox-list">
                                <label><input type="checkbox" class="lvl-cb" value="2" checked> INFO</label>
                                <label><input type="checkbox" class="lvl-cb" value="3" checked> WARN</label>
                                <label><input type="checkbox" class="lvl-cb" value="4" checked> ERROR</label>
                            </div>
                        </div>
                        <div class="filter-group">
                            <label>Stations / Nodes</label>
                            <div id="nodeCheckboxes" class="checkbox-list">
                                <!-- JS sẽ tự render trạm vào đây -->
                            </div>
                        </div>
                        <div class="filter-group" style="flex:1;">
                            <label>Search Message</label>
                            <input type="text" id="filterSearch" placeholder="Type to search..." style="width:100%;">
                        </div>
                    </div>
                    
                    <div class="table-container">
                        <table>
                            <thead><tr><th>Time</th><th>Node ID</th><th>Seq</th><th>Level</th><th>Message</th></tr></thead>
                            <tbody id="logsTbody"></tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- Modal hiển thị Lịch sử kết nối -->
            <div id="historyModal" class="modal">
                <div class="modal-content">
                    <span class="close-btn" onclick="closeModal()">&times;</span>
                    <h2 id="modalTitle">History</h2>
                    <div class="table-container" style="max-height: 400px;">
                        <table>
                            <thead><tr><th>Time</th><th>Event</th></tr></thead>
                            <tbody id="historyTbody"></tbody>
                        </table>
                    </div>
                </div>
            </div>

            <script>
                let globalData = { nodes: [], history: [], logs: [] };
                let availableNodes = new Set();
                let selectedNodes = new Set();

                // Các Element của Filters
                const filterStart = document.getElementById('filterStart');
                const filterEnd = document.getElementById('filterEnd');
                const filterSearch = document.getElementById('filterSearch');
                const lvlCheckboxes = document.querySelectorAll('.lvl-cb');

                // Lắng nghe thay đổi của bộ lọc để render lại ngay lập tức
                filterStart.addEventListener('change', renderLogs);
                filterEnd.addEventListener('change', renderLogs);
                filterSearch.addEventListener('input', renderLogs);
                lvlCheckboxes.forEach(cb => cb.addEventListener('change', renderLogs));

                function fetchApi() {
                    fetch('/api/data')
                        .then(res => res.json())
                        .then(data => {
                            globalData = data;
                            updateNodesList(data.nodes);
                            renderNodes();
                            renderLogs();
                            // Nếu modal đang mở, update data live
                            if(document.getElementById('historyModal').style.display === 'block') {
                                openHistory(document.getElementById('modalTitle').dataset.node);
                            }
                        });
                }

                function updateNodesList(nodes) {
                    const container = document.getElementById('nodeCheckboxes');
                    let changed = false;
                    nodes.forEach(n => {
                        if(!availableNodes.has(n.id)) {
                            availableNodes.add(n.id);
                            selectedNodes.add(n.id); // Default check all
                            changed = true;
                        }
                    });
                    if(changed) {
                        container.innerHTML = '';
                        availableNodes.forEach(id => {
                            const lbl = document.createElement('label');
                            lbl.innerHTML = `<input type="checkbox" value="${id}" onchange="toggleNode('${id}', this.checked)" checked> ${id}`;
                            container.appendChild(lbl);
                        });
                    }
                }

                function toggleNode(nodeId, isChecked) {
                    if(isChecked) selectedNodes.add(nodeId);
                    else selectedNodes.delete(nodeId);
                    renderLogs();
                }

                function renderNodes() {
                    const tbody = document.getElementById('nodesTbody');
                    tbody.innerHTML = '';
                    globalData.nodes.forEach(n => {
                        const tr = document.createElement('tr');
                        tr.className = 'clickable';
                        tr.onclick = () => openHistory(n.id);
                        const badge = n.status === 'ONLINE' ? '<span class="badge-online">ONLINE</span>' : '<span class="badge-offline">DISCONNECTED</span>';
                        tr.innerHTML = `<td><strong>${n.id}</strong></td><td>${badge}</td><td>#${n.lastSeq}</td>`;
                        tbody.appendChild(tr);
                    });
                }

                function getLevelBadge(level) {
                    if (level === 4) return "<span class='lvl-err'>[ERROR]</span>";
                    if (level === 3) return "<span class='lvl-warn'>[WARN]</span>";
                    return "<span class='lvl-info'>[INFO]</span>";
                }

                function renderLogs() {
                    const tbody = document.getElementById('logsTbody');
                    tbody.innerHTML = '';
                    
                    // Lấy giá trị filter
                    const startVal = filterStart.value ? new Date(filterStart.value).getTime() : 0;
                    const endVal = filterEnd.value ? new Date(filterEnd.value).getTime() : Infinity;
                    const searchTxt = filterSearch.value.toLowerCase();
                    const activeLevels = Array.from(lvlCheckboxes).filter(cb => cb.checked).map(cb => parseInt(cb.value));

                    let displayedCount = 0;

                    globalData.logs.forEach(log => {
                        // Core Logic Bộ lọc
                        const logTime = new Date(log.time.replace(' ', 'T')).getTime();
                        
                        if (logTime < startVal || logTime > endVal) return; // Lọc Thời gian
                        if (!activeLevels.includes(log.level)) return; // Lọc Level
                        if (!selectedNodes.has(log.id)) return; // Lọc Trạm
                        if (searchTxt && !log.msg.toLowerCase().includes(searchTxt) && !log.id.toLowerCase().includes(searchTxt)) return; // Lọc Keyword
                        
                        const tr = document.createElement('tr');
                        tr.innerHTML = `<td>${log.time.split(' ')[1]}</td><td><strong>${log.id}</strong></td><td>#${log.seq}</td><td>${getLevelBadge(log.level)}</td><td><code>${log.msg}</code></td>`;
                        tbody.appendChild(tr);
                        displayedCount++;
                    });

                    if(displayedCount === 0) {
                        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:#94a3b8;">No logs match current filters</td></tr>`;
                    }
                }

                function openHistory(nodeId) {
                    document.getElementById('modalTitle').innerText = "Connection History: " + nodeId;
                    document.getElementById('modalTitle').dataset.node = nodeId; // Save state
                    const tbody = document.getElementById('historyTbody');
                    tbody.innerHTML = '';
                    
                    const nodeHistory = globalData.history.filter(h => h.id === nodeId);
                    nodeHistory.forEach(h => {
                        const eventBadge = h.event === 'CONNECTED' ? '<span style="color:#4ade80;">CONNECTED</span>' : '<span style="color:#f87171;">DISCONNECTED</span>';
                        tbody.innerHTML += `<tr><td>${h.time}</td><td>${eventBadge}</td></tr>`;
                    });
                    
                    document.getElementById('historyModal').style.display = 'block';
                }

                function closeModal() {
                    document.getElementById('historyModal').style.display = 'none';
                    document.getElementById('modalTitle').dataset.node = "";
                }

                // Gọi API ngầm mỗi 1 giây
                fetchApi();
                setInterval(fetchApi, 1000);
            </script>
        </body>
        </html>
        """;
    }
}