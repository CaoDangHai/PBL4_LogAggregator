const express = require('express');
const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const { Server } = require('socket.io');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });
const port = 3000;

const pool = new Pool({
    host: process.env.DB_HOST || 'localhost',
    port: process.env.DB_PORT || 5432,
    database: process.env.DB_NAME || 'pbl4_db',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'secretpassword',
    max: 20,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 2000,
});

async function setupRealtimeDB() {
    const client = await pool.connect();
    try {
        await client.query(`
            CREATE OR REPLACE FUNCTION notify_new_log() RETURNS trigger AS $$
            BEGIN
                PERFORM pg_notify('new_log_channel', row_to_json(NEW)::text);
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            DROP TRIGGER IF EXISTS log_insert_trigger ON system_logs;

            CREATE TRIGGER log_insert_trigger
            AFTER INSERT ON system_logs
            FOR EACH ROW EXECUTE PROCEDURE notify_new_log();
        `);

        await client.query('LISTEN new_log_channel');
        client.on('notification', (msg) => {
            if (msg.channel === 'new_log_channel') {
                const newLog = JSON.parse(msg.payload);
                io.emit('new_log', newLog);
            }
        });
        console.log('[Realtime] Đã kích hoạt LISTEN/NOTIFY với PostgreSQL (Connection Keep-Alive)');
        // FIX: Không gọi client.release() ở đây để giữ connection luôn lắng nghe
    } catch (err) {
        console.error('Lỗi setup Realtime:', err);
        client.release(); // Chỉ nhả ra khi bị lỗi
    }
}
setupRealtimeDB();

app.use(express.static(path.join(__dirname, 'public')));

let cachedApps = [];
let lastAppFetchTime = 0;

app.get('/api/apps', async (req, res) => {
    try {
        const now = Date.now();
        if (cachedApps.length > 0 && (now - lastAppFetchTime < 60000)) {
            return res.json(cachedApps);
        }
        const { rows } = await pool.query('SELECT DISTINCT application_id FROM system_logs ORDER BY application_id');
        cachedApps = rows.map(r => r.application_id);
        lastAppFetchTime = now;
        res.json(cachedApps);
    } catch (err) {
        res.status(500).json(cachedApps);
    }
});

app.get('/api/logs', async (req, res) => {
    try {
        const { level, search, appId, timeRange, limit } = req.query;
        let query = 'SELECT * FROM system_logs WHERE 1=1';
        let values = [];
        let index = 1;

        if (level && level !== 'all') {
            query += ` AND log_level = ANY($${index++}::int[])`;
            values.push(level.split(',').map(Number));
        }
        if (appId && appId !== 'all') {
            query += ` AND application_id = ANY($${index++}::varchar[])`;
            values.push(appId.split(','));
        }
        if (search && search.trim() !== '') {
            query += ` AND payload ILIKE $${index++}`;
            values.push(`%${search.trim()}%`);
        }
        if (timeRange && timeRange !== 'all') {
            query += ` AND timestamp >= $${index++}`;
            values.push(Date.now() - (parseInt(timeRange) * 60 * 1000));
        }

        query += ` ORDER BY timestamp DESC LIMIT $${index}`;
        values.push(Math.min(parseInt(limit) || 200, 5000));

        const client = await pool.connect();
        try {
            await client.query('SET statement_timeout = 5000');
            const { rows } = await client.query(query, values);
            res.json(rows);
        } finally {
            client.release();
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/logs', async (req, res) => {
    if (req.headers.authorization !== 'Bearer admin123') {
        return res.status(401).json({ error: 'Unauthorized. Sai Token!' });
    }
    try {
        await pool.query('TRUNCATE TABLE system_logs RESTART IDENTITY;');
        cachedApps = [];
        lastAppFetchTime = 0;
        res.status(200).json({ message: 'Cleared' });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

server.listen(port, () => console.log(`[Dashboard] Running on http://localhost:${port}`));