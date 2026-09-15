const express = require('express');
const { Pool } = require('pg');
const path = require('path');

require('dotenv').config({ path: path.join(__dirname, '../.env') });

const app = express();
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

app.use(express.static(path.join(__dirname, 'public')));

let cachedApps = [];
let lastAppFetchTime = 0;
const CACHE_TTL_MS = 60000;

app.get('/api/apps', async (req, res) => {
    try {
        const now = Date.now();
        if (cachedApps.length > 0 && (now - lastAppFetchTime < CACHE_TTL_MS)) {
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

// NÂNG CẤP API: Hỗ trợ Multiple Select cho App và Level
app.get('/api/logs', async (req, res) => {
    try {
        const { level, search, appId, timeRange, limit } = req.query;
        let query = 'SELECT * FROM system_logs WHERE 1=1';
        let values = [];
        let index = 1;

        if (level && level !== 'all') {
            const levels = level.split(',').map(Number);
            query += ` AND log_level = ANY($${index++}::int[])`;
            values.push(levels);
        }

        if (appId && appId !== 'all') {
            const apps = appId.split(',');
            query += ` AND application_id = ANY($${index++}::varchar[])`;
            values.push(apps);
        }

        if (search && search.trim() !== '') {
            query += ` AND payload ILIKE $${index++}`;
            values.push(`%${search.trim()}%`);
        }

        if (timeRange && timeRange !== 'all') {
            const ms = parseInt(timeRange) * 60 * 1000;
            const startTime = Date.now() - ms;
            query += ` AND timestamp >= $${index++}`;
            values.push(startTime);
        }

        const queryLimit = Math.min(parseInt(limit) || 200, 500);
        query += ` ORDER BY timestamp DESC LIMIT $${index}`;
        values.push(queryLimit);

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
    try {
        await pool.query('TRUNCATE TABLE system_logs RESTART IDENTITY;');
        cachedApps = [];
        lastAppFetchTime = 0;
        res.status(200).json({ message: 'Cleared' });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.listen(port, () => console.log(`[Dashboard] Running on http://localhost:${port}`));