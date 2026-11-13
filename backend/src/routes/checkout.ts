import { Hono } from 'hono';
import { Env, CheckoutInitRequest, CheckoutInitResponse, CheckoutStatusResponse } from '../types.js';
import { getSupabaseClient, findLicenseByDeviceId, findOrderById, createOrder } from '../db/supabase.js';
import { getPaymentAdapter } from '../services/payment.js';

export const checkoutRouter = new Hono<{ Bindings: Env }>();

// POST /api/checkout/init
checkoutRouter.post('/init', async (c) => {
  const body = await c.req.json<CheckoutInitRequest>().catch(() => null);
  if (!body || !body.device_id || typeof body.device_id !== 'string') {
    return c.json({ error: 'INVALID_REQUEST', message: 'Missing or invalid device_id' }, 400);
  }

  const deviceId = body.device_id.trim();
  const supabase = getSupabaseClient(c.env);

  // 1. Idempotency Check: Already active license?
  const existingLicense = await findLicenseByDeviceId(supabase, deviceId);
  if (existingLicense && existingLicense.status === 'ACTIVE') {
    const response: CheckoutInitResponse = {
      status: 'ALREADY_ACTIVE',
      device_id: deviceId,
      license_key: existingLicense.license_key,
      message: 'Device already has an active Pro license.'
    };
    return c.json(response, 200);
  }

  // 2. Generate new Order
  const orderId = `ORD-${Date.now()}-${Math.random().toString(36).substring(2, 7).toUpperCase()}`;
  const amount = 150000; // IDR 150,000 (~$10)
  const currency = 'IDR';
  const baseUrl = c.env.BASE_URL || new URL(c.req.url).origin;

  const paymentAdapter = getPaymentAdapter(c.env);
  const checkoutUrl = await paymentAdapter.createPaymentUrl({
    orderId,
    deviceId,
    amount,
    currency,
    email: body.email,
    phone: body.phone,
    baseUrl
  });

  await createOrder(supabase, {
    order_id: orderId,
    device_id: deviceId,
    email: body.email || null,
    phone: body.phone || null,
    amount,
    currency,
    status: 'PENDING',
    payment_url: checkoutUrl
  });

  const response: CheckoutInitResponse = {
    status: 'success',
    order_id: orderId,
    checkout_url: checkoutUrl,
    amount,
    currency,
    device_id: deviceId
  };

  return c.json(response, 200);
});

// GET /api/checkout/status?order_id=...
checkoutRouter.get('/status', async (c) => {
  const orderId = c.req.query('order_id');
  if (!orderId) {
    return c.json({ error: 'INVALID_REQUEST', message: 'Missing order_id query parameter' }, 400);
  }

  const supabase = getSupabaseClient(c.env);
  const order = await findOrderById(supabase, orderId);

  if (!order) {
    return c.json({ error: 'ORDER_NOT_FOUND', message: `Order ${orderId} not found` }, 404);
  }

  if (order.status === 'PAID' && order.license_key) {
    const response: CheckoutStatusResponse = {
      order_id: order.order_id,
      status: 'PAID',
      device_id: order.device_id,
      license_key: order.license_key,
      paid_at: order.paid_at || order.created_at
    };
    return c.json(response, 200);
  }

  const response: CheckoutStatusResponse = {
    order_id: order.order_id,
    status: (order.status === 'PAID' ? 'PENDING' : order.status) as 'PENDING' | 'EXPIRED' | 'FAILED',
    device_id: order.device_id
  };
  return c.json(response, 200);
});
