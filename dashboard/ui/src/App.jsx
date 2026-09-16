import React, { useState, useEffect, useMemo } from 'react';
import { PieChart, Pie, Cell, Tooltip as RechartsTooltip, ResponsiveContainer } from 'recharts';
import { Search, Download, Pause, Play, Trash2, Activity, ShieldAlert, X } from 'lucide-react';
import { io } from 'socket.io-client';

const COLORS = ['#58a6ff', '#d29922', '#f85149', '#8957e5', '#238636'];

const Login = ({ setToken }) => {
    const [pwd, setPwd] = useState('');
    const handleLogin = (e) => {
        e.preventDefault();
        if (pwd === 'admin123') {
            localStorage.setItem('auth_token', pwd);
            setToken(pwd);
        } else {
            alert('Sai mật khẩu!');
        }
    };
    return (
        <div className="flex h-screen items-center justify-center bg-bgBase">
            <form onSubmit={handleLogin} className="bg-bgSurface p-8 rounded-lg border border-borderC w-96 text-center shadow-xl">
                <ShieldAlert className="mx-auto text-accent mb-4" size={48} />
                <h2 className="text-xl font-bold mb-6 text-textMain">System Login</h2>
                <input type="password" value={pwd} onChange={e => setPwd(e.target.value)}
                    className="w-full bg-bgBase border border-borderC p-2 rounded text-textMain mb-4 focus:border-accent outline-none"
                    placeholder="Enter Token..." />
                <button type="submit" className="w-full bg-accent text-white p-2 rounded hover:bg-blue-600 font-semibold">Access Dashboard</button>
            </form>
        </div>
    );
};

export default function App() {
    const [token, setToken] = useState(localStorage.getItem('auth_token') || '');
    const [logs, setLogs] = useState([]);
    const [apps, setApps] = useState([]);
    const [isLive, setIsLive] = useState(true);

    const [filters, setFilters] = useState({ search: '', timeRange: 'all', appId: 'all' });
    const [selectedLevels, setSelectedLevels] = useState([]);

    const [showExport, setShowExport] = useState(false);
    const [exportConfig, setExportConfig] = useState({ timeRange: 'all', appId: 'all' });

    if (token !== 'admin123') return <Login setToken={setToken} />;

    const fetchApps = async () => {
        try {
            const res = await fetch('/api/apps');
            setApps(await res.json());
        } catch (e) { console.error(e); }
    };

    const fetchLogs = async () => {
        try {
            const q = new URLSearchParams({
                ...filters,
                level: selectedLevels.length > 0 ? selectedLevels.join(',') : 'all'
            });
            const res = await fetch(`/api/logs?${q.toString()}`);
            setLogs(await res.json());
        } catch (e) { console.error(e); }
    };

    useEffect(() => {
        fetchApps();
        fetchLogs();
    }, [filters, selectedLevels]);

    // FIX: Sắp xếp theo Event Time giảm dần
    useEffect(() => {
        const socket = io();

        socket.on('new_log', (newLog) => {
            if (!isLive) return;

            setLogs(prevLogs => {
                if (selectedLevels.length > 0 && !selectedLevels.includes(newLog.log_level.toString())) return prevLogs;
                if (filters.appId !== 'all' && newLog.application_id !== filters.appId) return prevLogs;

                const updatedLogs = [newLog, ...prevLogs];
                // Sắp xếp lại log theo thời gian thực sinh ra
                updatedLogs.sort((a, b) => Number(b.timestamp) - Number(a.timestamp));
                return updatedLogs.slice(0, 500);
            });
        });

        return () => socket.disconnect();
    }, [isLive, filters.appId, selectedLevels]);

    const pieData = useMemo(() => {
        const errorLogs = logs.filter(l => l.log_level === 3);
        const counts = {};
        errorLogs.forEach(l => { counts[l.application_id] = (counts[l.application_id] || 0) + 1; });
        return Object.entries(counts).map(([name, value]) => ({ name, value }));
    }, [logs]);

    const executeExport = async () => {
        try {
            const q = new URLSearchParams({ ...exportConfig, level: 'all', limit: 5000 });
            const res = await fetch(`/api/logs?${q.toString()}`);
            const exportData = await res.json();

            const headers = ['Date', 'Level', 'Service', 'MessageId', 'Payload'];
            const rows = exportData.map(l => {
                const d = new Date(Number(l.timestamp)).toISOString();
                const lvl = l.log_level === 3 ? 'ERROR' : l.log_level === 2 ? 'WARN' : 'INFO';
                return `${d},${lvl},${l.application_id},${l.message_id},"${l.payload.replace(/"/g, '""')}"`;
            });
            const csv = [headers.join(','), ...rows].join('\n');
            const blob = new Blob([csv], { type: 'text/csv' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url; a.download = `logs-export-${Date.now()}.csv`;
            a.click();
            setShowExport(false);
        } catch (e) {
            alert('Lỗi xuất file!');
        }
    };

    const wipeData = async () => {
        if (!confirm('Wipe ALL logs permanently?')) return;
        try {
            await fetch('/api/logs', { method: 'DELETE', headers: { 'Authorization': `Bearer ${token}` } });
            fetchLogs();
        } catch (e) { alert('Lỗi xóa dữ liệu!'); }
    };

    const highlightText = (text) => {
        if (!filters.search.trim()) return text;
        const parts = text.split(new RegExp(`(${filters.search})`, 'gi'));
        return parts.map((part, i) =>
            part.toLowerCase() === filters.search.toLowerCase() ?
                <mark key={i} className="bg-yellow-500 text-black px-1 rounded">{part}</mark> : part
        );
    };

    const extractTraceId = (payload) => {
        const match = /\[([^,]+),\s*([a-f0-9]{8})\]/.exec(payload);
        return match ? match[2] : null;
    };

    const toggleLevel = (val) => {
        if (selectedLevels.includes(val)) {
            setSelectedLevels(selectedLevels.filter(l => l !== val));
        } else {
            setSelectedLevels([...selectedLevels, val]);
        }
    };

    return (
        <div className="flex flex-col h-full relative">
            <header className="flex items-center justify-between px-6 py-3 bg-bgSurface border-b border-borderC z-10 shrink-0">
                <div className="flex items-center gap-2 font-bold text-lg text-textMain">
                    <Activity className="text-accent" /> Nebula Logs
                </div>

                <div className="flex-1 max-w-2xl mx-8 relative">
                    <Search className="absolute left-3 top-2.5 text-textMuted" size={16} />
                    <input type="text" placeholder="Live filter payloads, trace_id, keywords..."
                        value={filters.search} onChange={e => setFilters({ ...filters, search: e.target.value })}
                        className="w-full bg-bgBase border border-borderC rounded px-9 py-2 text-sm text-textMain outline-none focus:border-accent font-mono" />
                </div>

                <div className="flex items-center gap-3">
                    <select
                        value={filters.timeRange}
                        onChange={e => setFilters({ ...filters, timeRange: e.target.value })}
                        className="bg-bgBase border border-borderC text-textMain rounded px-2 py-1.5 text-sm outline-none focus:border-accent"
                    >
                        <option value="5">Last 5m</option>
                        <option value="15">Last 15m</option>
                        <option value="60">Last 1h</option>
                        <option value="all">All Time</option>
                    </select>

                    <button onClick={() => setShowExport(true)} className="flex items-center gap-2 px-3 py-1.5 bg-bgHover border border-borderC rounded text-sm hover:bg-borderC transition-colors">
                        <Download size={16} /> Export
                    </button>
                    <button onClick={() => setIsLive(!isLive)} className="flex items-center gap-2 px-3 py-1.5 bg-bgHover border border-borderC rounded text-sm hover:bg-borderC transition-colors">
                        {isLive ? <Pause size={16} className="text-yellow-500" /> : <Play size={16} className="text-green-500" />}
                        {isLive ? 'Pause' : 'Resume'}
                    </button>
                    <button onClick={wipeData} className="flex items-center gap-2 px-3 py-1.5 border border-red-900/50 bg-red-900/20 text-red-400 rounded text-sm hover:bg-red-900/40 transition-colors">
                        <Trash2 size={16} /> Wipe
                    </button>
                </div>
            </header>

            <div className="flex flex-1 overflow-hidden">
                <aside className="w-64 bg-bgSurface border-r border-borderC overflow-y-auto shrink-0 flex flex-col">
                    <div className="p-4 border-b border-borderC">
                        <h3 className="text-xs font-bold text-textMuted uppercase mb-3 tracking-wider">Log Level</h3>
                        {['ERROR', 'WARN', 'INFO'].map((lvl, idx) => {
                            const val = (3 - idx).toString();
                            return (
                                <label key={lvl} className="flex items-center gap-2 text-sm mb-2 cursor-pointer hover:text-accent">
                                    <input type="checkbox" checked={selectedLevels.includes(val)}
                                        onChange={() => toggleLevel(val)} className="accent-accent w-4 h-4 rounded" />
                                    {lvl}
                                </label>
                            );
                        })}
                    </div>
                    <div className="p-4 flex-1">
                        <h3 className="text-xs font-bold text-textMuted uppercase mb-3 tracking-wider">Services</h3>
                        <select value={filters.appId} onChange={e => setFilters({ ...filters, appId: e.target.value })}
                            className="w-full bg-bgBase border border-borderC rounded p-2 text-sm text-textMain outline-none mb-4">
                            <option value="all">All Services</option>
                            {apps.map(app => <option key={app} value={app}>{app}</option>)}
                        </select>

                        <h3 className="text-xs font-bold text-textMuted uppercase mb-2 tracking-wider mt-6">Error Distribution</h3>
                        {pieData.length > 0 ? (
                            <div className="h-48 w-full -ml-4">
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie data={pieData} cx="50%" cy="50%" innerRadius={40} outerRadius={60} paddingAngle={5} dataKey="value">
                                            {pieData.map((entry, index) => <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />)}
                                        </Pie>
                                        <RechartsTooltip contentStyle={{ backgroundColor: '#191c21', border: '1px solid #2d333b' }} itemStyle={{ color: '#e6edf3' }} />
                                    </PieChart>
                                </ResponsiveContainer>
                            </div>
                        ) : <p className="text-xs text-textMuted text-center mt-4">No errors found.</p>}
                    </div>
                </aside>

                <main className="flex-1 overflow-y-auto bg-bgBase relative">
                    <table className="w-full text-left border-collapse table-fixed">
                        <thead className="bg-bgSurface sticky top-0 z-10 shadow">
                            <tr>
                                <th className="w-40 py-2 px-4 text-xs font-semibold text-textMuted border-b border-borderC">Timestamp</th>
                                <th className="w-20 py-2 px-4 text-xs font-semibold text-textMuted border-b border-borderC">Level</th>
                                <th className="w-56 py-2 px-4 text-xs font-semibold text-textMuted border-b border-borderC">Service</th>
                                <th className="py-2 px-4 text-xs font-semibold text-textMuted border-b border-borderC">Message</th>
                            </tr>
                        </thead>
                        <tbody>
                            {logs.map((log) => {
                                const date = new Date(Number(log.timestamp)).toISOString().replace('T', ' ').substring(0, 19);
                                const lvlConfig = log.log_level === 3 ? { c: 'text-red-400 bg-red-400/10', t: 'ERROR' } :
                                    log.log_level === 2 ? { c: 'text-yellow-400 bg-yellow-400/10', t: 'WARN' } :
                                        { c: 'text-blue-400 bg-blue-400/10', t: 'INFO' };
                                const traceId = extractTraceId(log.payload);
                                return (
                                    <tr key={log.message_id} className="border-b border-borderC hover:bg-bgHover group">
                                        <td className="py-2 px-4 text-xs text-textMuted font-mono whitespace-nowrap overflow-hidden text-ellipsis">{date}</td>
                                        <td className="py-2 px-4 text-xs"><span className={`px-2 py-0.5 rounded font-bold ${lvlConfig.c}`}>{lvlConfig.t}</span></td>
                                        <td className="py-2 px-4 text-xs text-gray-400 overflow-hidden text-ellipsis whitespace-nowrap">{log.application_id}</td>
                                        <td className="py-2 px-4 text-xs font-mono text-textMain break-words flex items-start justify-between gap-2">
                                            <span>{highlightText(log.payload)}</span>
                                            {traceId && (
                                                <button onClick={() => setFilters({ ...filters, search: traceId })}
                                                    className="opacity-0 group-hover:opacity-100 shrink-0 text-[10px] bg-accent/20 text-accent hover:bg-accent hover:text-white px-2 py-1 rounded transition-all">
                                                    Trace {traceId}
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                    {logs.length === 0 && <div className="text-center text-textMuted mt-20">No logs match the current filters.</div>}
                </main>
            </div>

            {showExport && (
                <div className="absolute inset-0 bg-black/60 flex items-center justify-center z-50">
                    <div className="bg-bgSurface p-6 rounded-lg border border-borderC w-96 shadow-2xl">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-lg font-bold">Export Logs</h2>
                            <button onClick={() => setShowExport(false)} className="text-textMuted hover:text-white"><X size={20} /></button>
                        </div>

                        <div className="mb-4">
                            <label className="block text-xs font-semibold text-textMuted mb-2">Time Range</label>
                            <select value={exportConfig.timeRange} onChange={e => setExportConfig({ ...exportConfig, timeRange: e.target.value })}
                                className="w-full bg-bgBase border border-borderC rounded p-2 text-sm text-textMain outline-none">
                                <option value="5">Last 5m</option>
                                <option value="15">Last 15m</option>
                                <option value="60">Last 1h</option>
                                <option value="all">All Time</option>
                            </select>
                        </div>

                        <div className="mb-6">
                            <label className="block text-xs font-semibold text-textMuted mb-2">Service</label>
                            <select value={exportConfig.appId} onChange={e => setExportConfig({ ...exportConfig, appId: e.target.value })}
                                className="w-full bg-bgBase border border-borderC rounded p-2 text-sm text-textMain outline-none">
                                <option value="all">All Services</option>
                                {apps.map(app => <option key={app} value={app}>{app}</option>)}
                            </select>
                        </div>

                        <div className="flex justify-end gap-2">
                            <button onClick={() => setShowExport(false)} className="px-4 py-2 rounded text-sm bg-bgBase border border-borderC hover:bg-bgHover">Cancel</button>
                            <button onClick={executeExport} className="px-4 py-2 rounded text-sm bg-accent text-white hover:bg-blue-600 font-semibold flex items-center gap-2">
                                <Download size={16} /> Download CSV
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}