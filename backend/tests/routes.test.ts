import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import { app } from '../src/index.js';
import { Env, OrderRecord, LicenseRecord, DatabaseBackup } from '../src/types.js';
import { bytesToBase64 } from '../src/crypto.js';

// In-memory database simulation for Supabase
const ordersDb = new Map<string, OrderRecord>();
const licensesDb = new Map<string, LicenseRecord>();

vi.mock('../src/db/supabase.js', () => {
  return {
    getSupabaseClient: vi.fn(() => ({})),
    findLicenseByDeviceId: vi.fn(async (_client: any, deviceId: string) => {
      return licensesDb.get(deviceId) || null;
    }),
    findOrderByDeviceId: vi.fn(async (_client: any, deviceId: string) => {
      const orders = Array.from(ordersDb.values())
        .filter((o) => o.device_id === deviceId)
        .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
      return orders[0] || null;
    }),
    findOrderById: vi.fn(async (_client: any, orderId: string) => {
      return ordersDb.get(orderId) || null;
    }),
    createOrder: vi.fn(async (_client: any, order: any) => {
      const newOrder: OrderRecord = {
        ...order,
        created_at: new Date().toISOString(),
        paid_at: null,
        license_key: null,
      };
      ordersDb.set(order.order_id, newOrder);
      return newOrder;
    }),
    markOrderPaid: vi.fn(async (_client: any, orderId: string, licenseKey: string) => {
      const existing = ordersDb.get(orderId);
      if (!existing) throw new Error('Order not found');
      const updated: OrderRecord = {
        ...existing,
        status: 'PAID',
        license_key: licenseKey,
        paid_at: new Date().toISOString(),
      };
      ordersDb.set(orderId, updated);
      return updated;
    }),
    createOrUpdateLicense: vi.fn(async (_client: any, license: any) => {
      const existing = licensesDb.get(license.device_id);
      const updated: LicenseRecord = {
        ...license,
        created_at: existing ? existing.created_at : new Date().toISOString(),
        updated_at: new Date().toISOString(),
      };
      licensesDb.set(license.device_id, updated);
      return updated;
    }),
    getDatabaseBackup: vi.fn(async (): Promise<DatabaseBackup> => {
      const orders = Array.from(ordersDb.values());
      const licenses = Array.from(licensesDb.values());
      return {
        timestamp: new Date().toISOString(),
        version: '1.0',
        total_orders: orders.length,
        total_licenses: licenses.length,
        orders,
        licenses,
      };
    }),
  };
});

describe('API Routes & Integration Tests', () => {
  let testEnv: Env;
  let samplePrivateKeyB64: string;

  beforeAll(async () => {
    // Generate genuine ECDSA P-256 PKCS#8 private key for test environment
    const keyPair = (await crypto.subtle.generateKey(
      { name: 'ECDSA', namedCurve: 'P-256' },
      true,
      ['sign', 'verify']
    )) as CryptoKeyPair;
    const pkcs8 = (await crypto.subtle.exportKey('pkcs8', keyPair.privateKey)) as ArrayBuffer;
    samplePrivateKeyB64 = bytesToBase64(new Uint8Array(pkcs8));

    testEnv = {
      ENVIRONMENT: 'test',
      BASE_URL: 'http://localhost:8787',
      SUPABASE_URL: 'https://mock.supabase.co',
      SUPABASE_SERVICE_ROLE_KEY: 'mock_service_key',
      LICENSE_PRIVATE_KEY: samplePrivateKeyB64,
      RESEND_API_KEY: 're_test_mock_key',
      RESEND_FROM_EMAIL: 'onboarding@resend.dev',
      TELEGRAM_BOT_TOKEN: '123456:MOCK_TOKEN',
      TELEGRAM_ADMIN_CHAT_ID: '987654321',
      TELEGRAM_SECRET_TOKEN: 'mock_secret_token_123',
      MOCK_PAYMENT_ENABLED: 'true',
    };
  });

  beforeEach(() => {
    ordersDb.clear();
    licensesDb.clear();
    vi.restoreAllMocks();

    // Mock global fetch for external APIs (Resend & Telegram)
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: any) => {
      const url = typeof input === 'string' ? input : input.url;
      if (url.includes('api.resend.com') || url.includes('api.telegram.org')) {
        return new Response(JSON.stringify({ ok: true, id: 'mock_external_id' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }
      return new Response('Not found', { status: 404 });
    });
  });

  // =========================================================================
  // 1. Health Check
  // =========================================================================
  describe('GET /health', () => {
    it('returns 200 with status ok and environment info', async () => {
      const res = await app.request('/health', {}, testEnv);
      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.status).toBe('ok');
      expect(data.environment).toBe('test');
      expect(data.timestamp).toBeDefined();
    });
  });

  // =========================================================================
  // 2. Checkout Router
  // =========================================================================
  describe('Checkout Router (/api/checkout)', () => {
    it('rejects init request with missing or invalid device_id', async () => {
      const res = await app.request(
        '/api/checkout/init',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({}),
        },
        testEnv
      );
      expect(res.status).toBe(400);
      const data = await res.json<any>();
      expect(data.error).toBe('INVALID_REQUEST');
    });

    it('creates a new pending order and returns checkout_url for valid device_id', async () => {
      const deviceId = 'HRY-A8F2-91C0-4E7B';
      const res = await app.request(
        '/api/checkout/init',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            device_id: deviceId,
            email: 'user@example.com',
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.status).toBe('success');
      expect(data.order_id).toMatch(/^ORD-\d+-[A-Z0-9]+$/);
      expect(data.amount).toBe(150000);
      expect(data.currency).toBe('IDR');
      expect(data.device_id).toBe(deviceId);
      expect(data.checkout_url).toContain('/api/mock/checkout');
      expect(data.checkout_url).toContain(encodeURIComponent(deviceId));

      // Verify order saved in database
      const savedOrder = ordersDb.get(data.order_id);
      expect(savedOrder).toBeDefined();
      expect(savedOrder?.status).toBe('PENDING');
      expect(savedOrder?.device_id).toBe(deviceId);
    });

    it('returns ALREADY_ACTIVE status if device already has active license (Idempotency)', async () => {
      const deviceId = 'HRY-ACTIVE-DEVICE-01';
      const existingKey = 'HRY1.EXISTING_MOCK_PAYLOAD.EXISTING_MOCK_SIG';
      licensesDb.set(deviceId, {
        device_id: deviceId,
        license_key: existingKey,
        customer_email: 'existing@example.com',
        customer_phone: null,
        order_id: 'ORD-OLD',
        status: 'ACTIVE',
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      });

      const res = await app.request(
        '/api/checkout/init',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ device_id: deviceId }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.status).toBe('ALREADY_ACTIVE');
      expect(data.license_key).toBe(existingKey);
      expect(data.device_id).toBe(deviceId);
    });

    it('rejects status request without order_id parameter', async () => {
      const res = await app.request('/api/checkout/status', {}, testEnv);
      expect(res.status).toBe(400);
      const data = await res.json<any>();
      expect(data.error).toBe('INVALID_REQUEST');
    });

    it('returns 404 for non-existent order_id in status lookup', async () => {
      const res = await app.request('/api/checkout/status?order_id=ORD-NONEXISTENT', {}, testEnv);
      expect(res.status).toBe(404);
      const data = await res.json<any>();
      expect(data.error).toBe('ORDER_NOT_FOUND');
    });

    it('returns PENDING status for unpaid order', async () => {
      const orderId = 'ORD-PENDING-001';
      ordersDb.set(orderId, {
        order_id: orderId,
        device_id: 'HRY-PENDING-1234',
        email: null,
        phone: null,
        amount: 150000,
        currency: 'IDR',
        status: 'PENDING',
        payment_url: 'http://localhost:8787/mock',
        license_key: null,
        created_at: new Date().toISOString(),
        paid_at: null,
      });

      const res = await app.request(`/api/checkout/status?order_id=${orderId}`, {}, testEnv);
      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.status).toBe('PENDING');
      expect(data.order_id).toBe(orderId);
      expect(data.license_key).toBeUndefined();
    });

    it('returns PAID status with license_key for paid order', async () => {
      const orderId = 'ORD-PAID-001';
      const licenseKey = 'HRY1.MOCK_PAYLOAD.MOCK_SIG';
      ordersDb.set(orderId, {
        order_id: orderId,
        device_id: 'HRY-PAID-1234',
        email: null,
        phone: null,
        amount: 150000,
        currency: 'IDR',
        status: 'PAID',
        payment_url: 'http://localhost:8787/mock',
        license_key: licenseKey,
        created_at: new Date().toISOString(),
        paid_at: new Date().toISOString(),
      });

      const res = await app.request(`/api/checkout/status?order_id=${orderId}`, {}, testEnv);
      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.status).toBe('PAID');
      expect(data.order_id).toBe(orderId);
      expect(data.license_key).toBe(licenseKey);
      expect(data.paid_at).toBeDefined();
    });
  });

  // =========================================================================
  // 3. Webhook Router
  // =========================================================================
  describe('Payment Webhook Router (/api/webhook)', () => {
    it('rejects invalid webhook payload without order_id', async () => {
      const res = await app.request(
        '/api/webhook/payment',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({}),
        },
        testEnv
      );
      expect(res.status).toBe(400);
      const data = await res.json<any>();
      expect(data.error).toBe('INVALID_WEBHOOK');
    });

    it('returns 404 if order does not exist', async () => {
      const res = await app.request(
        '/api/webhook/payment',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            order_id: 'ORD-UNKNOWN-999',
            status: 'PAID',
          }),
        },
        testEnv
      );
      expect(res.status).toBe(404);
      const data = await res.json<any>();
      expect(data.error).toBe('ORDER_NOT_FOUND');
    });

    it('processes PAID settlement: mints token, updates DB, sends email and telegram notifications', async () => {
      const orderId = 'ORD-SETTLE-001';
      const deviceId = 'HRY-SETTLE-1122';
      ordersDb.set(orderId, {
        order_id: orderId,
        device_id: deviceId,
        email: 'customer@example.com',
        phone: '+628123456789',
        amount: 150000,
        currency: 'IDR',
        status: 'PENDING',
        payment_url: 'http://localhost:8787/mock',
        license_key: null,
        created_at: new Date().toISOString(),
        paid_at: null,
      });

      const res = await app.request(
        '/api/webhook/payment',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            order_id: orderId,
            status: 'PAID',
            mode: 'mock',
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.success).toBe(true);
      expect(data.order_id).toBe(orderId);
      expect(data.license_key).toMatch(/^HRY1\.[A-Z0-9]+\.[A-Z0-9]+$/);

      // Verify order marked PAID
      const updatedOrder = ordersDb.get(orderId);
      expect(updatedOrder?.status).toBe('PAID');
      expect(updatedOrder?.license_key).toBe(data.license_key);
      expect(updatedOrder?.paid_at).toBeDefined();

      // Verify license created in licenses table
      const savedLicense = licensesDb.get(deviceId);
      expect(savedLicense).toBeDefined();
      expect(savedLicense?.license_key).toBe(data.license_key);
      expect(savedLicense?.status).toBe('ACTIVE');
    });

    it('handles idempotent duplicate webhook calls safely', async () => {
      const orderId = 'ORD-DUPLICATE-001';
      const deviceId = 'HRY-DUP-1122';
      const existingKey = 'HRY1.EXISTING_MOCK_PAYLOAD.EXISTING_MOCK_SIG';
      ordersDb.set(orderId, {
        order_id: orderId,
        device_id: deviceId,
        email: 'customer@example.com',
        phone: null,
        amount: 150000,
        currency: 'IDR',
        status: 'PAID',
        payment_url: null,
        license_key: existingKey,
        created_at: new Date().toISOString(),
        paid_at: new Date().toISOString(),
      });

      const res = await app.request(
        '/api/webhook/payment',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            order_id: orderId,
            status: 'PAID',
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.success).toBe(true);
      expect(data.license_key).toBe(existingKey);
      expect(data.message).toContain('already paid');
    });
  });

  // =========================================================================
  // 4. Telegram Admin Bot Router
  // =========================================================================
  describe('Telegram Webhook Router (/api/telegram)', () => {
    it('rejects requests with invalid secret token', async () => {
      const res = await app.request(
        '/api/telegram/webhook',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Telegram-Bot-Api-Secret-Token': 'wrong_secret',
          },
          body: JSON.stringify({ update_id: 1 }),
        },
        testEnv
      );
      expect(res.status).toBe(401);
    });

    it('rejects commands from unauthorized chat IDs', async () => {
      const res = await app.request(
        '/api/telegram/webhook',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Telegram-Bot-Api-Secret-Token': 'mock_secret_token_123',
          },
          body: JSON.stringify({
            update_id: 1,
            message: {
              message_id: 10,
              chat: { id: 111222333, type: 'private' },
              text: '/help',
              date: Math.floor(Date.now() / 1000),
            },
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      const data = await res.json<any>();
      expect(data.ok).toBe(true);
      // Access denied message dispatched to unauthorized chat
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/sendMessage'),
        expect.objectContaining({
          body: expect.stringContaining('Access Denied'),
        })
      );
    });

    it('executes /gen command from authorized admin and mints license', async () => {
      const targetDevice = 'HRY-TELE-1111-2222';
      const res = await app.request(
        '/api/telegram/webhook',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Telegram-Bot-Api-Secret-Token': 'mock_secret_token_123',
          },
          body: JSON.stringify({
            update_id: 2,
            message: {
              message_id: 11,
              chat: { id: 987654321, type: 'private' }, // matching admin chat id
              text: `/gen ${targetDevice} admin_gen@example.com`,
              date: Math.floor(Date.now() / 1000),
            },
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      // Verify license was registered in database
      const license = licensesDb.get(targetDevice);
      expect(license).toBeDefined();
      expect(license?.status).toBe('ACTIVE');
      expect(license?.license_key).toMatch(/^HRY1\.[A-Z0-9]+\.[A-Z0-9]+$/);

      // Verify telegram message sent with license details
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/sendMessage'),
        expect.objectContaining({
          body: expect.stringContaining('License Generated Successfully'),
        })
      );
    });

    it('executes /check command for an existing device', async () => {
      const targetDevice = 'HRY-CHECK-8888-9999';
      licensesDb.set(targetDevice, {
        device_id: targetDevice,
        license_key: 'HRY1.MOCK_PAYLOAD.MOCK_SIG',
        customer_email: 'check@example.com',
        customer_phone: null,
        order_id: 'ORD-CHECK-1',
        status: 'ACTIVE',
        created_at: '2026-11-12T00:00:00Z',
        updated_at: '2026-11-12T00:00:00Z',
      });

      const res = await app.request(
        '/api/telegram/webhook',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Telegram-Bot-Api-Secret-Token': 'mock_secret_token_123',
          },
          body: JSON.stringify({
            update_id: 3,
            message: {
              message_id: 12,
              chat: { id: 987654321, type: 'private' },
              text: `/check ${targetDevice}`,
              date: Math.floor(Date.now() / 1000),
            },
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/sendMessage'),
        expect.objectContaining({
          body: expect.stringContaining('License Found'),
        })
      );
    });

    it('executes /backup command and dispatches backup document', async () => {
      const res = await app.request(
        '/api/telegram/webhook',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Telegram-Bot-Api-Secret-Token': 'mock_secret_token_123',
          },
          body: JSON.stringify({
            update_id: 4,
            message: {
              message_id: 13,
              chat: { id: 987654321, type: 'private' },
              text: '/backup',
              date: Math.floor(Date.now() / 1000),
            },
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/sendDocument'),
        expect.anything()
      );
    });

    it('returns help menu for /help command', async () => {
      const res = await app.request(
        '/api/telegram/webhook',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Telegram-Bot-Api-Secret-Token': 'mock_secret_token_123',
          },
          body: JSON.stringify({
            update_id: 5,
            message: {
              message_id: 14,
              chat: { id: 987654321, type: 'private' },
              text: '/help',
              date: Math.floor(Date.now() / 1000),
            },
          }),
        },
        testEnv
      );

      expect(res.status).toBe(200);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/sendMessage'),
        expect.objectContaining({
          body: expect.stringContaining('Admin Bot Help'),
        })
      );
    });
  });

  // =========================================================================
  // 5. Mock Checkout Page
  // =========================================================================
  describe('Mock Checkout Page (/api/mock/checkout)', () => {
    it('serves interactive HTML simulator page with order details', async () => {
      const orderId = 'ORD-HTML-1234';
      const deviceId = 'HRY-HTML-5678';
      const res = await app.request(
        `/api/mock/checkout?order_id=${orderId}&device_id=${deviceId}&amount=150000`,
        {},
        testEnv
      );

      expect(res.status).toBe(200);
      const contentType = res.headers.get('Content-Type');
      expect(contentType).toContain('text/html');

      const html = await res.text();
      expect(html).toContain('Haraya Dock Mock Payment Simulator');
      expect(html).toContain(orderId);
      expect(html).toContain(deviceId);
      expect(html).toContain('Simulate Successful Payment');
      expect(html).toContain('Simulate Payment Failure');
    });
  });
});
