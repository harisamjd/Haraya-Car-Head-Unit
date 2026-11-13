import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';
import { Env } from './types.js';
import { checkoutRouter } from './routes/checkout.js';
import { webhookRouter } from './routes/webhook.js';
import { telegramRouter } from './routes/telegram.js';
import { mockRouter } from './routes/mock.js';
import { handleWeeklyBackup } from './cron/backup.js';

const app = new Hono<{ Bindings: Env }>();

// Middleware
app.use('*', logger());
app.use('*', cors());

// Health Check
app.get('/health', (c) => {
  return c.json({
    status: 'ok',
    environment: c.env?.ENVIRONMENT || 'development',
    timestamp: new Date().toISOString()
  });
});

// Mount Routes
app.route('/api/checkout', checkoutRouter);
app.route('/api/webhook', webhookRouter);
app.route('/api/telegram', telegramRouter);
app.route('/api/mock', mockRouter);

// Global Error Handler
app.onError((err, c) => {
  console.error('[Worker Error]', err);
  return c.json(
    {
      error: 'INTERNAL_SERVER_ERROR',
      message: err.message
    },
    500
  );
});

export { app };

export default {
  fetch: app.fetch,
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil(handleWeeklyBackup(env));
  }
};

